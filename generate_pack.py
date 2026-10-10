"""Generates the Kurogane resource pack (placeholder pixel-art icons + item models).
Replace any PNG in resourcepack/assets/kurogane/textures/item/ with your own art; names must stay the same."""
import json, os, shutil
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
TEX = f"{ROOT}/assets/kurogane/textures/item"
MOD = f"{ROOT}/assets/kurogane/models/item"
ITM = f"{ROOT}/assets/kurogane/items"
for d in (TEX, MOD, ITM): os.makedirs(d, exist_ok=True)

RACES = {"oni": (220, 50, 50), "kitsune": (255, 150, 30), "samurai": (230, 230, 240), "cursed": (150, 20, 20),
         "shinobi": (110, 110, 130), "kappa": (60, 180, 230), "shogun": (240, 200, 50)}

def ability_icon(color, n):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 30, 30), fill=(25, 20, 30, 255), outline=color + (255,), width=2)
    c = color + (255,)
    if n == 1:
        for l in [(16, 5, 16, 27), (5, 16, 27, 16), (8, 8, 24, 24), (8, 24, 24, 8)]: d.line(l, fill=c, width=2)
    elif n == 2:
        d.polygon([(8, 7), (14, 7), (24, 16), (14, 25), (8, 25), (18, 16)], fill=c)
    else:
        d.polygon([(16, 4), (20, 12), (28, 16), (20, 20), (16, 28), (12, 20), (4, 16), (12, 12)], fill=c)
        d.ellipse((13, 13, 19, 19), fill=(255, 255, 255, 255))
    return im

SWORDS = {  # id: (blade, guard)
    "devils_edge": ((150, 0, 0), (20, 20, 20)), "sakura": ((255, 170, 200), (200, 90, 130)),
    "raijin": ((255, 235, 60), (120, 90, 0)), "soul_reaper": ((70, 70, 80), (30, 30, 35)),
    "shadow_piercer": ((110, 40, 160), (30, 10, 50)), "leviathan": ((40, 120, 220), (10, 50, 110)),
    "imperial_blade": ((255, 200, 40), (160, 110, 0)), "steel_katana": ((190, 195, 205), (60, 60, 70))}

def sword_icon(name, blade, guard):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if name == "soul_reaper":
        d.line((5, 28, 24, 6), fill=guard + (255,), width=3)
        d.polygon([(22, 4), (30, 9), (27, 19), (25, 11)], fill=blade + (255,))
        d.line((22, 4, 30, 9), fill=(200, 200, 210, 255), width=1)
        return im
    d.line((9, 23, 28, 4), fill=blade + (255,), width=3)
    d.line((10, 22, 27, 5), fill=(255, 255, 255, 160), width=1)
    d.line((5, 19, 13, 27), fill=guard + (255,), width=2)
    d.line((8, 24, 3, 29), fill=guard + (255,), width=3)
    if name == "raijin": d.line([(21, 11), (18, 14), (22, 14), (18, 18)], fill=(90, 60, 0, 255), width=1)
    if name == "devils_edge": d.line((9, 23, 15, 17), fill=(255, 60, 20, 255), width=1)
    return im

def model(name, handheld=True):
    json.dump({"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
               "textures": {"layer0": f"kurogane:item/{name}"}}, open(f"{MOD}/{name}.json", "w"))
    json.dump({"model": {"type": "minecraft:model", "model": f"kurogane:item/{name}"}}, open(f"{ITM}/{name}.json", "w"))

for race, col in RACES.items():
    for n in (1, 2, 3):
        name = f"{race}_{n}"
        ability_icon(col, n).save(f"{TEX}/{name}.png"); model(name, False)
for name, (b, g) in SWORDS.items():
    sword_icon(name, b, g).save(f"{TEX}/{name}.png"); model(name)

json.dump({"pack": {"description": "Kurogane SMP (1.21.11)", "min_format": 75, "max_format": 75}}, open(f"{ROOT}/pack.mcmeta", "w"))
shutil.make_archive(os.path.join(ROOT, "..", "KuroganePack"), "zip", ROOT)
print("pack built")
