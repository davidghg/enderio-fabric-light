"""Generates the cache's textures, block model, blockstate and item definition.

Design: a dark steel casing like the terminal's, with bevelled corner posts. The front has a
recessed display window framed in the teal accent; the stored item and its count are drawn onto it
by a block entity renderer. The hardened cache swaps the steel for obsidian plates in a riveted
netherite frame with a violet accent, so the two tiers are easy to tell apart. Reuses the PNG helpers from gen_conduit.py. Pure stdlib; re-run after
changing anything here, the outputs are committed assets.
"""
import json
import os

import gen_conduit as gc

NS = gc.NS

# The plain cache: dark steel with a teal accent.
STEEL = (74, 80, 90, 255)
STEEL_DK = (40, 44, 51, 255)
STEEL_LT = (100, 107, 118, 255)
POST = (58, 63, 72, 255)
WINDOW = (18, 21, 26, 255)
WINDOW_EDGE = (28, 32, 38, 255)
ACCENT = (44, 184, 192, 255)
ACCENT_DIM = (26, 110, 116, 255)

# The hardened cache: speckled obsidian plates in a netherite frame with rivets, violet accent.
OBSIDIAN = (24, 17, 36, 255)
OBSIDIAN_SPECK = ((44, 28, 66, 255), (62, 40, 92, 255), (16, 11, 24, 255))
NETHERITE = (66, 58, 60, 255)
NETHERITE_DK = (34, 30, 32, 255)
NETHERITE_LT = (98, 88, 90, 255)
RIVET = (150, 138, 132, 255)
H_ACCENT = (186, 110, 250, 255)
H_ACCENT_DIM = (108, 58, 156, 255)

# Fixed speckle pattern, so re-running the script gives identical PNGs.
SPECKLES = [(3, 5, 0), (6, 3, 1), (9, 6, 0), (12, 4, 2), (4, 9, 1), (7, 11, 0), (11, 10, 1),
            (5, 13, 2), (10, 12, 0), (13, 7, 1), (8, 8, 2), (3, 11, 0), (12, 13, 1), (6, 6, 0)]


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


def hardened_casing():
    """Obsidian plate inside a 2px netherite frame, with a rivet on each corner."""
    px = gc.canvas(16, 16, OBSIDIAN)
    for x, y, c in SPECKLES:
        px[y][x] = OBSIDIAN_SPECK[c]
    for i in range(16):
        for j in (0, 15):
            px[j][i] = px[i][j] = NETHERITE_DK
        for j in (1, 14):
            if 0 < i < 15:
                px[j][i] = px[i][j] = NETHERITE
    for i in range(1, 15):
        px[1][i] = px[i][1] = NETHERITE_LT
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = NETHERITE
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[y][x] = RIVET
    return px


def front(hardened=False):
    px = hardened_casing() if hardened else casing()
    accent, accent_dim = (H_ACCENT, H_ACCENT_DIM) if hardened else (ACCENT, ACCENT_DIM)
    # Accent frame around the display window (window 3..12, frame at 2 and 13).
    for i in range(2, 14):
        px[2][i] = px[13][i] = px[i][2] = px[i][13] = accent_dim
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = accent
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = WINDOW
    for i in range(3, 13):
        px[3][i] = px[i][3] = WINDOW_EDGE  # inner shadow suggests depth
    return px


def side(hardened=False):
    if hardened:
        # Netherite cross-bracing over the obsidian plate.
        px = hardened_casing()
        for i in range(3, 13):
            px[i][i] = px[i][15 - i] = NETHERITE
        px[7][7] = px[7][8] = px[8][7] = px[8][8] = RIVET
        return px
    px = casing()
    # Two horizontal grooves, like the terminal's sides.
    for x in range(4, 12):
        px[6][x] = px[9][x] = STEEL_DK
    return px


def top(hardened=False):
    px = hardened_casing() if hardened else casing()
    # A small accent strip marks the top so the block reads as a machine, not plain metal.
    accent_dim = H_ACCENT_DIM if hardened else ACCENT_DIM
    for x in range(5, 11):
        px[7][x] = accent_dim
        px[8][x] = NETHERITE_DK if hardened else STEEL_DK
    if hardened:
        px[7][5] = px[7][10] = H_ACCENT
    return px


def hardening_kit():
    """Item: a netherite plate with an obsidian inlay and a violet gem, plus two rivets."""
    px = gc.canvas(16, 16, (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (3, 12)
            px[y][x] = NETHERITE_DK if edge else NETHERITE
    for i in range(3, 13):
        px[4][i] = NETHERITE_LT
    for y in range(6, 11):
        for x in range(5, 11):
            px[y][x] = OBSIDIAN
    px[7][6] = OBSIDIAN_SPECK[1]
    px[9][9] = OBSIDIAN_SPECK[0]
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[y][x] = H_ACCENT
    px[7][7] = (230, 190, 255, 255)
    px[6][3] = px[6][12] = px[10][3] = px[10][12] = RIVET
    return px


ROTATIONS = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}


def assets(name, hardened=False):
    gc.write_png(os.path.join(gc.TEX, f"{name}_front.png"), 16, 16, front(hardened))
    gc.write_png(os.path.join(gc.TEX, f"{name}_side.png"), 16, 16, side(hardened))
    gc.write_png(os.path.join(gc.TEX, f"{name}_top.png"), 16, 16, top(hardened))

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
    assets("hardened_cache", hardened=True)
    from gen_panels import write_item
    write_item("hardening_kit", hardening_kit())
    print("done")
