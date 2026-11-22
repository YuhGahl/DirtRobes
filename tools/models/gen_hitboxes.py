#!/usr/bin/env python3
"""
Hitbox de chaque Spectrobe calculée sur son modèle, comme les fiches Cobblemon (« hitbox » largeur × hauteur).

  python3 tools/models/gen_hitboxes.py

Pour chaque espèce de data/dirtrobes/spectrobes/species : on prend le modèle de son resolver DirtRobes
(le sien, sinon celui par défaut de son stade), on mesure ses cubes au repos et on écrit dans la fiche :
  "hitbox": {"width": …, "height": …}   (en blocs, avant la taille de l'espèce « scale »)
- hauteur = hauteur du modèle ;
- largeur = moyenne géométrique largeur × longueur, plafonnée à 1,5 × la largeur (une boîte Minecraft est
  carrée : un animal long n'a pas une boîte aussi longue que lui, comme chez Cobblemon) ;
le tout multiplié par le « scale » du resolver (la taille d'affichage du modèle).
Une fiche avec "hitbox_manual": true n'est jamais modifiée.
"""
import glob, json, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")
SPECIES = os.path.join(ROOT, "src/main/resources/data/dirtrobes/spectrobes/species")
RESOLVERS = os.path.join(ASSETS, "bedrock/spectrobes/resolvers")


def resolvers():
    out = {}
    for f in glob.glob(os.path.join(RESOLVERS, "*.json")):
        r = json.load(open(f, encoding="utf-8"))
        out[r.get("species", "dirtrobes:" + os.path.basename(f)[:-5])] = r
    return out


def model_file(model_id):
    name = model_id.split(":")[-1]
    if name.endswith(".geo"):
        name = name[:-4]
    for folder in ("spectrobes", "pokemon"):
        p = os.path.join(ASSETS, "bedrock", folder, "models", name + ".geo.json")
        if os.path.exists(p):
            return p
    raise SystemExit(f"modèle introuvable : {model_id}")


_BOUNDS = {}


def bounds(path):
    if path not in _BOUNDS:
        g = json.load(open(path, encoding="utf-8"))["minecraft:geometry"][0]
        lo, hi = [1e9] * 3, [-1e9] * 3
        for b in g["bones"]:
            for c in b.get("cubes", []):
                i = c.get("inflate", 0)
                for k in range(3):
                    lo[k] = min(lo[k], c["origin"][k] - i)
                    hi[k] = max(hi[k], c["origin"][k] + c["size"][k] + i)
        _BOUNDS[path] = [(hi[k] - lo[k]) / 16 for k in range(3)]
    return _BOUNDS[path]


def r05(x):
    return max(0.2, round(x * 20) / 20)


def main():
    res = resolvers()
    n = 0
    for f in sorted(glob.glob(os.path.join(SPECIES, "*.json"))):
        sid = os.path.basename(f)[:-5]
        d = json.load(open(f, encoding="utf-8"))
        if d.get("hitbox_manual"):
            continue
        r = res.get(f"dirtrobes:{sid}") or res.get(f"default:{d.get('stage', 'child')}") or res["default:adult"]
        sx, sy, sz = bounds(model_file(r["model"]))
        k = r.get("scale", 1.0)
        hb = {"width": r05(min(math.sqrt(sx * sz), 1.5 * sx) * k), "height": r05(sy * k)}
        if d.get("hitbox") != hb:
            d["hitbox"] = hb
            with open(f, "w", encoding="utf-8") as fh:
                json.dump(d, fh, indent=2, ensure_ascii=False)
                fh.write("\n")
            n += 1
    print(f"hitbox : {n} fiches mises à jour")


if __name__ == "__main__":
    main()
