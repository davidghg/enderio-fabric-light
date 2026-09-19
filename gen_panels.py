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
    "export_panel": ((240, 150, 58, 255), (122, 74, 30, 255)),
    "storage_connector": ((96, 156, 255, 255), (40, 72, 134, 255)),
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


def upgrade_card():
    """Blank circuit card with gold contacts, shared by all upgrades."""
    outline = (18, 24, 22, 255)
    board = (30, 58, 48, 255)
    board_hi = (44, 80, 66, 255)
    gold = (214, 170, 70, 255)
    px = gc.canvas(16, 16, (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            px[y][x] = outline if edge else board
    for i in range(3, 13):
        px[3][i] = px[i][3] = board_hi
    for x in (4, 6, 8, 10):
        px[12][x] = gold
    return px


def write_item(name, px):
    out = os.path.join(os.path.dirname(gc.TEX), "item")
    os.makedirs(out, exist_ok=True)
    gc.write_png(os.path.join(out, f"{name}.png"), 16, 16, px)
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}
    with open(os.path.join(gc.MODELS, "item", f"{name}.json"), "w") as f:
        json.dump(model, f, indent=2)
    with open(os.path.join(gc.ROOT, "items", f"{name}.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, f, indent=2)


def upgrade_texture():
    """Transfer upgrade: the circuit card with a glowing double chevron."""
    lit = (80, 214, 220, 255)
    dim = (40, 120, 126, 255)
    px = upgrade_card()
    for x0 in (5, 8):
        for dy, dx in ((0, 0), (1, 1), (2, 2), (3, 1), (4, 0)):
            px[5 + dy][x0 + dx] = lit
            px[5 + dy][x0 + dx - 1] = dim if px[5 + dy][x0 + dx - 1] != lit else lit
    write_item("transfer_upgrade", px)


def crafting_upgrade_texture():
    """Crafting upgrade: the circuit card with a glowing 3×3 crafting grid."""
    lit = (190, 140, 250, 255)
    dim = (104, 70, 150, 255)
    px = upgrade_card()
    for gy in range(3):
        for gx in range(3):
            x, y = 5 + gx * 2, 4 + gy * 2
            px[y][x] = lit if (gx + gy) % 2 == 0 else dim
    write_item("crafting_upgrade", px)


# --- Crafting panel ------------------------------------------------------------

CRAFTING_LIT = (190, 140, 250, 255)
CRAFTING_DIM = (96, 64, 140, 255)


def crafting_front_texture():
    """Screen-like front as on the terminal, showing a violet 3×3 grid with an arrow and result."""
    bezel = (58, 63, 72, 255)
    bezel_dk = (32, 35, 41, 255)
    bezel_lt = (84, 90, 100, 255)
    screen = (26, 20, 38, 255)
    px = gc.canvas(16, 16, bezel)
    for i in range(16):
        px[0][i] = px[15][i] = px[i][0] = px[i][15] = bezel_dk
    for i in range(1, 15):
        px[1][i] = px[i][1] = bezel_lt
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = screen
    for gy in range(3):
        for gx in range(3):
            x, y = 4 + gx * 2, 5 + gy * 2
            px[y][x] = CRAFTING_DIM
    px[7][10] = CRAFTING_LIT                     # arrow
    for y in (6, 7, 8):
        px[y][11] = CRAFTING_LIT                 # result
    px[5][4] = px[7][6] = px[9][8] = CRAFTING_LIT
    gc.write_png(os.path.join(gc.TEX, "crafting_panel_front.png"), 16, 16, px)


def crafting_panel():
    """Terminal-shaped panel without a socket: it joins the network through its back."""
    crafting_front_texture()
    textures = {
        "front": f"{NS}:block/crafting_panel_front",
        "side": f"{NS}:block/terminal_side",
        "particle": f"{NS}:block/terminal_side",
    }
    write_model("block/crafting_panel", textures, [panel_element()])
    variants = {f"facing={facing}": {"model": f"{NS}:block/crafting_panel", **rot} for facing, rot in ROTATIONS.items()}
    with open(os.path.join(gc.ROOT, "blockstates", "crafting_panel.json"), "w") as f:
        json.dump({"variants": variants}, f, indent=2)
    with open(os.path.join(gc.ROOT, "items", "crafting_panel.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:block/crafting_panel"}}, f, indent=2)


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
    """Tube from the socket to the block edge, ending in a sleeve like a conduit-to-conduit joint
    (the conduit meets import/export panels with a pipe connection, not a connector plate)."""
    reach = SOCKET_FACE - SOCKET_DEPTH
    return [
        gc.tube("north", gc.GLASS, reach, "#glass"),
        gc.tube("north", gc.ENERGY, reach, "#energy", emission=15),
        *gc.sleeve("north"),
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
    tube_textures = {k: gc.TEXTURES[k] for k in ("glass", "energy", "frame", "particle")}
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
    upgrade_texture()
    crafting_upgrade_texture()
    crafting_panel()
    print("done")
