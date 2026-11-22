#!/usr/bin/env python3
"""
Génère les espèces Cobblemon des Spectrobes (intégration optionnelle DirtRobes ↔ Cobblemon).

Entrée  : src/main/resources/data/dirtrobes/spectrobes/species/*.json (les espèces DirtRobes, stats brutes du doc)
Sortie  : src/main/resources/data/dirtrobes/species/<id>.json        (lu uniquement par Cobblemon)
          + clés de langue dirtrobes.species.<showdownid>.name dans fr_fr / en_us

Sans Cobblemon, ces fichiers ne sont lus par personne : le mod se comporte exactement comme avant.

Règles :
  - type 1 = propriété (Corona=Feu, Aurora=Plante, Flash=Eau, Eclipse=Sol, Halo=Vol, Prisma=Normal),
    type 2 = choisi par lignée (FAMILIES ci-dessous).
  - stats : les 6 stats de base de la liste des Spectrobes (tools/stats/pokemon_like_stats.py).
  - talents : 2 talents selon le type 1, talent caché selon le type 2.
  - attaques : attaques Pokémon existantes du type 1 et du type 2, apprises par niveau, + CT de ces types.
  - évolutions : niveau DirtRobes (enfant 10, adulte 30). Évolution rare (Vilakroma) : minéral d'Évolution
    utilisé sur le Spectrobe au niveau requis.
  - pas d'apparition sauvage (aucun fichier de spawn), pas d'œufs (groupe « undiscovered »), sans genre.
"""
import json
import glob
import os
import re
import colorsys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "src/main/resources/data/dirtrobes/spectrobes/species")
OUT = os.path.join(ROOT, "src/main/resources/data/dirtrobes/species")
LANG = os.path.join(ROOT, "src/main/resources/assets/dirtrobes/lang")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")
DR_RESOLVERS = os.path.join(ASSETS, "bedrock/spectrobes/resolvers")
CB_RESOLVERS = os.path.join(ASSETS, "bedrock/pokemon/resolvers")
TINT_DIR = "textures/spectrobes/cobblemon"   # textures provisoires teintées, générées
STAGE_SCALE = {"child": 0.8, "adult": 1.0, "evolved": 1.25, "secret": 1.35, "special": 1.35}
PROPERTY_COLOR = {"corona": 0xF08A3C, "aurora": 0x6BC56B, "flash": 0x4A9BE8, "eclipse": 0xB08A5A, "halo": 0xF2D64B, "prisma": 0xD9D9E6}
SHOWDOWN = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "showdown_ids.json")))

PRIMARY = {"corona": "fire", "aurora": "grass", "flash": "water", "eclipse": "ground", "halo": "flying", "prisma": "normal"}
# Atq. Spé = ATQ × r, Déf. Spé = DÉF × r
SPECIAL_RATIO = {"corona": (0.85, 0.9), "eclipse": (0.8, 0.9), "aurora": (1.0, 1.0),
                 "flash": (1.15, 1.1), "halo": (1.15, 1.05), "prisma": (1.0, 1.0)}

