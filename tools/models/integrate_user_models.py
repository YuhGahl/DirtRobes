#!/usr/bin/env python3
"""
Intègre dans le mod les modèles Blockbench faits par l'utilisateur (un dossier par Spectrobe).

  python3 tools/models/integrate_user_models.py <dossier Spectobes> [espèce ...]

Pour chaque espèce de PLAN (id → nom du dossier, fichier .bbmodel, cri), le script :
- exporte le modèle au format Bedrock (UV de boîte) dans bedrock/pokemon/models/<id>.geo.json
  (partagé par DirtRobes et Cobblemon), l'os racine renommé <id> ;
- extrait la texture intégrée au .bbmodel dans textures/spectrobes/<id>.png ;
- convertit les animations Blockbench du fichier (ground_idle, ground_walk, battle_idle, sleep, cry,
  recoil, faint, air_idle, air_fly, swim, idle_quirk…) en bedrock/pokemon/animations/<id>.animation.json ;
- écrit le poser (poses selon les animations présentes) et le resolver DirtRobes, avec une taille calculée
  pour que la créature ait la carrure voulue pour son stade (TARGET, en blocs, mesurée par
  √(hauteur × plus grande dimension horizontale)) ;
- copie le cri (mono) dans sounds/spectrobe/<id>_cry.ogg.
"""
import base64, json, math, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")
SPECIES = os.path.join(ROOT, "src/main/resources/data/dirtrobes/spectrobes/species")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bbmodel_to_geo import export  # noqa: E402

# carrure visée (blocs) une fois affiché, selon le stade (la taille du stade est déjà comptée)
TARGET = {"child": 1.0, "adult": 2.4, "evolved": 3.5, "secret": 3.5, "special": 3.5}
STAGE_SCALE = {"child": 0.8, "adult": 1.0, "evolved": 1.25, "secret": 1.25, "special": 1.25}

# id → (dossier, fichier .bbmodel, cri ou None, carrure imposée ou None)
PLAN = {
    "komainu": ("1 - komainu", "komainu.bbmodel", "Komainu_Cry.ogg", None),
    "komadoros": ("3 - komadoros", "komadoros.bbmodel", "Komadoros_Cry.ogg", 3.6),
    "vilamasta": ("5 - vilamasta", "Vilamasta.bbmodel", None, None),
    "vilanox": ("6 - vilanox", "vilanox.bbmodel", None, None),
    "vilakroma": ("7 - vilakroma", "Vilakroma.bbmodel", None, None),
    "segu": ("8 - segu", "segu.bbmodel", None, None),
    "segulos": ("9 - segulos", "segulos.bbmodel", None, None),
    "segulara": ("10 - segulara", "segulara_cobblemon.bbmodel", None, None),
    "harumi": ("11 - harumi", "harumi.bbmodel", None, None),
    "harumite": ("12 - harumite", "harumite.bbmodel", None, None),
    "harumitey": ("13 - harumitey", "bernard_final.bbmodel", None, None),
    "inkana": ("14 - inkana", "inkana.bbmodel", None, None),
    "inkanapa": ("15 - inkanapa", "inkanapa.bbmodel", None, None),
    "inkaflare": ("16 - inkaflare", "inkaflare.bbmodel", None, None),
    "aoi": ("17 - aoi", "aoi.bbmodel", "Aoi_Cry.ogg", None),
    "aoba": ("18 - aoba", "aoba.bbmodel", "Aoba_Cry.ogg", None),
    "aobasar": ("19 - aobasar", "aobasar.bbmodel", "Aobasar_Cry.ogg", None),
    "shakin": ("20 - shakin", "shakin.bbmodel", "Shakin_Cry.ogg", None),
    "shakor": ("21 - shakor", "shakor.bbmodel", "Shakor_Cry.ogg", None),
    "shakoblad": ("22 - shakoblad", "shakoblad.bbmodel", "Shakoblad_Cry.ogg", None),
    "zoza": ("23 - zoza", "bebe_lapin.bbmodel", "Zoza_Cry.ogg", None),
    "zozane": ("24 - zozane", None, "Zozane_Cry.ogg", None),
    "zozanero": ("25 - zozanero", "lapin_final.bbmodel", None, None),
    "zazanero": ("26 - zazanero", "zazanero.bbmodel", "Zazanero_Cry.ogg", None),
    "gejio": ("27 - gejio", "gejio.bbmodel", None, None),
}

LOOK = ("head_ai", "tete", "head", "tete_ai", "cou", "neck")


def num(v):
    if isinstance(v, (int, float)):
        return v
    s = str(v).strip()
    try:
        return float(s) if s else 0
    except ValueError:
        return s  # expression MoLang


