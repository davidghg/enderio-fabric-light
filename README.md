# EnderIO Fabric light

A small Fabric mod for Minecraft **26.1.2** inspired by Ender IO's Inventory Panel: a terminal
panel that shows the contents of all connected chests in one searchable list, lets you take and
store items, and crafts straight from storage. Chests are linked with thin conduits.

This is an independent implementation — it contains no Ender IO code.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.2 or newer
- Fabric API
- Java 25

Install the jar on the server **and** on every client.

## Blocks and items

### Terminal

A thin panel you can mount on walls, floors and ceilings. It connects through its **back**: put a
storage block or a conduit directly behind it. It needs a solid block, a conduit or a storage
block to hang on and pops off otherwise.

| Action | Result |
| --- | --- |
| Left-click an item | take a stack (then click or drag it into the crafting grid) |
| Right-click an item | take half a stack |
| Shift + click an item | take a stack into your inventory |
| Click the grid while holding items | store them (left: stack, right: one) |
| Drop items into the return area | stored automatically |
| Craft in the 3×3 grid | used ingredients are refilled from storage; shift-click the result to craft repeatedly |
| × under the result slot | move the crafting grid back into storage |
| Sort button (AZ / #) | sort by name or amount |

### Conduit

Connects the terminal to storage. Conduits link to each other and to chests, trapped chests,
copper chests, barrels and shulker boxes. The list of storage blocks is the block tag
`enderio-fabric-light:terminal_storage`, so a datapack can extend it.

### Conduit Wrench

- Right-click a conduit arm: switch that connection off or on (splits networks).
- Sneak + right-click a conduit or terminal: pick it up.

## Recipes

```
Terminal            Conduit (×6)        Conduit Wrench
I I I               I G I               I . I
G E G                                   . C .
I R I                                   . I .

I = iron ingot   G = glass   E = ender pearl   R = redstone   C = copper ingot
```

## Upgrading from 1.0.x

Conduit block states changed. Existing conduits show no connections until a neighbouring block
changes — break and replace one conduit per line to refresh them.

## Development

```
./gradlew runClient     # start a dev client
./gradlew build         # jar in build/libs/
python gen_conduit.py   # regenerate conduit models and textures
python gen_textures.py  # regenerate terminal and wrench textures
```

## License

CC0-1.0
