"""Generates the conduit's textures, block models, blockstate and item model.

Design: a thin translucent glass tube (4px) around a softly glowing core (2px). Bends, junctions
and ends get a bevelled graphite hub; pipe-to-pipe joints a slim bevelled sleeve; storage and
terminal connections a rounded flange with a glowing ring hugging the tube.

Models only allow boxes, so "rounded" shapes are built from non-overlapping boxes stepped inward
at the edges. Overlapping boxes would put coplanar faces on top of each other and flicker.

Pure stdlib. Re-run after changing anything here; the outputs are committed assets.
Hitbox sizes in ConduitBlock.java must match the dimensions below.
"""
import json
import os
import random
import struct
import zlib

NS = "enderio-fabric-light"
ROOT = os.path.join("src", "main", "resources", "assets", NS)
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models")

# Dimensions in pixels.
GLASS = (6, 10)          # tube cross-section
ENERGY = (7, 9)          # glowing core cross-section
HUB = (5, 11)            # junction hub
HUB_BEVEL = 0.5
SLEEVE = (5.5, 10.5)     # pipe-to-pipe sleeve cross-section
SLEEVE_DEPTH = 0.75      # per block; two neighbours form a 1.5px sleeve
SLEEVE_BEVEL = 0.5
FLANGE = (4, 12)         # connector flange cross-section
FLANGE_DEPTH = 1
FLANGE_BEVEL = 1
GLOW_OFFSET = 0.1        # glow ring sits this far in front of the flange face

DIRS = ["north", "south", "west", "east", "down", "up"]
AXIS = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}
NEGATIVE = {"north": True, "south": False, "west": True, "east": False, "down": True, "up": False}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west", "down": "up", "up": "down"}
FACE_AXIS = AXIS
# Natural texture axes per face: (u axis, v axis).
FACE_UV_AXES = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


# --- PNG ---------------------------------------------------------------------

def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(px[y][x])

    def chunk(typ, data):
        return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)


def canvas(w, h, color):
    return [[tuple(color) for _ in range(w)] for _ in range(h)]


def scale(color, k):
    return tuple(min(255, int(c * k)) for c in color[:3]) + (color[3],)


def textures():
    rnd = random.Random(26_1_2)

    # Glass: faint teal tint with a soft sheen along one edge.
    px = canvas(16, 16, (70, 160, 170, 56))
    for i in range(16):
        px[GLASS[0]][i] = (170, 230, 235, 105)
        px[GLASS[1] - 1][i] = (40, 100, 110, 80)
    write_png(os.path.join(TEX, "conduit_glass.png"), 16, 16, px)

    # Core: calm glow that slowly breathes between two brightness levels (interpolated).
    core = (80, 214, 220, 255)
    px = canvas(16, 32, core)
    for frame, k in ((0, 1.0), (1, 0.78)):
        for v in range(16):
            for u in range(16):
                shade = 1.12 if v % 2 == 0 else 0.9  # lighter upper row gives the 2px core some volume
                px[frame * 16 + v][u] = scale(core, k * shade)
    write_png(os.path.join(TEX, "conduit_energy.png"), 16, 32, px)
    with open(os.path.join(TEX, "conduit_energy.png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": 60, "interpolate": True}}, f, indent=2)

    # Graphite: smooth dark metal, very light grain.
    px = canvas(16, 16, (44, 48, 55, 255))
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            if r < 0.12:
                px[y][x] = (48, 52, 60, 255)
            elif r < 0.20:
                px[y][x] = (41, 44, 51, 255)
    write_png(os.path.join(TEX, "conduit_frame.png"), 16, 16, px)

    # Flange glow ring: a rounded ring just outside the tube (cutout overlay).
    px = canvas(16, 16, (0, 0, 0, 0))
    ring = (90, 236, 240, 255)
    lo, hi = GLASS[0] - 1, GLASS[1]          # 5 .. 10
    for i in range(lo + 1, hi):
        px[lo][i] = px[hi][i] = ring          # top/bottom rows, corners left open
        px[i][lo] = px[i][hi] = ring          # left/right columns
    write_png(os.path.join(TEX, "conduit_plug_glow.png"), 16, 16, px)

    for stale in ("conduit_accent.png", "conduit_plug.png"):
        path = os.path.join(TEX, stale)
        if os.path.exists(path):
            os.remove(path)


# --- Geometry helpers --------------------------------------------------------

def span(direction, d0, d1):
    """Range along the direction's axis, measured inward from that block face."""
    return (d0, d1) if NEGATIVE[direction] else (16 - d1, 16 - d0)


def box(direction, cross, depth):
    """from/to for a box with the given cross-section, extending `depth` inward from `direction`'s face."""
    a = AXIS[direction]
    lo, hi = span(direction, *depth)
    frm, to = [cross[0]] * 3, [cross[1]] * 3
    frm[a], to[a] = lo, hi
    return frm, to


def face(name, frm, to, texture, length_axis=None, cull=None):
    u_axis, v_axis = FACE_UV_AXES[name]
    f = {"texture": texture}
    if length_axis is not None and length_axis in (u_axis, v_axis):
        cross_axis = v_axis if length_axis == u_axis else u_axis
        length = to[length_axis] - frm[length_axis]
        f["uv"] = [0, frm[cross_axis], length, to[cross_axis]]
        if length_axis == v_axis:
            f["rotation"] = 90
    else:
        f["uv"] = [frm[u_axis], frm[v_axis], to[u_axis], to[v_axis]]
    if cull:
        f["cullface"] = cull
    return name, f


def element(frm, to, faces, emission=None):
    e = {"from": list(frm), "to": list(to), "faces": dict(faces)}
    if emission:
        e["shade"] = False
        e["light_emission"] = emission
    return e


