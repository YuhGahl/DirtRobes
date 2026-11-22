#!/usr/bin/env python3
"""
Importe un modèle Blockbench (.bbmodel, format Bedrock, UV en boîte) dans DirtRobes.

  python3 tools/models/import_bbmodel.py <spec.json> [chemin du .bbmodel]

Le fichier spec (tools/models/specs/<espèce>.json) dit :
  - "source"  : le .bbmodel de l'utilisateur (non modifié) ;
  - "id"      : nom du modèle (komainu) ;
  - "bones"   : os à créer, dans l'ordre, {"name", "parent", "pivot" (coordonnées Blockbench)} ;
  - "rules"   : [motif regex sur le nom de l'élément, os] (premier motif qui correspond) ;
  - "colors"  : [motif regex, couleur #RRGGBB] pour la texture provisoire.

Sorties :
  - assets/dirtrobes/bedrock/pokemon/models/<id>.geo.json (os renommés pour les animations : head, body,
    ear_left, front_leg_left…) ;
  - assets/dirtrobes/textures/spectrobes/<id>.png : texture PROVISOIRE (aplats de couleur) avec des UV
    rangées proprement en 64×64 ;
  - <dossier du .bbmodel>/<id>_dirtrobes.bbmodel : copie du modèle avec ces os, ces UV et la texture
    provisoire intégrée, à repeindre dans Blockbench (le .bbmodel d'origine n'est pas touché).
"""
import base64, copy, io, json, math, os, re, sys, uuid
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")
TEX = 64


def shade(hex_color, f):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return tuple(max(0, min(255, round(c * f))) for c in (r, g, b)) + (255,)


