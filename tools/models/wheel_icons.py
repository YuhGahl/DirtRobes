#!/usr/bin/env python3
"""Icônes PROVISOIRES de la roue d'interaction (16×16), dessinées en pixels 8×8 agrandis ×2.
Sortie : src/main/resources/assets/dirtrobes/textures/gui/wheel/<nom>.png"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "../../src/main/resources/assets/dirtrobes/textures/gui/wheel")
W, A, B = (240, 240, 240, 255), (120, 200, 255, 255), (255, 200, 90, 255)
ICONS = {
    "stay": ["..#.#...", ".##.##..", ".######.", ".######.", "..#####.", "..####..", "..####..", "...##..."],
    "follow": ["........", "..#.....", ".##..#..", "..#.##..", "....#...", ".#......", "##......", ".#......"],
    "shoulder": ["....bb..", "....bb..", "..##....", ".####...", "######..", "######..", "######..", ".####..."],
    "dismount": ["...##...", "...##...", "...##...", "...##...", ".######.", "..####..", "...##...", "########"],
    "ride": ["........", "..b.....", ".bbbbbb.", "bbbbbbbb", ".bb..bb.", ".#....#.", ".#....#.", ".#....#."],
    "summary": ["######..", "#....#..", "#.##.#..", "#....#..", "#.####..", "#....#..", "#.###...", "######.."],
    "recall": ["...a....", "..aaa...", ".aa.aa..", "aa...aa.", ".aa.aa..", "..aaa...", "...a....", "........"],
    "pc": ["########", "#aaaaaa#", "#aaaaaa#", "#aaaaaa#", "########", "...##...", "..####..", "........"],
    "cobblemon": ["..#.....", ".##.....", "######..", ".##...#.", "..#...##", "..######", "......##", "......#."],
}
COL = {"#": W, "a": A, "b": B}
os.makedirs(OUT, exist_ok=True)
for name, rows in ICONS.items():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in COL:
                for dx in range(2):
                    for dy in range(2):
                        img.putpixel((x * 2 + dx, y * 2 + dy), COL[ch])
    img.save(os.path.join(OUT, name + ".png"))
print(len(ICONS), "icônes dans", os.path.normpath(OUT))
