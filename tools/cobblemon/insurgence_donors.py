#!/usr/bin/env python3
"""
Movepools des Spectrobes étoffés à partir de Pokémon d'Insurgence qui leur ressemblent.

  python3 tools/cobblemon/insurgence_donors.py <export.json>

<export.json> : export des onglets Stats, Pokedex et Capacités du classeur « Pokémon Insurgence – Où trouver
chaque Pokémon » (valeurs brutes ; Pokedex lu à partir de la colonne B). Pour chaque lignée de Spectrobes on choisit
jusqu'à trois Pokémon « modèles » de lignées différentes, et on mélange leurs movepools :
- mêmes types Cobblemon (les deux si possible, sinon le premier) ;
- forme finale non légendaire (total ≤ 600) dont la répartition des stats ressemble le plus à celle de la forme
  finale du Spectrobe (similarité cosinus sur les 6 stats) ;
- les stades plus jeunes prennent la pré-évolution correspondante du Pokémon (sinon le même).
Attaques par niveau : tirage pondéré (graine = id du Spectrobe, donc stable) dans le réservoir des modèles ;
CT, tuteur et œuf : ce que plusieurs modèles partagent, plus une part tirée au sort du reste.
Les capacités (niveau, CT/CS, tuteur, œuf) du Pokémon sont traduites en identifiants Showdown et gardées si
Cobblemon les connaît. Résultat : tools/cobblemon/insurgence_movepools.json, fusionné par
gen_cobblemon_species.py avec le movepool de base de chaque Spectrobe.
"""
import json, math, os, random, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SPECIES = os.path.join(ROOT, "src/main/resources/data/dirtrobes/species")
FR = json.load(open(os.path.join(HERE, "cobblemon_fr_names.json"), encoding="utf-8"))
SHOW = json.load(open(os.path.join(HERE, "showdown_ids.json"), encoding="utf-8"))
# légendaires et fabuleux (n° national) : jamais pris comme modèle
LEGEND = set(range(144, 147)) | {150, 151, 243, 244, 245, 249, 250, 251} | set(range(377, 387)) | set(range(480, 495)) \
    | set(range(638, 650)) | set(range(716, 722))
N_DONORS = 3  # nombre de Pokémon dont on mélange les movepools
TYPE_FR = {"normal": "Normal", "fire": "Feu", "water": "Eau", "grass": "Plante", "electric": "Électrik", "ice": "Glace",
           "fighting": "Combat", "poison": "Poison", "ground": "Sol", "flying": "Vol", "psychic": "Psy", "bug": "Insecte",
           "rock": "Roche", "ghost": "Spectre", "dragon": "Dragon", "dark": "Ténèbres", "steel": "Acier", "fairy": "Fée"}


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower().replace("œ", "oe").replace("’", "'")
                  .translate(str.maketrans("àâäéèêëîïôöùûüç", "aaaeeeeiioouuuc")))


MOVE_ID = {norm(v): k.split(".")[-1] for k, v in FR.items() if k.startswith("cobblemon.move.") and isinstance(v, str)}


def move_id(fr):
    m = MOVE_ID.get(norm(fr))
    if m and m in SHOW["moves"] and SHOW["moves"][m]["ns"] is None:
        return m
    return None


