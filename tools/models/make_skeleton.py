#!/usr/bin/env python3
"""
Squelette Blockbench prêt à sculpter pour un Spectrobe (os, pivots, échelle), et sa spec d'import.

  python3 tools/models/make_skeleton.py komanoto

Écrit :
  - tools/models/skeletons/<id>_squelette.bbmodel : dossiers (os) avec leur pivot, et un cube gris « repere_… »
    par os pour voir l'échelle (ils ne sont jamais exportés dans le mod : on peut les laisser ou les supprimer) ;
  - tools/models/specs/<id>.json : spec pour import_bbmodel.py (« use_groups » : un cube appartient à l'os du
    dossier où on le range).
Les formes sont des blocs neutres : c'est à toi de modeler la créature par-dessus.
"""
import json, os, sys, uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Quadrupède adulte, l'avant vers -z (comme Komainu), « left » = +x. 16 px = 1 bloc (avant la taille de l'espèce).
# (nom, parent, pivot, repère from, repère to)
QUADRUPED_ADULT = [
    ("root", None, [0, 0, 0], None, None),
    ("body", "root", [0, 12, 0], [-5, 8, -8], [5, 17, 8]),
    ("neck", "body", [0, 15, -7], [-3, 13, -11], [3, 19, -6]),
    ("head", "neck", [0, 18, -10], [-4, 16, -17], [4, 23, -10]),
    ("jaw", "head", [0, 17, -11], [-3, 15, -16], [3, 17, -11]),
    ("eye_left", "head", [2, 20, -17], [1.5, 19.5, -17.1], [3, 21, -17]),
    ("eye_right", "head", [-2, 20, -17], [-3, 19.5, -17.1], [-1.5, 21, -17]),
    ("ear_left", "head", [3, 23, -12], [2.5, 23, -13], [4, 26, -11]),
    ("ear_right", "head", [-3, 23, -12], [-4, 23, -13], [-2.5, 26, -11]),
    ("back_1", "body", [0, 17, -4], [-0.5, 17, -5], [0.5, 20, -3]),
    ("back_2", "body", [0, 17, 1], [-0.5, 17, 0], [0.5, 20, 2]),
    ("back_3", "body", [0, 17, 6], [-0.5, 17, 5], [0.5, 20, 7]),
    ("tail", "body", [0, 15, 8], [-1, 14, 8], [1, 16, 13]),
    ("tail_2", "tail", [0, 15, 13], [-1, 14, 13], [1, 16, 17]),
    ("tail_3", "tail_2", [0, 15, 17], [-1, 14, 17], [1, 16, 21]),
    ("front_leg_left", "body", [4, 10, -5], [2.5, 5, -6.5], [5.5, 10, -3.5]),
    ("front_foot_left", "front_leg_left", [4, 5, -5], [2.5, 0, -7], [5.5, 5, -3.5]),
    ("front_leg_right", "body", [-4, 10, -5], [-5.5, 5, -6.5], [-2.5, 10, -3.5]),
    ("front_foot_right", "front_leg_right", [-4, 5, -5], [-5.5, 0, -7], [-2.5, 5, -3.5]),
    ("back_leg_left", "body", [4, 10, 5], [2.5, 5, 3.5], [5.5, 10, 6.5]),
    ("back_foot_left", "back_leg_left", [4, 5, 5], [2.5, 0, 3], [5.5, 5, 6.5]),
    ("back_leg_right", "body", [-4, 10, 5], [-5.5, 5, 3.5], [-2.5, 10, 6.5]),
    ("back_foot_right", "back_leg_right", [-4, 5, 5], [-5.5, 0, 3], [-2.5, 5, 6.5]),
]


def main(mid):
    bones = [(mid if n == "root" else n, (mid if p == "root" else p), piv, f, t) for n, p, piv, f, t in QUADRUPED_ADULT]
    elements, nodes = [], {}
    for name, parent, piv, f, t in bones:
        nodes[name] = {"name": name, "origin": piv, "rotation": [0, 0, 0], "color": 0, "uuid": str(uuid.uuid4()),
                       "export": True, "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": []}
        if f is not None:
            e = {"name": "repere_" + name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                 "allow_mirror_modeling": True, "from": f, "to": t, "autouv": 0, "color": 7, "origin": piv,
                 "uv_offset": [0, 0], "faces": {k: {"uv": [0, 0, 0, 0], "texture": None} for k in
                                                ("north", "east", "south", "west", "up", "down")},
                 "type": "cube", "uuid": str(uuid.uuid4())}
            elements.append(e)
            nodes[name]["children"].append(e["uuid"])
    roots = []
    for name, parent, *_ in bones:
        (nodes[parent]["children"] if parent else roots).append(nodes[name])
    bb = {"meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": True},
          "name": mid, "model_identifier": mid, "visible_box": [3, 2.5, 0.75], "variable_placeholders": "",
          "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
          "resolution": {"width": 64, "height": 64}, "elements": elements, "outliner": roots, "textures": []}
    out = os.path.join(ROOT, "tools/models/skeletons", f"{mid}_squelette.bbmodel")
    json.dump(bb, open(out, "w", encoding="utf-8"), indent=1)
    spec = {"source": f"{mid}.bbmodel", "id": mid, "use_groups": True, "keep_uv": True,
            "bones": [{"name": n, **({"parent": p} if p else {}), "pivot": piv} for n, p, piv, *_ in bones],
            "rules": [[".", "body"]], "colors": [[".", "#B0B0B0"]]}
    json.dump(spec, open(os.path.join(ROOT, "tools/models/specs", f"{mid}.json"), "w", encoding="utf-8"), indent=2)
    print("écrit :", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "komanoto")
