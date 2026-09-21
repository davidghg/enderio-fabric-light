"""Builds a ready-to-edit datapack that overrides this mod's crafting recipes.

A world datapack is loaded after the mod's own data, so a file with the same path replaces the
mod's version of it. The pack therefore starts out as an exact copy of every mod recipe: editing a
file changes that recipe, deleting one falls back to the mod's default, and the "filter" section in
pack.mcmeta removes a recipe entirely.

Run after changing any recipe so the template stays in sync. Pure stdlib; the output is committed.
"""
import json
import os
import shutil

NS = "enderio-fabric-light"
PACK_FORMAT = 101  # 26.1.2, from the game's version.json (pack_version.data_major)

SRC = os.path.join("src", "main", "resources", "data", NS, "recipe")
OUT = os.path.join("datapack", "enderio-fabric-light-recipes")

DESCRIPTION = "Editable copies of the EnderIO Fabric light recipes"

README = """EnderIO Fabric light - recipe datapack
=====================================

Drop this folder (the one holding pack.mcmeta) into:

  <world>/datapacks/           single player, or a server world

Then run /reload, or restart the server. Check with /datapack list that
"enderio-fabric-light-recipes" is enabled.

Editing
-------
data/{ns}/recipe/ holds one file per recipe, identical to the mod's own.
Change the "pattern" and "key" entries to make a recipe cheaper or more
expensive; every item id is a plain vanilla or mod id. A shaped recipe's
pattern may be 1x1 up to 3x3, and every letter used in it needs a key.

Delete a file to go back to the mod's default recipe.

Removing a recipe altogether: add it to the "filter" block in pack.mcmeta,
which hides the mod's file, for example

  "filter": {{ "block": [
    {{ "namespace": "{ns}", "path": "recipe/wrench.json" }},
    {{ "namespace": "{ns}", "path": "advancement/recipes/tools/wrench.json" }}
  ] }}

Filter the matching advancement too (advancement/recipes/<category>/
<name>.json), otherwise the recipe book still tries to unlock a recipe that
no longer exists.

After /reload the recipe book updates on the next join; the recipes
themselves take effect immediately.
""".format(ns=NS)


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    recipes = os.path.join(OUT, "data", NS, "recipe")
    os.makedirs(recipes)

    mcmeta = {
        "pack": {"pack_format": PACK_FORMAT, "description": DESCRIPTION},
        # Nothing filtered by default; see the readme for how to drop a recipe.
        "filter": {"block": []},
    }
    with open(os.path.join(OUT, "pack.mcmeta"), "w", newline="\n") as f:
        json.dump(mcmeta, f, indent=2)
        f.write("\n")
    with open(os.path.join(OUT, "README.txt"), "w", newline="\n") as f:
        f.write(README)

    for name in sorted(os.listdir(SRC)):
        if name.endswith(".json"):
            shutil.copyfile(os.path.join(SRC, name), os.path.join(recipes, name))
    print(f"done: {OUT}")


if __name__ == "__main__":
    main()
