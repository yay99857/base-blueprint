# Base Blueprint

**Build your first base in one click: pick a shape and size, preview it in the world, and place it with blocks from your inventory, backpack and nearby chests.**

*🇧🇷 Versão em português mais abaixo.*

An early-game item, crafted from a crafting table and a stick, that builds a whole base at once.

## 🧭 What it builds
- **Shapes:** circle (the same rule as pixel circle generators), square, octagon and hexagon, any width and length.
- **Floor:** filled, outline only, or none.
- **Walls** of any height, with an automatic **door** (a 1×2 opening in the wall facing you).
- **Roof:** flat, or a hollow **dome** over round bases.
- **Foundation** on uneven ground: fills under the floor down to the terrain.
- **Two materials:** one for the floor, another for the walls and roof (stone floor, glass walls...).

## ✋ How it plays
- Right-click the air (or press **N**) for the menu, with a pixel preview of the footprint.
- Hold the blueprint and aim: the outline shows in the world, green when it can be built, red where something is in the way.
- Right-click a block to build. Sneak + right-click the air undoes your last base and gives the blocks back (saved with the world).
- Blocks come from your inventory, from backpacks you carry and from chests nearby. Creative is free.
- Respects spawn protection and claim mods: every block fires the normal place event.

Server config: max size, max wall height, undo, partial builds, container radius, max foundation depth.

---

## 🇧🇷 Em português

**Construa a primeira base com um clique: escolha a forma e o tamanho, veja a prévia no mundo e coloque com blocos do inventário, da mochila e dos baús por perto.**

- Formas: círculo, quadrado, octógono e hexágono, de qualquer tamanho.
- Piso cheio, só contorno ou sem piso; paredes de qualquer altura com **porta** automática; **teto** plano ou **cúpula**; **fundação** em terreno irregular.
- Material separado para o piso e para as paredes e o teto.
- Prévia no mundo (verde = dá para construir, vermelho = ocupado), desfazer salvo com o mundo, respeita proteções de claim.

---

## Publishing checklist (not part of the page)
1. `./gradlew build` and `./gradlew runGameTestServer`.
2. Bump `mod_version` in `gradle.properties` and the changelog in `README.md`.
3. Upload `build/libs/baseblueprint-<version>.jar`: NeoForge, 1.21.1, Java 21, client + server, MIT.
