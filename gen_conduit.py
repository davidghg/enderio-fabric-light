"""Generates the conduit's textures, block models, blockstate and item model.

Design: a thin translucent glass tube (4px) with an animated, light-emitting energy core (2px).
Couplings ring the tube where two conduits meet, a cage with glowing corners marks bends and
junctions, and storage/terminal connections end in a connector plate with a glowing ring.

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
GLASS = (6, 10)      # tube cross-section
ENERGY = (7, 9)      # glowing core cross-section
CAGE = (5, 11)       # junction cage
PLATE = (3, 13)      # connector plate
PLATE_DEPTH = 1.5
GLOW_RING = (4, 12)
FLANGE_DEPTH = (PLATE_DEPTH, 3)

DIRS = ["north", "south", "west", "east", "down", "up"]
AXIS = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}
NEGATIVE = {"north": True, "south": False, "west": True, "east": False, "down": True, "up": False}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west", "down": "up", "up": "down"}
FACE_AXIS = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}
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


def textures():
    rnd = random.Random(26_1_2)

    # Glass: faint teal tint, a bright sheen line along one edge and a darker one opposite.
    px = canvas(16, 16, (70, 160, 170, 60))
    for i in range(16):
        px[GLASS[0]][i] = (190, 240, 245, 130)
        px[GLASS[1] - 1][i] = (30, 90, 100, 90)
    write_png(os.path.join(TEX, "conduit_glass.png"), 16, 16, px)

    # Energy core: pulses travelling along u. 8 frames, each shifted by one pixel.
    frames = 8
    base, mid, peak = (20, 120, 130, 255), (110, 230, 232, 255), (225, 255, 252, 255)
    px = canvas(16, 16 * frames, base)
    for f in range(frames):
        for v in range(16):
            for u in range(16):
                phase = (u - f) % 8
                c = peak if phase == 0 else mid if phase in (1, 7) else base
                if v % 2 == 1:  # slight shading on alternate rows gives the 2px core some depth
                    c = tuple(max(0, int(ch * 0.82)) for ch in c[:3]) + (255,)
                px[f * 16 + v][u] = c
    write_png(os.path.join(TEX, "conduit_energy.png"), 16, 16 * frames, px)
    with open(os.path.join(TEX, "conduit_energy.png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": 3, "interpolate": False}}, f, indent=2)

    # Frame: dark gunmetal with a little noise.
    px = canvas(16, 16, (50, 55, 64, 255))
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            if r < 0.18:
                px[y][x] = (60, 66, 76, 255)
            elif r < 0.30:
                px[y][x] = (42, 46, 54, 255)
    write_png(os.path.join(TEX, "conduit_frame.png"), 16, 16, px)

    # Accent: glowing teal.
    px = canvas(16, 16, (60, 215, 220, 255))
    write_png(os.path.join(TEX, "conduit_accent.png"), 16, 16, px)

    # Connector plate: bevelled metal with a dark recess where the tube enters.
    px = canvas(16, 16, (46, 50, 58, 255))  # edge strips live at 13..15
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = (58, 63, 72, 255)
    for i in range(3, 13):
        px[3][i] = px[i][3] = (84, 91, 102, 255)
        px[12][i] = px[i][12] = (28, 31, 36, 255)
    for y in range(5, 11):
        for x in range(5, 11):
            px[y][x] = (36, 40, 46, 255)
    write_png(os.path.join(TEX, "conduit_plug.png"), 16, 16, px)

    # Plate glow ring: cutout overlay.
    px = canvas(16, 16, (0, 0, 0, 0))
    lo, hi = GLOW_RING[0], GLOW_RING[1] - 1
    for i in range(lo, hi + 1):
        for (x, y) in ((i, lo), (i, hi), (lo, i), (hi, i)):
            px[y][x] = (80, 235, 238, 255)
    write_png(os.path.join(TEX, "conduit_plug_glow.png"), 16, 16, px)


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
    return [n for n in ("north", "south", "west", "east", "down", "up") if FACE_AXIS[n] != axis]


def tube(direction, cross, reach, texture, emission=None):
    frm, to = box(direction, cross, (0, reach))
    a = AXIS[direction]
    return element(frm, to, [face(n, frm, to, texture, length_axis=a) for n in side_faces(a)], emission)


def cap(direction, cross, texture, emission=None):
    """Zero-thickness square closing the core on an unconnected side."""
    frm, to = box(direction, cross, (cross[0], cross[0]))
    return element(frm, to, [face(direction, frm, to, texture)], emission)


def bars_ring(direction, depth, outer, inner, texture):
    """Four bars forming a square ring around the tube."""
    a = AXIS[direction]
    p, q = [i for i in range(3) if i != a]
    lo, hi = span(direction, *depth)
    out = []
    for (p0, p1, q0, q1) in ((outer[0], outer[1], outer[0], inner[0]),
                             (outer[0], outer[1], inner[1], outer[1]),
                             (outer[0], inner[0], inner[0], inner[1]),
                             (inner[1], outer[1], inner[0], inner[1])):
        frm, to = [0, 0, 0], [0, 0, 0]
        frm[a], to[a] = lo, hi
        frm[p], to[p] = p0, p1
        frm[q], to[q] = q0, q1
        out.append(element(frm, to, [face(n, frm, to, texture) for n in DIRS]))
    return out


def plate(direction):
    frm, to = box(direction, PLATE, (0, PLATE_DEPTH))
    a = AXIS[direction]
    faces = [face(OPPOSITE[direction], frm, to, "#plug"),
             face(direction, frm, to, "#plug", cull=direction)]
    for n in side_faces(a):
        # Columns 13..15 of the plate texture are a uniform edge colour, so orientation doesn't matter.
        faces.append((n, {"texture": "#plug", "uv": [13, 0, 16, 16]}))
    glow_frm, glow_to = box(direction, GLOW_RING, (PLATE_DEPTH + 0.1, PLATE_DEPTH + 0.1))
    glow = element(glow_frm, glow_to, [face(OPPOSITE[direction], glow_frm, glow_to, "#glow")], emission=15)
    return [element(frm, to, faces), glow]


def cage():
    lo, hi = CAGE
    out = []
    # Edges: 12 bars between the corners.
    for a in range(3):
        p, q = [i for i in range(3) if i != a]
        for pv in (lo, hi - 1):
            for qv in (lo, hi - 1):
                frm, to = [0, 0, 0], [0, 0, 0]
                frm[a], to[a] = lo + 1, hi - 1
                frm[p], to[p] = pv, pv + 1
                frm[q], to[q] = qv, qv + 1
                out.append(element(frm, to, [face(n, frm, to, "#frame") for n in DIRS]))
    # Corners: 8 glowing studs.
    for x in (lo, hi - 1):
        for y in (lo, hi - 1):
            for z in (lo, hi - 1):
                frm, to = [x, y, z], [x + 1, y + 1, z + 1]
                out.append(element(frm, to, [face(n, frm, to, "#accent") for n in DIRS], emission=12))
    return out


TEXTURES = {
    "glass": f"{NS}:block/conduit_glass",
    "energy": f"{NS}:block/conduit_energy",
    "frame": f"{NS}:block/conduit_frame",
    "accent": f"{NS}:block/conduit_accent",
    "plug": f"{NS}:block/conduit_plug",
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
            *bars_ring(d, (0, 1), CAGE, GLASS, "#frame"),
        ])
        write_model(f"block/conduit/plug_{d}", [
            tube(d, GLASS, GLASS[0], "#glass"),
            tube(d, ENERGY, ENERGY[0], "#energy", emission=15),
            *plate(d),
            *bars_ring(d, FLANGE_DEPTH, CAGE, GLASS, "#frame"),
        ])
    write_model("block/conduit/cage", cage())

    # Item: a straight segment along X with couplings at both ends and a cage in the middle.
    glass_frm, glass_to = [0, GLASS[0], GLASS[0]], [16, GLASS[1], GLASS[1]]
    energy_frm, energy_to = [0, ENERGY[0], ENERGY[0]], [16, ENERGY[1], ENERGY[1]]
    item = [
        element(glass_frm, glass_to, [face(n, glass_frm, glass_to, "#glass", length_axis=0) for n in DIRS]),
        element(energy_frm, energy_to, [face(n, energy_frm, energy_to, "#energy", length_axis=0) for n in DIRS], emission=15),
        *bars_ring("west", (0, 1), CAGE, GLASS, "#frame"),
        *bars_ring("east", (0, 1), CAGE, GLASS, "#frame"),
        *cage(),
    ]
    write_model("item/conduit", item, {"display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    }})


def blockstate():
    connected = "pipe|plug"
    cage_when = []
    axes = [("north", "south"), ("west", "east"), ("down", "up")]
    # Bends and junctions: connections on two different axes.
    for i in range(3):
        for j in range(i + 1, 3):
            for a in axes[i]:
                for b in axes[j]:
                    cage_when.append({a: connected, b: connected})
    # Dead ends and lone conduits.
    cage_when.append({d: "none" for d in DIRS})
    for d in DIRS:
        cage_when.append({x: (connected if x == d else "none") for x in DIRS})

    parts = [{"when": {"OR": cage_when}, "apply": {"model": f"{NS}:block/conduit/cage"}}]
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
