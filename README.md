# EnderIO Fabric light

A small Fabric mod for Minecraft **26.1.2** inspired by Ender IO's Inventory Panel: a terminal
panel that shows the contents of all connected chests in one searchable list, lets you take and
store items, and crafts straight from storage — or lets the system autocraft whole recipe chains.
Chests are linked with thin conduits, and import and export panels connect farms and other chests
to the system.

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
| Mode button (chest / crafting table) | switch between storage and autocrafting (needs a crafting panel) |

#### Autocrafting

In autocrafting mode the grid lists every item the network can craft, with the amount already in
storage. Click one to open the craft dialog:

- Set the amount with the −64 / −1 / +1 / +64 buttons, by typing, or with the mouse wheel over the
  field (Shift: ±16).
- The preview shows the ingredients taken from storage and, on a red ground, anything missing.
- **Craft** (or Enter) crafts instantly; the result goes into storage. **Cancel** (or Esc) closes the
  dialog.

The system uses every crafting-table recipe and crafts intermediates on its own (logs → planks →
sticks → torches). Where a recipe accepts several items, it uses what storage holds most of, e.g. the
most plentiful wood type. Damaged, enchanted, renamed or otherwise special items are never used up.
Leftovers such as empty buckets go back into storage.

### Conduit

Connects the terminal to storage. Conduits link to each other and to chests, trapped chests,
copper chests, barrels and shulker boxes. The list of storage blocks is the block tag
`enderio-fabric-light:terminal_storage`, so a datapack can extend it.

### Import Panel

Mount it on a storage block (for example a farm's output chest) and dock a conduit onto the socket
on its **front**. It pulls items out of that chest into the system — never the other way round.
Right-click to open its settings:

- **Filter** (3×3): click a slot with an item to add it, click with an empty hand to remove it.
  Nothing is consumed. The filter matches the item type only (damage and enchantments are ignored).
- **Blacklist** (default): import everything except the filtered items — an empty blacklist imports
  everything. **Whitelist**: import only the filtered items.
- **Upgrade slot**: up to 4 transfer upgrades.

### Export Panel

Mount it on a storage block and dock a conduit onto its front. It fills that chest with the filtered
items from the system. Same settings screen as the import panel, with two modes:

- **Keep in stock** (default): tops each filtered item up to its target amount. The amount is shown
  in the filter slot; change it with the mouse wheel (±1, with Shift ±16, up to 9999). A new filter
  entry starts at one stack.
- **Push all**: moves filtered items in while there is room.

An empty filter exports nothing. A chest with an import or export panel on it never counts as system
storage, even if a conduit touches it, so items can't loop back to where they came from.

### Transfer Upgrade

Speeds up import and export panels:

| Upgrades | Items per transfer | Interval | Items per second |
| --- | --- | --- | --- |
| 0 | 4 | 2 s | 2 |
| 1 | 16 | 1 s | 16 |
| 2 | 32 | 1 s | 32 |
| 3 | 64 | 1 s | 64 |
| 4 | 64 | 0.5 s | 128 |

### Crafting Panel

Enables autocrafting in every terminal on its network. Like the terminal, it connects through its
**back**: mount it on a conduit. The largest order depends on the crafting upgrades inside:

| Crafting upgrades | Largest order |
| --- | --- |
| 0 | 64 |
| 1 | 256 |
| 2 | 1,024 |
| 3 | 4,096 |
| 4 | 9,999 |

With several crafting panels on one network, the best one counts.

### Cache and Hardened Cache

Stores a huge amount of a single item type: **20,000** in a cache, **80,000** in a hardened cache.
The front shows the item, the count and a fill bar. Only plain items (no enchantments, names or
other data) fit.

- Right-click with an item: store the held stack. Double right-click: store every matching item
  from your inventory.
- Left-click: take a stack. Sneak + left-click: take a single item.
- Sneak + right-click with an empty hand: settings (priority, lock).
- **Lock:** a locked cache keeps its item type when it runs empty, so the network refills it.
- Breaking a cache keeps its contents, lock and priority in the item, like a shulker box.
- A conduit next to the cache connects it directly. The network only fills caches that already
  have an item type; it never picks a new type for an empty cache.

**Upgrade** a cache by crafting it with a **Hardening Kit**. Contents and settings carry over.

### Storage Connector

A panel for a chest, barrel or shulker box (or any side of a cache except its display). Dock a
conduit to its front, like an import panel. Unlike import/export panels, the storage stays ordinary
network storage. Right-click it to set the priority of the storage behind it; on a cache it opens
the cache's own settings.

### Priorities

Every storage has a priority from -99 to 99 (chests 0, caches 10 by default; set with the buttons
or the mouse wheel, Shift = steps of 10). Items go into the highest priority first, filling
existing stacks before starting new ones. Items are taken from the lowest priority first. So a
cache for iron blocks gets every iron block before any chest does.

### Conduit Wrench

- Right-click a conduit arm: switch that connection off or on (splits networks).
- Sneak + right-click a conduit or panel: pick it up.

## Recipes

```
Terminal            Conduit (×6)        Conduit Wrench
I I I               I G I               I . I
G E G                                   . C .
I R I                                   . I .

I = iron ingot   G = glass   E = ender pearl   R = redstone   C = copper ingot

Import Panel        Export Panel        Transfer Upgrade
D Y D               D Y D               D R D
B H B               B S B               R N R
I O I               I O I               D R D

Crafting Panel      Crafting Upgrade
D Y D               D R D
B W B               R N R
I O I               D W D

D = diamond   Y = eye of ender   B = block of redstone   H = hopper   S = sticky piston
I = iron ingot   O = conduit   R = redstone   N = netherite scrap   W = crafting table

Cache               Hardening Kit       Storage Connector
K D K               X D X               D Y D
B C B               N Y N               B C B
K D K               X D X               I O I

Hardened Cache = Cache + Hardening Kit (shapeless, keeps contents)

K = block of iron   C = chest   X = obsidian   N = netherite ingot
```

## Changing the recipes

`datapack/enderio-fabric-light-recipes/` is a datapack holding an editable copy of every recipe in
this mod. Copy that folder into `<world>/datapacks/` (single player or server), run `/reload`, and
your versions replace the built-in ones — no new build needed.

- Edit a file under `data/enderio-fabric-light/recipe/` to change what a block costs.
- Delete a file to fall back to the mod's default.
- To remove a recipe completely, list it (and its recipe-book advancement) in the `filter` block of
  `pack.mcmeta`.

`README.txt` inside the pack repeats this, and `python gen_datapack.py` regenerates the copies.

## Upgrading from 1.2.x

Conduits are thinner, and they now meet import and export panels with a pipe joint instead of a
connector plate. Conduits placed next to such a panel before 1.3.0 keep showing the plate until a
neighbouring block changes — toggle that side off and on with the wrench to refresh it.

## Upgrading from 1.0.x

Conduit block states changed. Existing conduits show no connections until a neighbouring block
changes — break and replace one conduit per line to refresh them.

## Development

```
./gradlew runClient     # start a dev client
./gradlew build         # jar in build/libs/
python gen_conduit.py   # regenerate conduit models and textures
python gen_textures.py  # regenerate terminal and wrench textures
python gen_panels.py    # regenerate panel and upgrade assets
python gen_cache.py     # regenerate cache, hardened cache and hardening kit assets
python gen_datapack.py  # regenerate the editable recipe datapack
```

## License

CC0-1.0
