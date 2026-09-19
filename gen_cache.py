"""Generates the cache's textures, block model, blockstate and item definition.

Design: a dark steel casing like the terminal's, with bevelled corner posts. The front has a
recessed display window framed in the teal accent; the stored item and its count are drawn onto it
by a block entity renderer. Reuses the PNG helpers from gen_conduit.py. Pure stdlib; re-run after
changing anything here, the outputs are committed assets.
"""
import json
import os

import gen_conduit as gc

NS = gc.NS

STEEL = (74, 80, 90, 255)
STEEL_DK = (40, 44, 51, 255)
STEEL_LT = (100, 107, 118, 255)
POST = (58, 63, 72, 255)
WINDOW = (18, 21, 26, 255)
WINDOW_EDGE = (28, 32, 38, 255)
ACCENT = (44, 184, 192, 255)
ACCENT_DIM = (26, 110, 116, 255)


def casing():
    """Plain steel with darker corner posts and a light bevel on the upper-left edges."""
    px = gc.canvas(16, 16, STEEL)
    for i in range(16):
        px[0][i] = px[15][i] = px[i][0] = px[i][15] = STEEL_DK
    for i in range(1, 15):
        px[1][i] = px[i][1] = STEEL_LT
    for x0, y0 in ((1, 1), (12, 1), (1, 12), (12, 12)):
        for y in range(y0, y0 + 3):
            for x in range(x0, x0 + 3):
                px[y][x] = POST
    return px


def front():
    px = casing()
    # Accent frame around the display window (window 3..12, frame at 2 and 13).
    for i in range(2, 14):
        px[2][i] = px[13][i] = px[i][2] = px[i][13] = ACCENT_DIM
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = ACCENT
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = WINDOW
    for i in range(3, 13):
        px[3][i] = px[i][3] = WINDOW_EDGE  # inner shadow suggests depth
    return px


def side():
    px = casing()
    # Two horizontal grooves, like the terminal's sides.
    for x in range(4, 12):
        px[6][x] = px[9][x] = STEEL_DK
    return px


def top():
    px = casing()
    # A small accent strip marks the top so the block reads as a machine, not plain metal.
    for x in range(5, 11):
        px[7][x] = ACCENT_DIM
        px[8][x] = STEEL_DK
    return px


ROTATIONS = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}


def assets(name):
    gc.write_png(os.path.join(gc.TEX, f"{name}_front.png"), 16, 16, front())
    gc.write_png(os.path.join(gc.TEX, f"{name}_side.png"), 16, 16, side())
    gc.write_png(os.path.join(gc.TEX, f"{name}_top.png"), 16, 16, top())

    model = {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": f"{NS}:block/{name}_front",
            "side": f"{NS}:block/{name}_side",
            "top": f"{NS}:block/{name}_top",
            "particle": f"{NS}:block/{name}_side",
        },
    }
    with open(os.path.join(gc.MODELS, "block", f"{name}.json"), "w") as f:
        json.dump(model, f, indent=2)

    variants = {f"facing={facing}": {"model": f"{NS}:block/{name}", **rot} for facing, rot in ROTATIONS.items()}
    with open(os.path.join(gc.ROOT, "blockstates", f"{name}.json"), "w") as f:
        json.dump({"variants": variants}, f, indent=2)
    with open(os.path.join(gc.ROOT, "items", f"{name}.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}}, f, indent=2)


if __name__ == "__main__":
    assets("cache")
    print("done")
