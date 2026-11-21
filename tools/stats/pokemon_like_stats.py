#!/usr/bin/env python3
"""
Stats de base « façon Pokémon » (6 stats) des Spectrobes, selon le stade et la rareté.

Total des stats de base visé (forme finale) :
  Commun ~490 · Rare ~540 (Ronflex 540, Scalpereur 550, starters ~530) ·
  Épique ~570 (Pokémon Paradoxe, Ultra-Chimères 570) · Secret ~600 (pseudo-légendaires : Dracolosse, Tyranocif, Carchacrok).
Formes de base et intermédiaires sur le même modèle (starters 310 / 405 / 530, pseudo-légendaires 300 / 420 / 600).

Profil : on garde la répartition actuelle de chaque Spectrobe (PV / ATQ / DÉF / VIT de la liste) ;
Atq. Spé et Déf. Spé sont déduites de ATQ / DÉF selon la propriété, puis les 6 stats sont mises à l'échelle
du total visé. Petite variation (±3 %) entre Spectrobes d'un même palier, d'après leur total actuel.

Usage : python3 tools/stats/pokemon_like_stats.py [--write]
  sans --write : affiche le résultat ; avec : réécrit base_stats dans les JSON des espèces.
"""
import glob, json, os, statistics, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "src/main/resources/data/dirtrobes/spectrobes/species")

TARGET = {  # stade → rareté → total des 6 stats de base
    "child":   {"common": 300, "rare": 310, "epic": 320, "secret": 330},
    "adult":   {"common": 400, "rare": 410, "epic": 430, "secret": 450},
    "final":   {"common": 490, "rare": 540, "epic": 570, "secret": 600},
}
# Atq. Spé = ATQ × a, Déf. Spé = DÉF × d (style de la propriété)
SPECIAL_RATIO = {"corona": (0.85, 0.9), "eclipse": (0.8, 0.9), "aurora": (1.0, 1.0),
                 "flash": (1.15, 1.1), "halo": (1.15, 1.05), "prisma": (1.0, 1.0)}
# accentue (> 1) ou adoucit (< 1) les écarts entre stats d'un même Spectrobe
EXPONENT = 1.5
KEYS = ["hp", "attack", "defense", "special_attack", "special_defense", "speed"]


def stage_of(d):
    return {"child": "child", "adult": "adult"}.get(d["stage"], "final")


def compute(species):
    # total actuel (4 stats) par palier, pour la petite variation
    groups = {}
    for sid, d in species.items():
        st = d["base_stats"]
        cur = st.get("_old4", st["hp"] + st["attack"] + st["defense"] + st["speed"])
        groups.setdefault((stage_of(d), d["rarity"]), []).append(cur)
    med = {k: statistics.median(v) for k, v in groups.items()}
    # médiane de chaque stat par stade : le profil d'un Spectrobe = ses forces relatives (les PV d'Origins sont
    # bien plus hauts que les autres stats ; chez les Pokémon chaque stat pèse en moyenne 1/6 du total)
    stat_med = {}
    for st4 in ("hp", "attack", "defense", "speed"):
        for stg in ("child", "adult", "final"):
            stat_med[(stg, st4)] = statistics.median(d["base_stats"][st4] for d in species.values() if stage_of(d) == stg)
    out = {}
    for sid, d in species.items():
        st = d["base_stats"]
        old = {k: st[k] for k in ("hp", "attack", "defense", "speed")}
        if "_old" in st:
            old = st["_old"]
        a, r = SPECIAL_RATIO[d["property"]]
        rel = {k: (old[k] / stat_med[(stage_of(d), k)]) ** EXPONENT for k in old}
        prof = {"hp": rel["hp"], "attack": rel["attack"], "defense": rel["defense"],
                "special_attack": rel["attack"] * a, "special_defense": rel["defense"] * r, "speed": rel["speed"]}
        cur4 = old["hp"] + old["attack"] + old["defense"] + old["speed"]
        key = (stage_of(d), d["rarity"])
        var = max(0.97, min(1.03, cur4 / med[key]))
        total = round(TARGET[key[0]][key[1]] * var)
        s = sum(prof.values())
        vals = {k: max(5, min(255, round(prof[k] * total / s))) for k in KEYS}
        diff = total - sum(vals.values())
        big = max(KEYS, key=lambda k: vals[k])
        vals[big] += diff
        out[sid] = (vals, old)
    return out


def main():
    species = {os.path.basename(f)[:-5]: json.load(open(f, encoding="utf-8")) for f in glob.glob(os.path.join(SRC, "*.json"))}
    res = compute(species)
    if "--write" in sys.argv:
        for sid, (vals, _) in res.items():
            p = os.path.join(SRC, sid + ".json")
            d = json.load(open(p, encoding="utf-8"))
            d["base_stats"] = {k: vals[k] for k in KEYS}
            with open(p, "w", encoding="utf-8") as fh:
                json.dump(d, fh, ensure_ascii=False, indent=2)
        print(f"{len(res)} espèces réécrites")
    else:
        for sid in sorted(res):
            v, o = res[sid]
            print(f"{sid:14s} {species[sid]['stage']:8s} {species[sid]['rarity']:7s} {o} -> {[v[k] for k in KEYS]} = {sum(v.values())}")


if __name__ == "__main__":
    main()
