EnderIO Fabric light - recipe datapack
=====================================

Drop this folder (the one holding pack.mcmeta) into:

  <world>/datapacks/           single player, or a server world

Then run /reload, or restart the server. Check with /datapack list that
"enderio-fabric-light-recipes" is enabled.

Editing
-------
data/enderio-fabric-light/recipe/ holds one file per recipe, identical to the mod's own.
Change the "pattern" and "key" entries to make a recipe cheaper or more
expensive; every item id is a plain vanilla or mod id. A shaped recipe's
pattern may be 1x1 up to 3x3, and every letter used in it needs a key.

Delete a file to go back to the mod's default recipe.

Removing a recipe altogether: add it to the "filter" block in pack.mcmeta,
which hides the mod's file, for example

  "filter": { "block": [
    { "namespace": "enderio-fabric-light", "path": "recipe/wrench.json" },
    { "namespace": "enderio-fabric-light", "path": "advancement/recipes/tools/wrench.json" }
  ] }

Filter the matching advancement too (advancement/recipes/<category>/
<name>.json), otherwise the recipe book still tries to unlock a recipe that
no longer exists.

After /reload the recipe book updates on the next join; the recipes
themselves take effect immediately.