def side_faces(axis):
    return [n for n in DIRS if FACE_AXIS[n] != axis]


def tube(direction, cross, reach, texture, emission=None):
    frm, to = box(direction, cross, (0, reach))
    a = AXIS[direction]
    return element(frm, to, [face(n, frm, to, texture, length_axis=a) for n in side_faces(a)], emission)


def cap(direction, cross, texture, emission=None):
    """Zero-thickness square closing the tube on an unconnected side."""
    frm, to = box(direction, cross, (cross[0], cross[0]))
    return element(frm, to, [face(direction, frm, to, texture)], emission)


def bevelled(frm, to, bevel, axes, texture):
    """
    A box whose edges are stepped inward by `bevel` on the given axes, as non-overlapping pieces:
    a core shrunk on those axes plus one slab per bevelled side.
    """
    core_frm, core_to = list(frm), list(to)
    for a in axes:
        core_frm[a] += bevel
        core_to[a] -= bevel
    pieces = [(core_frm, core_to)]
    for a in axes:
        others = [i for i in axes if i != a]
        for side in (0, 1):
            s_frm, s_to = list(frm), list(to)
            for o in others:
                s_frm[o], s_to[o] = core_frm[o], core_to[o]
            if side == 0:
                s_to[a] = core_frm[a]
            else:
                s_frm[a] = core_to[a]
            pieces.append((s_frm, s_to))
    return [element(f, t, [face(n, f, t, texture) for n in DIRS]) for f, t in pieces]


def cross_axes(direction):
    return [i for i in range(3) if i != AXIS[direction]]


def sleeve(direction):
    frm, to = box(direction, SLEEVE, (0, SLEEVE_DEPTH))
    return bevelled(frm, to, SLEEVE_BEVEL, cross_axes(direction), "#frame")


def flange(direction):
    frm, to = box(direction, FLANGE, (0, FLANGE_DEPTH))
    pieces = bevelled(frm, to, FLANGE_BEVEL, cross_axes(direction), "#frame")
    d = FLANGE_DEPTH + GLOW_OFFSET
    g_frm, g_to = box(direction, (GLASS[0] - 1, GLASS[1] + 1), (d, d))
    glow = element(g_frm, g_to, [face(OPPOSITE[direction], g_frm, g_to, "#glow")], emission=15)
    return pieces + [glow]


def hub():
    return bevelled([HUB[0]] * 3, [HUB[1]] * 3, HUB_BEVEL, [0, 1, 2], "#frame")


TEXTURES = {
    "glass": f"{NS}:block/conduit_glass",
    "energy": f"{NS}:block/conduit_energy",
    "frame": f"{NS}:block/conduit_frame",
    "glow": f"{NS}:block/conduit_plug_glow",
    "particle": f"{NS}:block/conduit_frame",
}


def write_model(rel, elements, extra=None):
    model = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": TEXTURES, "elements": elements}
    if extra:
        model.update(extra)
    path = os.path.join(MODELS, *rel.split("/")) + ".json"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(model, f, indent=2)


def models():
    for d in DIRS:
        write_model(f"block/conduit/none_{d}", [
            cap(d, GLASS, "#glass"),
            cap(d, ENERGY, "#energy", emission=15),
        ])
        write_model(f"block/conduit/pipe_{d}", [
            tube(d, GLASS, GLASS[0], "#glass"),
            tube(d, ENERGY, ENERGY[0], "#energy", emission=15),
            *sleeve(d),
        ])
        write_model(f"block/conduit/plug_{d}", [
            tube(d, GLASS, GLASS[0], "#glass"),
            tube(d, ENERGY, ENERGY[0], "#energy", emission=15),
            *flange(d),
        ])
    write_model("block/conduit/hub", hub())

    stale = os.path.join(MODELS, "block", "conduit", "cage.json")
    if os.path.exists(stale):
        os.remove(stale)

    # Item: a straight segment along X with sleeves at both ends.
    glass_frm, glass_to = [0, GLASS[0], GLASS[0]], [16, GLASS[1], GLASS[1]]
    energy_frm, energy_to = [0, ENERGY[0], ENERGY[0]], [16, ENERGY[1], ENERGY[1]]
    item = [
        element(glass_frm, glass_to, [face(n, glass_frm, glass_to, "#glass", length_axis=0) for n in DIRS]),
        element(energy_frm, energy_to, [face(n, energy_frm, energy_to, "#energy", length_axis=0) for n in DIRS], emission=15),
        *sleeve("west"),
        *sleeve("east"),
    ]
    write_model("item/conduit", item, {"display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    }})


def blockstate():
    connected = "pipe|plug"
    hub_when = []
    axes = [("north", "south"), ("west", "east"), ("down", "up")]
    # Bends and junctions: connections on two different axes.
    for i in range(3):
        for j in range(i + 1, 3):
            for a in axes[i]:
                for b in axes[j]:
                    hub_when.append({a: connected, b: connected})
    # Dead ends and lone conduits.
    hub_when.append({d: "none" for d in DIRS})
    for d in DIRS:
        hub_when.append({x: (connected if x == d else "none") for x in DIRS})

    parts = [{"when": {"OR": hub_when}, "apply": {"model": f"{NS}:block/conduit/hub"}}]
    for d in DIRS:
        for kind in ("none", "pipe", "plug"):
            parts.append({"when": {d: kind}, "apply": {"model": f"{NS}:block/conduit/{kind}_{d}"}})
    with open(os.path.join(ROOT, "blockstates", "conduit.json"), "w") as f:
        json.dump({"multipart": parts}, f, indent=2)

    with open(os.path.join(ROOT, "items", "conduit.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/conduit"}}, f, indent=2)


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    textures()
    models()
    blockstate()
    print("done")