# second type par lignée (family DirtRobes) ; une espèce peut avoir sa propre entrée dans SPECIES_TYPE2
FAMILIES = {
    "anubi": "dark", "aoi": "dragon", "azapi": "rock", "azeko": "fighting", "bakuraiya": "dragon",
    "bartor": "rock", "zora": "dark", "boma": "fighting", "komainu": "dark", "komainu_bombzar": "dark",
    "komainu_flamerax": "dragon", "komainu_mizusar": "dragon", "komainu_optoger": "electric",
    "komainu_scarazook": "bug", "tobasu": "bug", "mugon": "dragon", "shogyo": "water", "dongu": "ground",
    "danawa": "ground", "darucho": "flying", "denden": "electric", "dongor": "rock", "elo": "dragon",
    "hananomi": "fairy", "gagong": "steel", "zen": "fighting", "gejio": "bug", "tenkro": "dark",
    "karakuri": "steel", "tamazoa": "psychic", "gorz": "dark", "grilda": "dragon", "haneoto": "flying",
    "hapacha": "psychic", "harumi": "fairy", "hiko": "flying", "inkana": "psychic", "isadora": "dragon",
    "kasumi": "ghost", "kibatekka": "steel", "koroko": "rock", "kubaku": "rock", "leo": "fighting",
    "makanui": "rock", "mantrador": "flying", "masetto": "steel", "mayar": "dragon", "mesa": "ghost",
    "mossari": "grass", "musakabu": "bug", "nagu": "dragon", "nokoko": "steel", "ojimaru": "psychic",
    "paga": "flying", "pago": "ground", "pahacha": "dark", "pegatinum": "fairy", "pepe": "poison",
    "pikos": "bug", "rudacho": "flying", "ryza": "dragon", "sabo": "ground", "sametto": "electric",
    "samukabu": "fighting", "segu": "bug", "senpu": "ice", "shakin": "steel", "shimainu": "fighting",
    "spiko": "ground", "tekka": "steel", "thunba": "electric", "torga": "normal", "totemo": "psychic",
    "troga": "rock", "ukabi": "psychic", "vilar": "steel", "winba": "flying", "zaapi": "dark",
    "zappi": "electric", "zaza": "bug", "zeni": "fire", "zoza": "poison", "zuwa": "rock", "zyra": "ice",
}
SPECIES_TYPE2 = {"vilakroma": "dark"}

# talents : (talent 1, talent 2) selon le type 1, talent caché selon le type 2
ABILITIES_T1 = {"fire": ("blaze", "flashfire"), "grass": ("overgrow", "chlorophyll"), "water": ("torrent", "swiftswim"),
                "ground": ("sandveil", "sturdy"), "flying": ("keeneye", "pressure"), "normal": ("adaptability", "pressure")}
HIDDEN_T2 = {"electric": "static", "steel": "clearbody", "dragon": "roughskin", "rock": "rockhead", "bug": "swarm",
             "psychic": "synchronize", "dark": "intimidate", "ghost": "cursedbody", "poison": "poisonpoint",
             "ice": "icebody", "fighting": "guts", "fairy": "cutecharm", "flying": "bigpecks", "ground": "sandforce",
             "water": "hydration", "grass": "leafguard", "fire": "flamebody", "normal": "scrappy"}

# attaques par type, de la plus faible à la plus forte
LADDERS = {
    "fire": ["ember", "flamecharge", "firefang", "incinerate", "flamethrower", "fireblast", "flareblitz"],
    "grass": ["absorb", "vinewhip", "razorleaf", "megadrain", "seedbomb", "energyball", "leafblade", "solarbeam"],
    "water": ["watergun", "aquajet", "bubblebeam", "waterpulse", "aquatail", "surf", "hydropump"],
    "ground": ["mudslap", "sandattack", "bulldoze", "dig", "earthpower", "earthquake"],
    "flying": ["gust", "peck", "wingattack", "aerialace", "airslash", "drillpeck", "hurricane"],
    "normal": ["tackle", "quickattack", "headbutt", "bodyslam", "hypervoice", "doubleedge", "gigaimpact"],
    "electric": ["thundershock", "spark", "shockwave", "thunderbolt", "wildcharge", "thunder"],
    "steel": ["metalclaw", "irondefense", "ironhead", "flashcannon", "meteormash"],
    "dragon": ["dragonbreath", "twister", "dragonclaw", "dragonpulse", "outrage"],
    "rock": ["rockthrow", "rocktomb", "rockslide", "ancientpower", "stoneedge"],
    "bug": ["bugbite", "strugglebug", "furycutter", "xscissor", "bugbuzz", "megahorn"],
    "psychic": ["confusion", "psybeam", "zenheadbutt", "psychic", "futuresight"],
    "dark": ["bite", "snarl", "crunch", "nightslash", "darkpulse"],
    "ghost": ["astonish", "shadowsneak", "hex", "shadowclaw", "shadowball"],
    "poison": ["poisonsting", "acid", "poisonfang", "sludgebomb", "gunkshot"],
    "ice": ["powdersnow", "iceshard", "icefang", "icebeam", "blizzard"],
    "fighting": ["lowkick", "karatechop", "brickbreak", "drainpunch", "closecombat"],
    "fairy": ["fairywind", "drainingkiss", "dazzlinggleam", "playrough", "moonblast"],
}
STARTERS = ["tackle", "leer"]
UTILITY = {12: "protect", 24: "rest", 36: "endure"}