def main(path):
    raw = json.load(open(path, encoding="utf-8"))
    stats = next(v for k, v in raw.items() if k.startswith("Stats"))
    dex = next(v for k, v in raw.items() if k.startswith("Pokedex"))
    caps = [r for k, v in raw.items() if "Capacit" in k for r in v]
    poke = {}
    for r in stats:
        if len(r) < 14 or not r[1]:
            continue
        try:
            st = [int(x) for x in r[7:13]]
        except ValueError:
            continue
        try:
            num = int(r[0])
        except ValueError:
            num = 0
        poke[r[1]] = {"types": {r[2], r[3]} - {""}, "t1": r[2], "stats": st, "total": sum(st), "num": num}
    pre = {}
    for r in dex:
        if len(r) >= 9 and r[8].startswith("De "):
            pre[r[1]] = r[8][3:].split(" :")[0].strip()
    evolves = set(pre.values())
    moves = {}
    for r in caps:
        if len(r) < 4:
            continue
        name, meth, lvl, mv = r[:4]
        mid = move_id(mv)
        if not mid:
            continue
        d = moves.setdefault(name, {"level": {}, "tm": set(), "tutor": set(), "egg": set()})
        if meth == "Niveau":
            try:
                l = max(1, int(lvl))
            except ValueError:
                l = 1
            d["level"][mid] = min(d["level"].get(mid, 999), l)
        elif meth in ("CT", "CS"):
            d["tm"].add(mid)
        elif meth == "Œuf":
            d["egg"].add(mid)
        else:
            d["tutor"].add(mid)

    def cos(a, b):
        return sum(x * y for x, y in zip(a, b)) / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))

    sp = {}
    for f in os.listdir(SPECIES):
        d = json.load(open(os.path.join(SPECIES, f), encoding="utf-8"))
        sid = f[:-5]
        bs = d["baseStats"]
        sp[sid] = {"t": (d["primaryType"], d.get("secondaryType")),
                   "stats": [bs["hp"], bs["attack"], bs["defence"], bs["special_attack"], bs["special_defence"], bs["speed"]],
                   "evo": [e["result"].split()[0].split(":")[-1] for e in d.get("evolutions", [])]}
    parent = {}
    for s, d in sp.items():
        for e in d["evo"]:
            parent.setdefault(e, s)
    finals = [s for s, d in sp.items() if not d["evo"]]
    out, used = {}, {}
    for fin in sorted(finals):
        t1, t2 = sp[fin]["t"]
        want = {TYPE_FR[t1]} | ({TYPE_FR[t2]} if t2 else set())
        line, s = [fin], fin
        while s in parent:
            s = parent[s]
            line.append(s)

        def size(n):
            m = moves[n]
            return len(m["level"]) + len(m["tm"]) + len(m["tutor"])

        def chain(n):
            c = [n]
            while c[-1] in pre:
                c.append(pre[c[-1]])
            return c

        cands = [n for n, p in poke.items() if n in moves and n not in evolves and p["total"] <= 600
                 and p["num"] not in LEGEND and size(n) >= 30]
        exact = [n for n in cands if poke[n]["types"] == want]
        near = [n for n in cands if n not in exact and want & poke[n]["types"]]
        if not exact and not near:
            continue

        def score(n):
            # ressemblance des stats, même nombre de stades si possible, et on évite de reprendre toujours les mêmes
            return (cos(poke[n]["stats"], sp[fin]["stats"]) + (0.02 if len(chain(n)) == len(line) else 0)
                    - 0.015 * used.get(n, 0) + (0.03 if n in exact else 0))

        # plusieurs modèles : les meilleurs de mêmes types d'abord, complétés par ceux qui partagent un type,
        # jamais deux de la même lignée
        donors, lines_seen = [], set()
        for n in sorted(exact, key=score, reverse=True) + sorted(near, key=score, reverse=True):
            root = chain(n)[-1].replace("Δ", "").strip()  # une forme Delta compte comme la même lignée
            if root in lines_seen:
                continue
            donors.append(n)
            lines_seen.add(root)
            if len(donors) == N_DONORS:
                break
        for n in donors:
            used[n] = used.get(n, 0) + 1
        rng = random.Random(fin)  # même mélange à chaque génération
        sp_types = {t1.capitalize()} | ({t2.capitalize()} if t2 else set())
        chains = [chain(n) for n in donors]

        def at_stage(c, i):
            n = c[min(i, len(c) - 1)]
            return n if n in moves else c[0]

        # attaques par niveau : réservoir de toutes les lignées modèles, tirage pondéré (une attaque connue de
        # plusieurs modèles ou du type du Spectrobe a plus de chances), pour toute la lignée du Spectrobe
        pool = {}
        for k, c in enumerate(chains):
            for n in c:
                if n in moves:
                    for mv in moves[n]["level"]:
                        pool.setdefault(mv, set()).add(k)
        sizes = [len(moves[n]["level"]) for n in donors]
        target = min(len(pool), round(max(sizes) * 1.3))

        def weight(mv):
            return len(pool[mv]) + (1.0 if SHOW["moves"][mv]["type"] in sp_types else 0) + 0.3

        keys = sorted(pool)
        chosen = set()
        # les attaques de départ (niveau 1) du premier modèle restent, pour que le Spectrobe sache se battre
        chosen |= {mv for mv, l in moves[donors[0]]["level"].items() if l <= 1}
        while len(chosen) < target:
            rest = [mv for mv in keys if mv not in chosen]
            chosen.add(rng.choices(rest, weights=[weight(mv) for mv in rest])[0])

        def mix(key, keep):
            # CT / tuteur / œuf : ce que plusieurs modèles partagent, plus une part tirée au sort du reste
            cnt = {}
            for c in chains:
                ms = set()
                for n in c:
                    if n in moves:
                        ms |= moves[n][key]
                for mv in ms:
                    cnt[mv] = cnt.get(mv, 0) + 1
            if len(donors) == 1:
                return sorted(cnt)
            common = sorted(mv for mv, k in cnt.items() if k >= 2)
            rest = sorted(mv for mv, k in cnt.items() if k < 2)
            rng.shuffle(rest)
            return sorted(common + rest[:round(len(rest) * keep)])

        tm, tutor, egg = mix("tm", 0.5), mix("tutor", 0.5), mix("egg", 0.4)
        label = ", ".join(donors[:-1]) + " et " + donors[-1] if len(donors) > 1 else donors[0]
        for i, s in enumerate(line):
            stage = [at_stage(c, i) for c in chains]
            lv = []
            for mv in sorted(chosen):
                # niveau : celui du premier modèle qui l'apprend à ce stade, sinon à sa forme finale
                l = next((moves[n]["level"][mv] for n in stage if mv in moves[n]["level"]), None)
                if l is None:
                    # niveau réel chez les autres formes (un niveau 1 de forme évoluée n'est qu'un rappel :
                    # les stades plus jeunes ne l'apprennent alors pas)
                    real = [moves[n]["level"][mv] for c in chains for n in c if n in moves and moves[n]["level"].get(mv, 0) > 1]
                    if not real:
                        continue
                    l = min(real)
                lv.append([l, mv])
            out[s] = {"donor": label, "donors": [stage[k] for k in range(len(donors))],
                      "level": sorted(lv, key=lambda x: (x[0], x[1])), "tm": tm, "tutor": tutor, "egg": egg}
    json.dump(out, open(os.path.join(HERE, "insurgence_movepools.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(len(out), "Spectrobes avec un Pokémon modèle")
    for s in ("komainu", "komanoto", "komadoros", "vilar", "aoi", "aobasar", "shakin", "zozanero", "zazanero"):
        if s in out:
            o = out[s]
            print(f"  {s}: {', '.join(o['donors'])} ({len(o['level'])} niveau, {len(o['tm'])} CT, {len(o['tutor'])} tuteur, {len(o['egg'])} œuf)")


if __name__ == "__main__":
    main(sys.argv[1])
