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

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "src/main/resources/data/dirtrobes/spectrobes/species")
OUT = os.path.join(ROOT, "src/main/resources/data/dirtrobes/species")
LANG = os.path.join(ROOT, "src/main/resources/assets/dirtrobes/lang")
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
            "baseScale": 1.0,
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