STAGE_MAX_LEVEL = {"child": 34, "adult": 52, "evolved": 70, "secret": 70, "special": 70}
EXP_GROUP = {"common": "medium_fast", "rare": "medium_slow", "epic": "slow", "secret": "slow"}
BASE_EXP = {"child": 62, "adult": 147, "evolved": 236, "secret": 260, "special": 270}
EV_YIELD = {"child": 1, "adult": 2, "evolved": 3, "secret": 3, "special": 3}
SIZE = {"child": (0.6, 0.6, 6, 120), "adult": (1.0, 1.2, 12, 450), "evolved": (1.4, 1.8, 18, 1200),
        "secret": (1.4, 1.8, 18, 1300), "special": (1.4, 1.8, 18, 1300)}


def valid_move(m):
    info = SHOWDOWN["moves"].get(m)
    return info is not None and info["ns"] is None


def valid_ability(a):
    return a in SHOWDOWN["abilities"] and SHOWDOWN["abilities"][a] is None


def showdown_id(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def cobblemon_name(species_id, display):
    """Nom unique pour Cobblemon/Showdown (plusieurs Komainu existent) : basé sur l'identifiant DirtRobes."""
    if showdown_id(display) == showdown_id(species_id):
        return display
    return " ".join(w.capitalize() for w in species_id.split("_"))


_DR = None


def dirtrobes_resolver(sid, stage):
    """Resolver DirtRobes de l'espèce, sinon celui par défaut de son stade (même règle que ModelRepository)."""
    global _DR
    if _DR is None:
        _DR = {}
        for f in glob.glob(os.path.join(DR_RESOLVERS, "*.json")):
            r = json.load(open(f, encoding="utf-8"))
            _DR[r.get("species", "dirtrobes:" + os.path.basename(f)[:-5])] = r
    return _DR.get(f"dirtrobes:{sid}") or _DR.get(f"default:{stage}") or _DR["default:adult"]


def _tint_rgb(rgb, amount):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return tuple(int(c + (255 - c) * amount) for c in (r, g, b))


def _shift(rgb, variant):
    if variant <= 0:
        return rgb
    h, s_, v = colorsys.rgb_to_hsv(((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255)
    r, g, b = colorsys.hsv_to_rgb((h + variant / 3) % 1, s_, v)
    return (int(r * 255) << 16) | (int(g * 255) << 8) | int(b * 255)


def _tinted(texture, prop, variant, emissive):
    """Texture provisoire teintée comme le fait DirtRobes à l'affichage (Cobblemon ne sait pas teinter)."""
    from PIL import Image
    ns, path = texture.split(":")
    name = os.path.basename(path)[:-4] + f"_{prop}" + (f"_chroma{variant}" if variant else "") + ".png"
    out = os.path.join(ASSETS, TINT_DIR, name)
    color = _shift(PROPERTY_COLOR[prop], variant)
    mult = (color >> 16 & 255, color >> 8 & 255, color & 255) if emissive else _tint_rgb(color, 0.45)
    img = Image.open(os.path.join(ASSETS, path)).convert("RGBA")
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            px[x, y] = (r * mult[0] // 255, g * mult[1] // 255, b * mult[2] // 255, a)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    return f"dirtrobes:{TINT_DIR}/{name}"


_CB_MODELS = {}


def _find_model(model_id):
    name = model_id.split(":")[1] + ".geo.json"
    for folder in ("bedrock/pokemon/models", "bedrock/spectrobes/models"):
        p = os.path.join(ASSETS, folder, name)
        if os.path.exists(p):
            return p, folder
    raise SystemExit(f"Modèle introuvable : {model_id}")


def cobblemon_model(model_id, textures):
    """Référence du modèle pour Cobblemon et textures à utiliser.

    Cobblemon ne lit que les UV « de boîte ». Si le modèle DirtRobes a des UV par face (dessinées librement dans
    Blockbench), on en fabrique automatiquement une version Cobblemon : mêmes os et cubes, UV de boîte rangées
    sous la texture d'origine, et pour chaque texture une copie agrandie où les pixels de chaque face sont
    recopiés à leur nouvelle place. Tout est régénéré à chaque build : il suffit de modifier la texture DirtRobes."""
    path, folder = _find_model(model_id)
    geo = json.load(open(path, encoding="utf-8"))
    g = geo["minecraft:geometry"][0]
    per_face = any(isinstance(c.get("uv"), dict) for b in g["bones"] for c in b.get("cubes", []))
    if not per_face:
        return model_id + ".geo", {t: t for t in textures if t}
    import math
    from PIL import Image
    W, H = g["description"]["texture_width"], g["description"]["texture_height"]
    # place pour les patrons de boîte sous la texture d'origine
    x = 0
    y = H
    row = 0
    layout = []  # (cube, u, v)
    cubes = [c for b in g["bones"] for c in b.get("cubes", []) if isinstance(c.get("uv"), dict)]
    for c in cubes:
        w, h, d = (max(1, math.ceil(v - 1e-6)) for v in c["size"])
        nw, nh = 2 * (d + w), d + h
        if x + nw > W:
            x, y, row = 0, y + row + 1, 0
        layout.append((c, x, y))
        x += nw + 1
        row = max(row, nh)
    newH = y + row + 1
    newH = max(H * 2, 1 << (newH - 1).bit_length())
    cb = json.loads(json.dumps(geo))
    cg = cb["minecraft:geometry"][0]
    cg["description"]["identifier"] = g["description"]["identifier"] + "_cobblemon"
    cg["description"]["texture_height"] = newH
    by_id = {id(c): (u, v) for c, u, v in layout}
    cb_cubes = [c for b in cg["bones"] for c in b.get("cubes", [])]
    for orig, new in zip([c for b in g["bones"] for c in b.get("cubes", [])], cb_cubes):
        if id(orig) in by_id:
            new["uv"] = list(by_id[id(orig)])
    name = model_id.split(":")[1] + "_cobblemon"
    with open(os.path.join(ASSETS, "bedrock/pokemon/models", name + ".geo.json"), "w", encoding="utf-8") as fh:
        json.dump(cb, fh, indent=2)
    out = {}
    for t in textures:
        if not t:
            continue
        src = Image.open(os.path.join(ASSETS, t.split(":")[1])).convert("RGBA")
        sw, sh = src.size
        sx, sy = sw / W, sh / H  # textures plus grandes que la grille du modèle
        img = Image.new("RGBA", (sw, round(newH * sy)), (0, 0, 0, 0))
        img.paste(src, (0, 0))
        sp, dp = src.load(), img.load()
        for c, bu, bv in layout:
            w, h, d = c["size"][0], c["size"][1], c["size"][2]
            boxes = {"north": (bu + d, bv + d, w, h), "south": (bu + 2 * d + w, bv + d, w, h), "west": (bu, bv + d, d, h),
                     "east": (bu + d + w, bv + d, d, h), "up": (bu + d, bv, w, d), "down": (bu + d + w, bv, w, d)}
            for face, (du, dv, dw, dh) in boxes.items():
                # emplacement « west » du patron de boîte = côté +X = face « east » de Blockbench (et inversement)
                f = c["uv"].get({"east": "west", "west": "east"}.get(face, face))
                if not f or dw <= 0 or dh <= 0:
                    continue
                fu, fv = f["uv"]
                fw, fh = f.get("uv_size", [0, 0])
                for px in range(math.floor(du * sx), math.ceil((du + dw) * sx)):
                    s_ = min(max((px + 0.5) / sx - du, 0) / dw, 0.999)
                    for py in range(math.floor(dv * sy), math.ceil((dv + dh) * sy)):
                        t_ = min(max((py + 0.5) / sy - dv, 0) / dh, 0.999)
                        qx = min(sw - 1, max(0, math.floor((fu + s_ * fw) * sx)))
                        qy = min(sh - 1, max(0, math.floor((fv + t_ * fh) * sy)))
                        if 0 <= px < img.width and 0 <= py < img.height:
                            dp[px, py] = sp[qx, qy]
        rel = f"{TINT_DIR}/{os.path.basename(t.split(':')[1])[:-4]}_cobblemon.png"
        os.makedirs(os.path.join(ASSETS, TINT_DIR), exist_ok=True)
        img.save(os.path.join(ASSETS, rel))
        out[t] = f"dirtrobes:{rel}"
    return f"dirtrobes:{name}.geo", out


_CRIES = {}


def _find_animation(key):
    for f in glob.glob(os.path.join(ASSETS, "bedrock/pokemon/animations/**/*.animation.json"), recursive=True):
        if os.path.basename(f) == "cobblemon_cries.animation.json":
            continue
        anims = json.load(open(f, encoding="utf-8"))["animations"]
        if key in anims:
            return anims[key]
    return None


def cry_poser(sid, poser_id):
    """Poser Cobblemon propre à l'espèce : celui du modèle, dont l'animation de cri joue le cri de l'espèce
    (« dirtrobes:spectrobe.<id>.cry », assets/dirtrobes/sounds/spectrobe/<id>_cry.ogg)."""
    base_path = os.path.join(ASSETS, "bedrock/pokemon/posers", poser_id.split(":")[1] + ".json")
    if not os.path.exists(base_path):
        return poser_id
    poser = json.load(open(base_path, encoding="utf-8"))
    cry = poser.get("animations", {}).get("cry", "")
    m = re.search(r"q\.bedrock_stateful\('([^']+)',\s*'cry'\)", cry)
    base_anim = _find_animation(f"animation.{m.group(1)}.cry") if m else None
    anim = json.loads(json.dumps(base_anim)) if base_anim else {"animation_length": 1.0, "bones": {}}
    anim["sound_effects"] = {"0.0": {"effect": f"dirtrobes:spectrobe.{sid}.cry"}}
    group = f"cb_{sid}"
    _CRIES[f"animation.{group}.cry"] = anim
    poser.setdefault("animations", {})["cry"] = f"q.bedrock_stateful('{group}', 'cry')"
    out = os.path.join(ASSETS, "bedrock/pokemon/posers/cobblemon", f"{group}.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(poser, fh, indent=2)
    return f"dirtrobes:{group}"


def cobblemon_resolver(sid, d):
    """Resolver Cobblemon qui reprend le modèle, le poser et la texture du Spectrobe dans DirtRobes.
    Modèles, animations et posers sont partagés (assets/dirtrobes/bedrock/pokemon/…) : rien n'est copié.
    Seules les textures provisoires teintées par propriété sont générées (Cobblemon ne teinte pas)."""
    stage, prop = d["stage"], d["property"]
    r = dirtrobes_resolver(sid, stage)
    tint = r.get("tint", False)
    texs = [r["texture"], r.get("emissive")] + (r.get("variant_textures") or []) + (r.get("variant_emissives") or [])
    key = (r["model"], tuple(t for t in texs if t))  # chaque texture doit être remappée pour le modèle Cobblemon
    if key not in _CB_MODELS:
        _CB_MODELS[key] = cobblemon_model(r["model"], texs)
    model, remap = _CB_MODELS[key]
    tex = _tinted(r["texture"], prop, 0, False) if tint else remap.get(r["texture"], r["texture"])
    glow = r.get("emissive")
    if glow and tint:
        glow = _tinted(glow, prop, 0, True)
    elif glow:
        glow = remap.get(glow, glow)
    base = {"aspects": [], "poser": cry_poser(sid, r["poser"]), "model": model, "texture": tex,
            "layers": [{"name": "glow", "emissive": True, "texture": glow}] if glow else []}
    variations = [base]
    # Chroma 2 de DirtRobes = chromatique (« shiny ») de Cobblemon
    shiny = (r.get("variant_textures") or [None])[0]
    if shiny:
        shiny = remap.get(shiny, shiny)
    if shiny is None and tint:
        shiny = _tinted(r["texture"], prop, 1, False)
    if shiny:
        v = {"aspects": ["shiny"], "texture": shiny}
        shiny_glow = (r.get("variant_emissives") or [None])[0] or (_tinted(r["emissive"], prop, 1, True) if tint and r.get("emissive") else None)
        if shiny_glow:
            v["layers"] = [{"name": "glow", "emissive": True, "texture": shiny_glow}]
        variations.append(v)
    return {"order": 0, "species": f"dirtrobes:{sid}", "variations": variations}


def learnset(t1, t2, stage):
    top = STAGE_MAX_LEVEL[stage]
    moves = [f"1:{m}" for m in STARTERS]
    l1 = [m for m in LADDERS[t1] if valid_move(m)]
    l2 = [m for m in LADDERS.get(t2, []) if valid_move(m)] if t2 else []
    # type 1 de 1 à top, type 2 décalé de quelques niveaux
    for i, m in enumerate(l1):
        lvl = 1 if i == 0 else round(4 + (top - 4) * i / max(1, len(l1) - 1))
        moves.append(f"{lvl}:{m}")
    for i, m in enumerate(l2):
        lvl = round(8 + (top - 6) * i / max(1, len(l2) - 1))
        moves.append(f"{lvl}:{m}")
    for lvl, m in UTILITY.items():
        if lvl <= top and valid_move(m):
            moves.append(f"{lvl}:{m}")
    moves.sort(key=lambda s: int(s.split(":")[0]))
    tms = sorted({m for m in LADDERS[t1] + LADDERS.get(t2 or "", []) if valid_move(m)} | {"protect", "rest", "substitute", "facade"})
    return moves + [f"tm:{m}" for m in tms if valid_move(m)]


def main():
    os.makedirs(OUT, exist_ok=True)
    src = {}
    for f in sorted(glob.glob(os.path.join(SRC, "*.json"))):
        sid = os.path.basename(f)[:-5]
        src[sid] = json.load(open(f))
    # numéros de Pokédex propres à l'espace « dirtrobes » : ordre alphabétique stable
    order = {sid: i + 1 for i, sid in enumerate(sorted(src))}
    names, lang_keys = {}, {}
    for sid, d in src.items():
        names[sid] = cobblemon_name(sid, d["name"])

    for sid, d in src.items():
        prop, stage, rarity = d["property"], d["stage"], d.get("rarity", "common")
        t1 = PRIMARY[prop]
        t2 = SPECIES_TYPE2.get(sid, FAMILIES.get(d.get("family")))
        if t2 == t1:
            t2 = None
        st = d["base_stats"]
        hp, atk, df, spe = st["hp"], st["attack"], st["defense"], st.get("speed", 50)
        ra, rd = SPECIAL_RATIO[prop]
        # Atq. Spé / Déf. Spé : celles de la liste des Spectrobes (6 stats), sinon déduites de ATQ / DÉF
        base = {"hp": hp, "attack": atk, "defence": df, "special_attack": st.get("special_attack", round(atk * ra)),
                "special_defence": st.get("special_defense", round(df * rd)), "speed": spe}
        best = max(("hp", "attack", "defence", "speed"), key=lambda k: base[k])
        ev = {k: 0 for k in base}
        ev[best] = EV_YIELD[stage]
        a1, a2 = ABILITIES_T1[t1]
        hidden = HIDDEN_T2.get(t2) if t2 else "insomnia"
        abilities = [a for a in (a1, a2) if valid_ability(a)] + ([f"h:{hidden}"] if valid_ability(hidden) else [])

        evolutions = []
        nxt = d.get("evolves_into")
        if nxt:
            nid = nxt.split(":")[1]
            evolutions.append({"id": f"{sid}_{nid}", "variant": "level_up", "result": f"dirtrobes:{nid}",
                               "consumeHeldItem": False, "learnableMoves": [],
                               "requirements": [{"variant": "level", "minLevel": d.get("evolution_level", 30)}]})
        rare = d.get("rare_evolution")
        if rare:
            rid = rare["species"].split(":")[1]
            evolutions.append({"id": f"{sid}_{rid}", "variant": "item_interact", "result": f"dirtrobes:{rid}",
                               "requiredContext": "dirtrobes:evolve_mineral", "consumeHeldItem": False,
                               "learnableMoves": [],
                               "requirements": [{"variant": "level", "minLevel": d.get("evolution_level", 30)}]})
        w, h, height, weight = SIZE[stage]
        out = {
            "implemented": True,
            "name": names[sid],
            "nationalPokedexNumber": order[sid],
            "primaryType": t1,
            **({"secondaryType": t2} if t2 else {}),
            "abilities": abilities,
            "baseStats": base,
            "evYield": ev,
            "baseExperienceYield": BASE_EXP[stage],
            "experienceGroup": EXP_GROUP.get(rarity, "medium_slow"),
            "catchRate": 45,
            "baseFriendship": 70,
            "maleRatio": -1,
            "eggCycles": 40,
            "eggGroups": ["undiscovered"],
            "moves": learnset(t1, t2, stage),
            "labels": ["dirtrobes", "spectrobe", stage],
            "aspects": [],
            "pokedex": [],
            "evolutions": evolutions,
            "baseScale": round(d.get("scale", STAGE_SCALE[stage]) * dirtrobes_resolver(sid, stage).get("scale", 1.0), 3),
            "hitbox": {"width": w, "height": h, "fixed": False},
            "height": height,
            "weight": weight,
            "behaviour": {"resting": {"canSleep": True, "willSleepOnBed": True}},
        }
        if d.get("flying"):
            out["behaviour"]["moving"] = {"fly": {"canFly": True}}
        with open(os.path.join(OUT, f"{sid}.json"), "w", encoding="utf-8") as fh:
            json.dump(out, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        os.makedirs(CB_RESOLVERS, exist_ok=True)
        with open(os.path.join(CB_RESOLVERS, f"{sid}.json"), "w", encoding="utf-8") as fh:
            json.dump(cobblemon_resolver(sid, d), fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        lang_keys[f"dirtrobes.species.{showdown_id(names[sid])}.name"] = d["name"]
        lang_keys[f"dirtrobes.species.{showdown_id(names[sid])}.desc"] = ""

    for code in ("fr_fr", "en_us"):
        p = os.path.join(LANG, f"{code}.json")
        lang = json.load(open(p, encoding="utf-8"))
        for k in [k for k in lang if k.startswith("dirtrobes.species.")]:
            del lang[k]
        lang.update(lang_keys)
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(lang, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
    cries = os.path.join(ASSETS, "bedrock/pokemon/animations/cobblemon_cries.animation.json")
    with open(cries, "w", encoding="utf-8") as fh:
        json.dump({"format_version": "1.8.0", "animations": _CRIES}, fh, indent=1)
    # garde-fou : un resolver Cobblemon qui pointe vers un modèle absent ou illisible fait échouer TOUT le
    # rechargement des ressources du jeu (les packs de ressources du joueur sont alors désactivés)
    for f in glob.glob(os.path.join(CB_RESOLVERS, "*.json")):
        for v in json.load(open(f, encoding="utf-8"))["variations"]:
            if "model" not in v:
                continue
            mp = os.path.join(ASSETS, "bedrock/pokemon/models", v["model"].split(":")[1] + ".json")
            assert os.path.exists(mp), f"{f} : modèle Cobblemon introuvable {mp}"
            g = json.load(open(mp, encoding="utf-8"))["minecraft:geometry"][0]
            assert all(isinstance(c.get("uv", [0, 0]), list) for b in g["bones"] for c in b.get("cubes", [])), \
                f"{mp} : UV par face, illisibles par Cobblemon"
    ids = {showdown_id(n) for n in names.values()}
    assert len(ids) == len(names), "noms Showdown en double"
    print(f"{len(src)} espèces Cobblemon générées dans {OUT}")


if __name__ == "__main__":
    main()


def export_doc_table():
    """docs/cobblemon_fiches.tsv : types, talents, attaques… de chaque Spectrobe côté Cobblemon (en français),
    importé par l'onglet « Cobblemon » du document de conception (IMPORTDATA)."""
    fr = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "cobblemon_fr_names.json"), encoding="utf-8"))
    T = lambda t: fr.get(f"cobblemon.type.{t}", t) if t else ""
    A = lambda a: fr.get(f"cobblemon.ability.{a}", a)
    M = lambda m: fr.get(f"cobblemon.move.{m}", m)
    groups = {"medium_fast": "Moyenne", "medium_slow": "Parabolique", "slow": "Lente", "fast": "Rapide"}
    evn = {"hp": "PV", "attack": "ATQ", "defence": "DÉF", "special_attack": "Atq. Spé", "special_defence": "Déf. Spé", "speed": "VIT"}
    src = {os.path.basename(f)[:-5]: json.load(open(f, encoding="utf-8")) for f in glob.glob(os.path.join(SRC, "*.json"))}
    lines = ["\t".join(["Nom (comme dans la liste)", "Type 1", "Type 2", "Talent 1", "Talent 2", "Talent caché",
                        "Attaques apprises (niveau)", "CT", "EV donnés", "Courbe d'XP", "Évolution (Cobblemon)"])]
    for sid in sorted(src):
        d = src[sid]
        c = json.load(open(os.path.join(OUT, f"{sid}.json"), encoding="utf-8"))
        name = d["name"]
        if "_" in sid and sid.split("_")[0] in ("komainu", "komanoto"):
            name = f"{d['name']} (lignée {sid.split('_')[1].capitalize()})"
        normal = [A(a) for a in c["abilities"] if not a.startswith("h:")]
        hidden = [A(a[2:]) for a in c["abilities"] if a.startswith("h:")]
        level = [m for m in c["moves"] if not m.startswith("tm:")]
        tms = [m[3:] for m in c["moves"] if m.startswith("tm:")]
        evo = []
        for e in c["evolutions"]:
            res = src[e["result"].split(":")[1]]["name"]
            lvl = e["requirements"][0]["minLevel"]
            evo.append(f"{res} (niv. {lvl})" if e["variant"] == "level_up" else f"{res} (minéral d'Évolution, niv. {lvl}+)")
        lines.append("\t".join([
            name, T(c["primaryType"]), T(c.get("secondaryType")), normal[0] if normal else "",
            normal[1] if len(normal) > 1 else "", hidden[0] if hidden else "",
            ", ".join(f"{m.split(':')[0]} {M(m.split(':')[1])}" for m in level),
            ", ".join(M(t) for t in tms),
            " · ".join(f"{v} {evn[k]}" for k, v in c["evYield"].items() if v),
            groups.get(c["experienceGroup"], c["experienceGroup"]), " · ".join(evo) or "—"]))
    path = os.path.join(ROOT, "docs", "cobblemon_fiches.tsv")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"Tableau du document : {path}")


if __name__ == "__main__":
    export_doc_table()