def main(spec_path):
    spec = json.load(open(spec_path, encoding="utf-8"))
    src = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(spec_path)), spec["source"])
    bb = json.load(open(src, encoding="utf-8"))
    mid = spec["id"]
    elements = [e for e in bb["elements"] if e.get("type", "cube") == "cube"]
    # « keep_uv » : on garde les UV dessinées dans Blockbench (sinon on les range en 64×64)
    keep_uv = spec.get("keep_uv", False)
    global TEX
    if keep_uv:
        TEX = bb.get("resolution", {}).get("width", 64)
    texh = bb.get("resolution", {}).get("height", TEX) if keep_uv else TEX

    def bone_of(name):
        for pat, bone in spec["rules"]:
            if re.search(pat, name, re.I):
                return bone
        raise SystemExit(f"Aucun os pour l'élément {name}")

    def color_of(name):
        for pat, col in spec["colors"]:
            if re.search(pat, name, re.I):
                return col
        return "#C08040"

    # ---- rangement des UV (patron de boîte : largeur 2(p+l), hauteur p+h) ----
    x = y = row_h = 0
    layout = {}
    for e in ([] if keep_uv else sorted(elements, key=lambda e: -(math.ceil(e["to"][1] - e["from"][1]) + math.ceil(e["to"][2] - e["from"][2])))):
        w, h, d = (max(1, math.ceil(e["to"][i] - e["from"][i] - 1e-6)) for i in range(3))
        W, H = 2 * (d + w), d + h
        if x + W > TEX:
            x, y, row_h = 0, y + row_h + 1, 0
        if y + H > TEX:
            raise SystemExit("Texture 64×64 trop petite pour ce modèle")
        layout[e["uuid"]] = (x, y, w, h, d)
        x += W + 1
        row_h = max(row_h, H)

    # ---- texture provisoire ----
    img = Image.new("RGBA", (TEX, texh), (0, 0, 0, 0))
    px = img.load()
    if keep_uv:
        # chaque face peinte à l'endroit défini dans Blockbench (dessus plus clair, dessous plus sombre)
        for e in elements:
            col = color_of(e["name"])
            for face, f in e["faces"].items():
                if f.get("texture") is None or "uv" not in f:
                    continue
                u0, v0, u1, v1 = f["uv"]
                c = shade(col, {"up": 1.18, "down": 0.8}.get(face, 1.0 if face in ("north", "south") else 0.92))
                iu0, iu1 = math.floor(min(u0, u1)), max(math.ceil(max(u0, u1)), math.floor(min(u0, u1)) + 1)
                iv0, iv1 = math.floor(min(v0, v1)), max(math.ceil(max(v0, v1)), math.floor(min(v0, v1)) + 1)
                for i in range(iu0, iu1):
                    for j in range(iv0, iv1):
                        if 0 <= i < TEX and 0 <= j < texh:
                            px[i, j] = c
    for e in ([] if keep_uv else elements):
        u, v, w, h, d = layout[e["uuid"]]
        col = color_of(e["name"])
        for i in range(2 * (d + w)):
            for j in range(d + h):
                if j < d:  # dessus / dessous
                    c = shade(col, 1.18 if i < d + 2 * w and i >= d else 1.0)
                else:      # côtés, plus sombres vers le bas
                    c = shade(col, 1.0 - 0.18 * (j - d) / max(1, h))
                px[u + i, v + j] = c
    tex_path = os.path.join(ASSETS, "textures/spectrobes", f"{mid}.png")
    os.makedirs(os.path.dirname(tex_path), exist_ok=True)
    img.save(tex_path)

    # ---- géométrie Bedrock (x miroir comme l'export Blockbench) ----
    bones_out = []
    for b in spec["bones"]:
        bo = {"name": b["name"], "pivot": [-b["pivot"][0], b["pivot"][1], b["pivot"][2]]}
        if b.get("parent"):
            bo["parent"] = b["parent"]
        bo["cubes"] = []
        bones_out.append(bo)
    by_name = {b["name"]: b for b in bones_out}
    for e in elements:
        f, t = e["from"], e["to"]
        cube = {"origin": [round(-t[0], 4), f[1], f[2]], "size": [round(t[i] - f[i], 4) for i in range(3)]}
        if keep_uv:
            # UV par face, comme l'export Bedrock de Blockbench (dessus et dessous retournés)
            faces = {}
            for name, fc in e["faces"].items():
                if fc.get("texture") is None or "uv" not in fc:
                    continue
                a, b, c2, d2 = fc["uv"]
                uv, size = [a, b], [c2 - a, d2 - b]
                if name in ("up", "down"):
                    uv = [uv[0] + size[0], uv[1] + size[1]]
                    size = [-size[0], -size[1]]
                faces[name] = {"uv": uv, "uv_size": size}
                if fc.get("rotation"):
                    faces[name]["uv_rotation"] = fc["rotation"]
            cube["uv"] = faces
        else:
            u, v, *_ = layout[e["uuid"]]
            cube["uv"] = [u, v]
        rot = e.get("rotation", [0, 0, 0])
        if any(rot):
            o = e.get("origin", [0, 0, 0])
            cube["pivot"] = [-o[0], o[1], o[2]]
            cube["rotation"] = [-rot[0], -rot[1], rot[2]]
        if e.get("inflate"):
            cube["inflate"] = e["inflate"]
        if e.get("mirror_uv") and not keep_uv:
            cube["mirror"] = True
        by_name[bone_of(e["name"])]["cubes"].append(cube)
    for b in bones_out:
        if not b["cubes"]:
            del b["cubes"]
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{mid}", "texture_width": TEX, "texture_height": texh,
                        "visible_bounds_width": 3, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, 0.75, 0]},
        "bones": bones_out}]}
    geo_path = os.path.join(ASSETS, "bedrock/pokemon/models", f"{mid}.geo.json")
    with open(geo_path, "w", encoding="utf-8") as fh:
        json.dump(geo, fh, indent=2)

    # ---- copie du .bbmodel pour Blockbench ----
    out = copy.deepcopy(bb)
    groups, by_group = [], {}
    for b in spec["bones"]:
        g = {"name": b["name"], "uuid": str(uuid.uuid4()), "export": True, "locked": False, "scope": 0, "visibility": True,
             "origin": b["pivot"], "rotation": [0, 0, 0], "bedrock_binding": "", "color": 0, "children": [], "reset": False,
             "shade": True, "mirror_uv": False, "autouv": 0, "isOpen": True}
        groups.append(g)
        by_group[b["name"]] = {"uuid": g["uuid"], "isOpen": True, "children": []}
    for e in out["elements"]:
        if keep_uv:
            by_group[bone_of(e["name"])]["children"].append(e["uuid"])
            continue
        if e["uuid"] not in layout:
            continue
        u, v, *_ = layout[e["uuid"]]
        e["uv_offset"] = [u, v]
        w, h, d = (e["to"][i] - e["from"][i] for i in range(3))
        e["faces"] = {
            "north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0},
            "east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0},
            "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
            "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
            "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0},
            "down": {"uv": [u + d + 2 * w, v, u + d + w, v + d], "texture": 0},
        }
        by_group[bone_of(e["name"])]["children"].append(e["uuid"])
    roots = []
    for b in spec["bones"]:
        node = by_group[b["name"]]
        if b.get("parent"):
            by_group[b["parent"]]["children"].append(node)
        else:
            roots.append(node)
    out["groups"] = groups
    out["outliner"] = roots
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    out["textures"] = [{"name": f"{mid}.png", "id": "0", "width": TEX, "height": texh, "uv_width": TEX, "uv_height": texh,
                        "particle": False, "render_mode": "default", "render_sides": "auto", "visible": True, "mode": "bitmap",
                        "saved": False, "uuid": str(uuid.uuid4()), "relative_path": f"{mid}.png",
                        "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}]
    out["model_identifier"] = mid
    out["resolution"] = {"width": TEX, "height": texh}
    bb_out = os.path.join(os.path.dirname(src), f"{mid}_dirtrobes.bbmodel")
    with open(bb_out, "w", encoding="utf-8") as fh:
        json.dump(out, fh)
    if keep_uv:
        img.save(os.path.join(os.path.dirname(src), f"{mid}.png"))
    print("modèle :", geo_path, "\ntexture :", tex_path, "\nBlockbench :", bb_out)


if __name__ == "__main__":
    main(sys.argv[1])