def convert_animations(bb, sid, root_old):
    out = {}
    for an in bb.get("animations", []):
        short = an["name"].split(".")[-1]
        a = {}
        loop = an.get("loop", "once")
        if loop == "loop":
            a["loop"] = True
        elif loop == "hold":
            a["loop"] = "hold_on_last_frame"
        if an.get("length"):
            a["animation_length"] = an["length"]
        bones = {}
        for animator in an.get("animators", {}).values():
            if animator.get("type", "bone") != "bone":
                continue
            name = sid if animator["name"] == root_old else animator["name"]
            chans = {}
            for k in animator.get("keyframes", []):
                ch = k["channel"]
                if ch not in ("rotation", "position", "scale"):
                    continue
                dps = [[num(dp.get(ax, 0)) for ax in "xyz"] for dp in k["data_points"]]
                if k.get("interpolation") == "step" and len(dps) == 1:
                    val = {"pre": dps[0], "post": dps[0]}
                elif len(dps) > 1:
                    val = {"pre": dps[0], "post": dps[1]}
                else:
                    val = dps[0]
                if k.get("interpolation") == "catmullrom":
                    val = {"post": val if isinstance(val, list) else val["post"], "lerp_mode": "catmullrom"}
                chans.setdefault(ch, {})[f"{float(k['time']):g}" if float(k['time']) % 1 else f"{float(k['time']):.1f}"] = val
            for ch, keys in chans.items():
                if len(keys) == 1 and "0.0" in keys and not isinstance(keys["0.0"], dict):
                    chans[ch] = keys["0.0"]  # valeur fixe (souvent une expression)
                else:
                    chans[ch] = dict(sorted(keys.items(), key=lambda kv: float(kv[0])))
            if chans:
                bones[name] = chans
        a["bones"] = bones
        out[f"animation.{sid}.{short}"] = a
    return out


