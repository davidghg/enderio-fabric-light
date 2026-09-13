"""One-off generator for simple procedural 16x16 block textures (RGBA PNG).
Pure stdlib (zlib) — no PIL. Run once; the PNGs are the real assets, this script
is just tooling and can be deleted afterwards.
"""
import zlib, struct, os

W = H = 16
OUT = os.path.join("src", "main", "resources", "assets", "enderio-fabric-light", "textures", "block")


def png(path, px):
    raw = bytearray()
    for y in range(H):
        raw.append(0)  # filter: none
        for x in range(W):
            r, g, b, a = px[y][x]
            raw += bytes((r, g, b, a))
    comp = zlib.compress(bytes(raw), 9)

    def chunk(typ, data):
        return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff)

    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", comp)
            + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)
    print("wrote", path)


def grid(fill):
    return [[tuple(fill) for _ in range(W)] for _ in range(H)]


def edge(px, color):
    for i in range(W):
        px[0][i] = color
        px[H - 1][i] = color
        px[i][0] = color
        px[i][W - 1] = color


def rect(px, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[y][x] = color


# --- Conduit: dark steel pipe with a teal node in the centre ---------------
def conduit():
    base = (62, 68, 78, 255)
    dark = (34, 38, 45, 255)
    light = (86, 93, 104, 255)
    teal = (40, 168, 176, 255)
    teal_dk = (24, 110, 118, 255)
    px = grid(base)
    edge(px, dark)
    # subtle highlight along top + left, inside the border
    for i in range(1, W - 1):
        px[1][i] = light
        px[i][1] = light
    # centre node
    rect(px, 6, 6, 10, 10, teal_dk)
    rect(px, 7, 7, 9, 9, teal)
    png(os.path.join(OUT, "conduit.png"), px)


# --- Terminal front: a glowing screen in a metal bezel ---------------------
def terminal_front():
    bezel = (58, 63, 72, 255)
    bezel_dk = (32, 35, 41, 255)
    bezel_lt = (84, 90, 100, 255)
    screen_bg = (16, 38, 44, 255)
    screen_lit = (44, 184, 192, 255)
    screen_dim = (28, 96, 104, 255)
    px = grid(bezel)
    edge(px, bezel_dk)
    # bezel highlight
    for i in range(1, W - 1):
        px[1][i] = bezel_lt
        px[i][1] = bezel_lt
    # screen area 3..13
    rect(px, 3, 3, 13, 13, screen_bg)
    # a few "display" rows to suggest a list of items
    for y in (4, 6, 8, 10):
        rect(px, 4, y, 12, y + 1, screen_dim)
    # a couple of brighter cells
    rect(px, 4, 4, 6, 5, screen_lit)
    rect(px, 8, 6, 11, 7, screen_lit)
    rect(px, 4, 8, 7, 9, screen_lit)
    png(os.path.join(OUT, "terminal_front.png"), px)


# --- Terminal side: plain metal casing with two grooves --------------------
def terminal_side():
    base = (74, 80, 90, 255)
    dark = (40, 44, 51, 255)
    light = (100, 107, 118, 255)
    groove = (52, 57, 65, 255)
    px = grid(base)
    edge(px, dark)
    for i in range(1, W - 1):
        px[1][i] = light
        px[i][1] = light
    rect(px, 2, 5, 14, 6, groove)
    rect(px, 2, 10, 14, 11, groove)
    png(os.path.join(OUT, "terminal_side.png"), px)


# --- Terminal top: metal plate with a central hatch ------------------------
def terminal_top():
    base = (74, 80, 90, 255)
    dark = (40, 44, 51, 255)
    light = (100, 107, 118, 255)
    hatch = (54, 59, 67, 255)
    px = grid(base)
    edge(px, dark)
    for i in range(1, W - 1):
        px[1][i] = light
        px[i][1] = light
    rect(px, 5, 5, 11, 11, hatch)
    edge_inner = (40, 44, 51, 255)
    for x in range(5, 11):
        px[5][x] = edge_inner
        px[10][x] = edge_inner
    for y in range(5, 11):
        px[y][5] = edge_inner
        px[y][10] = edge_inner
    png(os.path.join(OUT, "terminal_top.png"), px)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    conduit()
    terminal_front()
    terminal_side()
    terminal_top()
    print("done")
