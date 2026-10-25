# NameStyle (Fabric, Minecraft 1.21.4)

Client-side only name styler. Nobody else sees it, nothing is sent to the server.

## Build
1. Easiest: generate a template at https://fabricmc.net/develop (MC 1.21.4, Yarn mappings, Fabric API, Java 21,
   package `dev.namestyle`, mod id `namestyle`). Copy this project's `src/` folder over the template's `src/`.
   (Or use the included build files and add a Gradle wrapper: `gradle wrapper --gradle-version 8.12`.)
2. `./gradlew build` -> jar in `build/libs/` (use the one without `-sources`).
3. Drop the jar in `.minecraft/mods` together with Fabric API.

## Use
- Press **Right Shift** (rebindable) or run **/namestyle** to open the editor.
- `/namestyle toggle`, `/namestyle profile <name>`
- Config: `.minecraft/config/namestyle.json`

## What it hooks (if the game crashes on launch the log names the one that doesn't match your MC version)
- ChatHud.addMessage            -> your name in chat
- PlayerListHud.getPlayerName   -> tab list
- Entity.getDisplayName         -> nametag
- LivingEntityRenderer.hasLabel -> show own nametag in 3rd person