def auto_animations(sid, geo):
    """Animations de base pour un modèle livré sans animations (squelette façon Cobblemon : body, legs,
    leg_front_left…, head_ai, jaw, eye_*, tail…). Le corps respire en gardant les pieds au sol."""
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    has = lambda n: n in bones
    head = next((b for b in ("head_ai", "head", "neck") if has(b)), None)
    quad = [b for b in ("leg_front_left", "leg_front_right", "leg_back_left", "leg_back_right") if has(b)]
    biped = [b for b in ("leg_left", "leg_right") if has(b)]
    arms = [b for b in ("arm_left", "arm_right") if has(b)]
    tails = [b for b in ("tail", "tail2", "tail3") if has(b)]
    eyes = [b for b in ("eye_left", "eye_right") if has(b)]
    legs_root = "legs" if has("legs") else None
    body = "body" if has("body") else sid
    T = "q.anim_time*90"

    def bob(amp, speed, base=0.0):
        e = f"{base}+math.sin({T}*{speed})*{amp}" if base else f"math.sin({T}*{speed})*{amp}"
        d = {body: {"position": [0, e, 0]}}
        feet = [legs_root] if legs_root else quad + biped
        for f in feet:
            d[f] = {"position": [0, f"-({e})", 0]}
        return d

    def merge(*ds):
        out = {}
        for d in ds:
            for k, v in d.items():
                out.setdefault(k, {}).update(v)
        return out

    A = {}
    tail = {t: {"rotation": [0, f"math.sin({T}*0.8-{i*40})*{6+i*3}", 0]} for i, t in enumerate(tails)}
    A["ground_idle"] = {"loop": True, "bones": merge(bob(0.3, 1), tail,
        {head: {"rotation": [f"math.sin({T}-60)*2", 0, 0]}} if head else {},
        {a: {"rotation": [0, 0, f"{'-' if a.endswith('left') else ''}math.sin({T})*3"]} for a in arms})}
    walk = {}
    for i, l in enumerate(quad):
        ph = 0 if l in ("leg_front_left", "leg_back_right") else 180
        walk[l] = {"rotation": [f"math.sin({T}*3+{ph})*25", 0, 0]}
    for l in biped:
        walk[l] = {"rotation": [f"math.sin({T}*3+{0 if l.endswith('left') else 180})*30", 0, 0]}
    for a in arms:
        walk[a] = {"rotation": [f"math.sin({T}*3+{180 if a.endswith('left') else 0})*20", 0, 0]}
    A["ground_walk"] = {"loop": True, "bones": merge(bob(0.4, 6), tail, walk)}
    A["battle_idle"] = {"loop": True, "bones": merge(bob(0.3, 3, -0.8), tail,
        {head: {"rotation": ["-5+math.sin(" + T + "*3)*2", 0, 0]}} if head else {},
        {"jaw": {"rotation": ["-6+math.sin(" + T + "*3)*2", 0, 0]}} if has("jaw") else {},
        {a: {"rotation": [-25, 0, 0]} for a in arms})}
    if eyes:
        A["idle_quirk"] = {"animation_length": 0.25, "bones": {e: {"scale": {"0.0": [1, 1, 1], "0.1": [1, 0.1, 1], "0.25": [1, 1, 1]}} for e in eyes}}
    A["cry"] = {"animation_length": 1.2, "bones": merge(
        {head: {"rotation": {"0.0": [0, 0, 0], "0.25": [-18, 0, 0], "0.45": [-16, 5, 0], "0.65": [-18, -5, 0], "0.9": [-16, 0, 0], "1.2": [0, 0, 0]}}} if head else {},
        {"jaw": {"rotation": {"0.0": [0, 0, 0], "0.2": [-25, 0, 0], "0.9": [-25, 0, 0], "1.1": [0, 0, 0]}}} if has("jaw") else {},
        {a: {"rotation": {"0.0": [0, 0, 0], "0.25": [-60, 0, 0], "0.9": [-60, 0, 0], "1.2": [0, 0, 0]}} for a in arms},
        {t: {"rotation": {"0.0": [0, 0, 0], "0.3": [-20, 0, 0], "0.9": [-20, 0, 0], "1.2": [0, 0, 0]}} for t in tails[:1]})}
    A["recoil"] = {"animation_length": 0.6, "bones": merge(
        {body: {"position": {"0.0": [0, 0, 0], "0.08": [0, 0, 1.5], "0.6": [0, 0, 0]},
                "rotation": {"0.0": [0, 0, 0], "0.08": [5, 0, 4], "0.2": [2, 0, -3], "0.6": [0, 0, 0]}}},
        {legs_root: {"position": {"0.0": [0, 0, 0], "0.08": [0, 0, -1.5], "0.6": [0, 0, 0]}}} if legs_root else {},
        {head: {"rotation": {"0.0": [0, 0, 0], "0.08": [-12, 10, 0], "0.25": [-5, -6, 0], "0.6": [0, 0, 0]}}} if head else {},
        {e: {"scale": {"0.0": [1, 1, 1], "0.06": [1, 0.15, 1], "0.4": [1, 0.15, 1], "0.6": [1, 1, 1]}} for e in eyes})}
    low = -0.25 * max(c["origin"][1] + c["size"][1] for b in bones.values() for c in b.get("cubes", []))
    A["sleep"] = {"loop": True, "bones": merge(
        {body: {"position": [0, round(low, 2), 0]}},
        {l: {"rotation": [-80 if "front" in l else 80, 0, 0]} for l in quad},
        {l: {"rotation": [-80, 0, 0]} for l in biped},
        {head: {"rotation": [12, -12, 0]}} if head else {},
        {e: {"scale": [1, 0.1, 1]} for e in eyes},
        {t: {"rotation": [20, -30, 0]} for t in tails[:1]})}
    A["faint"] = {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": merge(
        {body: {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 0, 80]}, "position": {"0.0": [0, 0, 0], "1.0": [0, round(low * 0.8, 2), 0]}}},
        {e: {"scale": {"0.0": [1, 1, 1], "0.5": [1, 0.1, 1]}} for e in eyes})}
    return {f"animation.{sid}.{k}": v for k, v in A.items()}


def poser(sid, anims, bone_names):
    have = {k.split(".")[-1] for k in anims}
    look = next((b for b in LOOK if b in bone_names), None)
    lk = [f"q.look('{look}')"] if look else []
    q = [f"q.bedrock_quirk('{sid}', 'idle_quirk')"] if "idle_quirk" in have else []
    idle = "ground_idle" if "ground_idle" in have else None

    def pose(types, anim, battle=None, extra=()):
        p = {"poseTypes": types, "animations": lk + [f"q.bedrock('{sid}', '{anim}')"] + list(extra)}
        if battle is not None:
            p["isBattle"] = battle
        if q:
            p["quirks"] = q
        return p

    poses = {}
    if "battle_idle" in have:
        poses["battle-standing"] = pose(["STAND"], "battle_idle", True)
    if idle:
        poses["standing"] = pose(["STAND", "PORTRAIT", "PROFILE", "NONE", "SHOULDER_LEFT", "SHOULDER_RIGHT"]
                                 + ([] if "air_idle" in have else ["FLOAT", "HOVER"]), idle, False)
    if "ground_walk" in have:
        poses["walking"] = pose(["WALK"] + ([] if "swim" in have else ["SWIM"]), "ground_walk")
    if "swim" in have:
        poses["swimming"] = pose(["SWIM", "FLOAT"], "swim")
    if "air_idle" in have:
        poses["hovering"] = pose(["HOVER", "FLOAT"], "air_idle")
    if "air_fly" in have:
        poses["flying"] = pose(["FLY"], "air_fly")
    if "sleep" in have:
        poses["sleep"] = {"poseTypes": ["SLEEP"], "animations": [f"q.bedrock('{sid}', 'sleep')"]}
    animations = {}
    for k in ("cry", "recoil"):
        if k in have:
            animations[k] = f"q.bedrock_stateful('{sid}', '{k}')"
    if "faint" in have:
        animations["faint"] = f"q.bedrock_primary('{sid}', 'faint', q.curve('one'))"
    for k in ("physical", "special", "status"):
        if k in have:
            animations[k] = f"q.bedrock_primary('{sid}', '{k}', q.curve('symmetrical_wide'))"
    return {"portraitScale": 1.0, "portraitTranslation": [0, 0, 0], "profileScale": 0.6,
            "profileTranslation": [0, 0.6, 0], "rootBone": sid, "animations": animations, "poses": poses}


