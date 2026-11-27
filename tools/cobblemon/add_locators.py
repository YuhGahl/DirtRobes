#!/usr/bin/env python3
"""
Repères (« locators ») de Cobblemon dans les modèles des Spectrobes vus par Cobblemon.

  python3 tools/cobblemon/add_locators.py

Cobblemon place ses effets sur des points nommés du modèle : « target » et « middle » (visée et impacts des
attaques), « mouth », « physical », « special » (départ des attaques), « head », « face », « eye1/eye2 »,
« item » (objet tenu), « top », « tail », « foot_… ». Ce sont les petites boîtes vertes qu'on voit avec F3 + B
sur les Pokémon. Ce script les calcule à partir des cubes de chaque os (tête, corps, pattes, queue) et les range
dans l'os qui convient, pour qu'ils suivent les animations. Il ne touche qu'aux modèles de
assets/dirtrobes/bedrock/pokemon/models (le modèle DirtRobes ignore ces repères).
"""
import glob, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS = os.path.join(ROOT, "src/main/resources/assets/dirtrobes/bedrock/pokemon/models")


def bounds(cubes):
    lo, hi = [1e9] * 3, [-1e9] * 3
    for c in cubes:
        i = c.get("inflate", 0)
        for k in range(3):
            lo[k] = min(lo[k], c["origin"][k] - i)
            hi[k] = max(hi[k], c["origin"][k] + c["size"][k] + i)
    return lo, hi


def r(v):
    return [round(x, 3) for x in v]


def add(path):
    geo = json.load(open(path, encoding="utf-8"))
    bones = geo["minecraft:geometry"][0]["bones"]
    by = {b["name"]: b for b in bones}
    children = {}
    for b in bones:
        children.setdefault(b.get("parent"), []).append(b["name"])

    def subtree(name):
        out = list(by[name].get("cubes", []))
        for c in children.get(name, []):
            out += subtree(c)
        return out

    # repères déjà présents (posés dans Blockbench, ou modèles provisoires) : jamais remplacés
    existing = {n for b in bones if b["name"].startswith("locator_") for n in b.get("locators", {})}
    root = next(b for b in bones if not b.get("parent"))
    allc = subtree(root["name"])
    if not allc:
        return False
    lo, hi = bounds(allc)
    mid = [(lo[k] + hi[k]) / 2 for k in range(3)]
    loc = {}  # os -> {nom: position}

    def put(bone, name, pos):
        if name in existing:
            return
        loc.setdefault(bone if bone in by else root["name"], {})[name] = r(pos)

    body = next((n for n in ("body", "corps") if n in by), root["name"])
    put(root["name"], "root", [0, 0, 0])
    put(body, "middle", mid)
    put(body, "target", mid)
    put(body, "top", [mid[0], hi[1], mid[2]])
    hb = next((n for n in ("head_ai", "head", "tete") if n in by and subtree(n)), None)
    if hb:
        hl, hh = bounds(subtree(hb))
        hc = [(hl[k] + hh[k]) / 2 for k in range(3)]
        front = [hc[0], hl[1] + (hh[1] - hl[1]) * 0.35, hl[2]]  # l'avant du modèle est vers -z
        put(hb, "head", hc)
        put(hb, "face", [hc[0], hc[1], hl[2]])
        for n in ("mouth", "physical", "special", "item", "item_face"):
            put(hb, n, front)
        put(hb, "item_hat", [hc[0], hh[1], hc[2]])
        for eye, n in (("eye_left", "eye2"), ("eye_right", "eye1"), ("yeux", "eye1")):
            if eye in by and by[eye].get("cubes"):
                el, eh = bounds(by[eye]["cubes"])
                put(eye, n, [(el[k] + eh[k]) / 2 for k in range(3)])
    else:
        for n in ("head", "face", "mouth", "physical", "special", "item"):
            put(body, n, [mid[0], hi[1] * 0.8, lo[2]])
    tail = next((n for n in ("tail", "queue", "queue1") if n in by), None)
    if tail:
        tc = subtree(tail)
        if tc:
            tl, th = bounds(tc)
            put(tail, "tail", by[tail]["pivot"])
            put(tail, "tail_tip", [(tl[0] + th[0]) / 2, (tl[1] + th[1]) / 2, th[2]])
    for legs, n in ((("front_leg_left", "leg_front_left"), "foot_primary1"), (("front_leg_right", "leg_front_right"), "foot_primary2"),
                    (("back_leg_left", "leg_back_left"), "foot_secondary1"), (("back_leg_right", "leg_back_right"), "foot_secondary2")):
        leg = next((l for l in legs if l in by), None)
        if leg and subtree(leg):
            ll, lh = bounds(subtree(leg))
            put(leg, n, [(ll[0] + lh[0]) / 2, ll[1], (ll[2] + lh[2]) / 2])
    changed = False
    for b in bones:
        if b["name"].startswith("locator_"):
            continue
        want = loc.get(b["name"])
        if want and b.get("locators") != want:
            b["locators"] = want
            changed = True
    if changed:
        json.dump(geo, open(path, "w", encoding="utf-8"), indent=2)
    return changed


def main():
    n = sum(add(p) for p in sorted(glob.glob(os.path.join(MODELS, "*.geo.json"))))
    print(f"repères Cobblemon : {n} modèles mis à jour")


if __name__ == "__main__":
    main()
