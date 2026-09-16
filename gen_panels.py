"""Generates textures, block models, blockstates and item definitions for the import/export panels.

Design: the terminal's 3px panel with a coloured accent ring on the front and a conduit socket in
the middle (a bevelled graphite flange with a glowing ring). When a conduit is docked in front, a
glass tube with a glowing core bridges the 12px gap from the socket to the conduit's own flange.

Reuses the geometry helpers and dimensions from gen_conduit.py so socket and tube match the conduit
exactly. Pure stdlib. Re-run after changing anything here; the outputs are committed assets.
"""
import json
import os

import gen_conduit as gc

NS = gc.NS
PANEL_DEPTH = 3                      # must match PanelBlock.THICKNESS
SOCKET_FACE = 16 - PANEL_DEPTH       # z of the panel's front face (model faces north)
SOCKET_DEPTH = 1

PANELS = {
    # name: (accent lit, accent dim)
    "import_panel": ((92, 224, 120, 255), (38, 110, 58, 255)),
}


# --- Textures ----------------------------------------------------------------

def front_texture(name, lit, dim):
    bezel = (58, 63, 72, 255)
    bezel_dk = (32, 35, 41, 255)
    bezel_lt = (84, 90, 100, 255)
    plate = (34, 38, 45, 255)
    px = gc.canvas(16, 16, bezel)
    for i in range(16):
        px[0][i] = px[15][i] = px[i][0] = px[i][15] = bezel_dk
    for i in range(1, 15):
        px[1][i] = px[i][1] = bezel_lt
    for y in range(2, 14):
        for x in range(2, 14):
            px[y][x] = plate
    # Accent ring around the socket (the socket flange covers 4..11).
    for i in range(3, 13):
        px[3][i] = px[12][i] = px[i][3] = px[i][12] = dim
    # Brighter corners and edge midpoints.
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[y][x] = lit
    for i in (7, 8):
        px[3][i] = px[12][i] = px[i][3] = px[i][12] = lit
    gc.write_png(os.path.join(gc.TEX, f"{name}_front.png"), 16, 16, px)


# --- Models ------------------------------------------------------------------

def write_model(rel, textures, elements):
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    path = os.path.join(gc.MODELS, *rel.split("/")) + ".json"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(model, f, indent=2)


def panel_element():
    t = PANEL_DEPTH
    return {
        "from": [0, 0, 16 - t],
        "to": [16, 16, 16],
        "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south"},
            "east": {"uv": [0, 0, t, 16], "texture": "#side"},
            "west": {"uv": [16 - t, 0, 16, 16], "texture": "#side"},
            "up": {"uv": [0, 16 - t, 16, 16], "texture": "#side"},
            "down": {"uv": [0, 0, 16, t], "texture": "#side"},
        },
    }


def socket_elements():
    frm, to = gc.box("north", gc.FLANGE, (SOCKET_FACE - SOCKET_DEPTH, SOCKET_FACE))
    pieces = gc.bevelled(frm, to, gc.FLANGE_BEVEL, [0, 1], "#frame")
    z = SOCKET_FACE - SOCKET_DEPTH - gc.GLOW_OFFSET
    g_frm, g_to = [gc.GLASS[0] - 1, gc.GLASS[0] - 1, z], [gc.GLASS[1] + 1, gc.GLASS[1] + 1, z]
    glow = gc.element(g_frm, g_to, [gc.face("north", g_frm, g_to, "#glow")], emission=15)
    return pieces + [glow]


def tube_elements():
    reach = SOCKET_FACE - SOCKET_DEPTH
    return [
        gc.tube("north", gc.GLASS, reach, "#glass"),
        gc.tube("north", gc.ENERGY, reach, "#energy", emission=15),
    ]


def models(name):
    base_textures = {
        "front": f"{NS}:block/{name}_front",
        "side": f"{NS}:block/terminal_side",
        "frame": gc.TEXTURES["frame"],
        "glow": gc.TEXTURES["glow"],
        "particle": f"{NS}:block/terminal_side",
    }
    write_model(f"block/{name}", base_textures, [panel_element(), *socket_elements()])
    tube_textures = {k: gc.TEXTURES[k] for k in ("glass", "energy", "particle")}
    write_model(f"block/{name}_tube", tube_textures, tube_elements())


# Rotations turning the north-facing model toward each facing (same as the terminal).
ROTATIONS = {
    "north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
    "up": {"x": 270}, "down": {"x": 90},
}


def blockstate(name):
    parts = []
    for facing, rot in ROTATIONS.items():
        parts.append({"when": {"facing": facing}, "apply": {"model": f"{NS}:block/{name}", **rot}})
        parts.append({"when": {"facing": facing, "connected": "true"},
                      "apply": {"model": f"{NS}:block/{name}_tube", **rot}})
    with open(os.path.join(gc.ROOT, "blockstates", f"{name}.json"), "w") as f:
        json.dump({"multipart": parts}, f, indent=2)

    with open(os.path.join(gc.ROOT, "items", f"{name}.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}}, f, indent=2)


if __name__ == "__main__":
    for panel, (lit, dim) in PANELS.items():
        front_texture(panel, lit, dim)
        models(panel)
        blockstate(panel)
    print("done")