def size_metric(geo):
    lo, hi = [1e9] * 3, [-1e9] * 3
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            for i in range(3):
                lo[i] = min(lo[i], c["origin"][i]); hi[i] = max(hi[i], c["origin"][i] + c["size"][i])
    h, w, l = (hi[i] - lo[i] for i in (1, 0, 2))
    return math.sqrt(h * max(w, l)) / 16


def integrate(src_root, sid, folder, bbfile, cry, target):
    d = os.path.join(src_root, folder)
    spec = json.load(open(os.path.join(SPECIES, sid + ".json"), encoding="utf-8"))
    stage = spec.get("stage", "child")
    report = {"id": sid}
    if bbfile:
        bb = json.load(open(os.path.join(d, bbfile), encoding="utf-8"))
        groups = {g["uuid"]: g for g in bb.get("groups", [])}
        roots = [groups.get(n.get("uuid"), n)["name"] for n in bb["outliner"] if not isinstance(n, str)]
        root_old = roots[0]
        geo = export(os.path.join(d, bbfile), sid, {root_old: sid})
        json.dump(geo, open(os.path.join(A, "bedrock/pokemon/models", sid + ".geo.json"), "w"), indent=2)
        tex = bb["textures"][0]["source"].split(",", 1)[1]
        open(os.path.join(A, "textures/spectrobes", sid + ".png"), "wb").write(base64.b64decode(tex))
        anims = convert_animations(bb, sid, root_old)
        report["auto"] = not anims
        if not anims:
            anims = auto_animations(sid, geo)
        bone_names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
        if anims:
            json.dump({"format_version": "1.8.0", "animations": anims},
                      open(os.path.join(A, "bedrock/pokemon/animations", sid + ".animation.json"), "w"), indent=2)
            json.dump(poser(sid, anims, bone_names), open(os.path.join(A, "bedrock/pokemon/posers", sid + ".json"), "w"), indent=2)
            poser_id = f"dirtrobes:{sid}"
        else:
            poser_id = None  # animations à faire à la main (gardées si elles existent déjà)
            if os.path.exists(os.path.join(A, "bedrock/pokemon/posers", sid + ".json")):
                poser_id = f"dirtrobes:{sid}"
        metric = size_metric(geo)
        scale = round((target or TARGET[stage]) / (metric * STAGE_SCALE[stage]), 3)
        res = {"species": f"dirtrobes:{sid}", "model": f"dirtrobes:{sid}", "texture": f"dirtrobes:textures/spectrobes/{sid}.png",
               "tint": False, "scale": scale}
        if poser_id:
            res["poser"] = poser_id
        from PIL import Image
        import numpy as np
        alpha = np.array(Image.open(os.path.join(A, "textures/spectrobes", sid + ".png")).convert("RGBA"))[..., 3]
        if ((alpha > 0) & (alpha < 250)).any():
            res["translucent"] = True
        json.dump(res, open(os.path.join(A, "bedrock/spectrobes/resolvers", sid + ".json"), "w"), indent=2)
        report.update(cubes=sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"]),
                      anims=sorted(k.split(".")[-1] for k in anims), scale=scale)
    if cry:
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", os.path.join(d, cry), "-ac", "1", "-c:a", "libvorbis",
                        "-q:a", "5", os.path.join(A, "sounds/spectrobe", sid + "_cry.ogg")], check=True)
        report["cry"] = True
    return report


if __name__ == "__main__":
    src = sys.argv[1]
    only = sys.argv[2:] or list(PLAN)
    for sid in only:
        print(integrate(src, sid, *PLAN[sid]))
