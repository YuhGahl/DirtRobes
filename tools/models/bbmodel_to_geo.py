#!/usr/bin/env python3
"""Export Bedrock d'un .bbmodel en UV de boîte (comme « Exporter le modèle Bedrock » de Blockbench)."""
import json, sys
def export(bbpath, ident, rename=None):
    bb = json.load(open(bbpath))
    rename = rename or {}
    groups = {g['uuid']: g for g in bb.get('groups', [])}
    els = {e['uuid']: e for e in bb['elements']}
    bones = []
    def nm(n): return rename.get(n, n)
    def walk(node, parent):
        g = groups.get(node.get('uuid'), node)
        o = g.get('origin', [0, 0, 0])
        b = {'name': nm(g['name']), 'pivot': [-o[0], o[1], o[2]]}
        if parent: b['parent'] = parent
        r = g.get('rotation', [0, 0, 0])
        if any(r): b['rotation'] = [-r[0], -r[1], r[2]]
        cubes = []
        for c in node.get('children', []):
            if isinstance(c, str) and c in els:
                e = els[c]
                if e.get('export') is False or e.get('type', 'cube') != 'cube': continue
                f, t = e['from'], e['to']
                cube = {'origin': [round(-t[0], 4), f[1], f[2]], 'size': [round(t[i] - f[i], 4) for i in range(3)],
                        'uv': e.get('uv_offset') or [0, 0]}
                if e.get('inflate'): cube['inflate'] = e['inflate']
                if e.get('mirror_uv'): cube['mirror'] = True
                er = e.get('rotation', [0, 0, 0])
                if any(er):
                    eo = e.get('origin', [0, 0, 0])
                    cube['pivot'] = [-eo[0], eo[1], eo[2]]; cube['rotation'] = [-er[0], -er[1], er[2]]
                cubes.append(cube)
        if cubes: b['cubes'] = cubes
        # dossier vide « locator_xxx » : repère Cobblemon « xxx » à son pivot
        if not cubes and g['name'].startswith('locator_'):
            b['locators'] = {g['name'][len('locator_'):]: [-o[0], o[1], o[2]]}
        bones.append(b)
        for c in node.get('children', []):
            if not isinstance(c, str): walk(c, b['name'])
    for n in bb['outliner']:
        if not isinstance(n, str): walk(n, None)
    W, H = bb['resolution']['width'], bb['resolution']['height']
    return {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {'identifier': f'geometry.{ident}',
            'texture_width': W, 'texture_height': H, 'visible_bounds_width': 6, 'visible_bounds_height': 5,
            'visible_bounds_offset': [0, 2, 0]}, 'bones': bones}]}
if __name__ == '__main__':
    g = export(sys.argv[1], sys.argv[2], {'guardianlionevo': sys.argv[2], 'guardianlion': sys.argv[2]})
    json.dump(g, open(sys.argv[3], 'w'), indent=2)
    xs=[];ys=[];zs=[]
    for b in g['minecraft:geometry'][0]['bones']:
        for c in b.get('cubes',[]):
            o,s=c['origin'],c['size']; xs+=[o[0],o[0]+s[0]]; ys+=[o[1],o[1]+s[1]]; zs+=[o[2],o[2]+s[2]]
    print('bounds', min(xs),max(xs),min(ys),max(ys),min(zs),max(zs))
