# BBS FSP

Standalone Fabric addon for BBS FS. It registers the `bbs_fsp:canvas` clip, stores frame/layer pixel data, and provides viewport painting, a grid, a brush editor, a screen-wide eyedropper and film rendering of the painted layers.

It also adds the `bbs_fsp:graffiti` form (Graffiti), a picture that lives in the scene like any other form and is painted frame by frame right over the animation; see [Graffiti](#graffiti).

## Supported versions

| Jar | Minecraft | Java | BBS FS |
| --- | --- | --- | --- |
| `bbs-fsp-<version>+bbs2.6-1.20.1.jar` | 1.20.1 | 17 | 2.6, 2.6.1 |
| `bbs-fsp-<version>+bbs2.7-1.20.1.jar` | 1.20.1 | 17 | 2.7 |
| `bbs-fsp-<version>+bbs2.6-1.20.4.jar` | 1.20.4 | 17 | 2.6, 2.6.1 |
| `bbs-fsp-<version>+bbs2.7-1.20.4.jar` | 1.20.4 | 17 | 2.7 |
| `bbs-fsp-<version>+bbs2.6-1.21.11.jar` | 1.21.11 | 21 | 2.6, 2.6.1 |
| `bbs-fsp-<version>+bbs2.7-1.21.11.jar` | 1.21.11 | 21 | 2.7 |

BBS FS reports its version as `<version>-<minecraft>` (for example `2.6-1.20.4`), which semantic versioning treats as a pre-release of `2.6`. The dependency ranges therefore end in `-` (`>=2.6- <2.7-`), otherwise Fabric Loader would reject BBS 2.6 itself and accept 2.7 instead.

## Build

The addon compiles against BBS FS jars built from `../bbs-fs`:

```text
scripts/prepare-bbs.sh      # exports every pinned BBS commit into ../bbs-fs-builds/<version> and builds it
scripts/build-all.sh        # builds, checks and collects all four addon jars into dist/
```

`prepare-bbs.sh` uses `git archive`, so the `bbs-fs` checkout itself is never modified. One target can be built on its own:

```text
./gradlew -Ptarget=1.21.11-bbs2.7 checkCanvas remapJar
```

Targets live in `targets/*.properties`. `-Pbbs_jar=<path>` compiles against another BBS jar; `build-all.sh` uses it to also compile the 2.6 targets against BBS 2.6.0.

Sources:

- `src/main` — common data model, raster tools and the brush engine (safe on a dedicated server);
- `src/client` — editor UI, painting, eyedropper and mixins shared by both Minecraft versions;
- `src/mc1204`, `src/mc12111` — rendering that differs between Minecraft versions (texture cache, layer drawing, world/foreground passes, screen colour read-back).

Checks: `checkCanvasFrames` (frames and layers), `checkCanvasBrushes` (stamps, patterns, eraser, gradient, compositing, persistence), `checkStrings` (all 19 locales have the same keys and placeholders), `checkGraffiti` (graffiti frames: shared copies, data, layers, resizing, compositing, blur, halo, lights, tools, the keyframe type); `checkCanvas` runs all four.

In-game checks start a real client in a throwaway run folder (offline player, nothing from your own `.minecraft` is used), create a world, open the film editor on a canvas clip and drive it: painting with the pencil and the marker, the brush editor buttons, the eyedropper on the film preview and on the panel, cancelling it, Alt+click on the canvas, screenshots of the three render bands, and the film played in the world with the editor closed. Results are logged with an `[in-game check]` prefix and screenshots land in the run folder.

```text
./gradlew -Ptarget=1.21.11-bbs2.7 runClientGameTest   # Fabric client game test, build/run/clientGameTest/screenshots
./gradlew -Ptarget=1.20.4-bbs2.7 runDevtest           # 1.20.4 has no client game tests: a driver mod, build/1.20.4-bbs2.7/run/devtest/screenshots
./gradlew -Ptarget=1.20.1-bbs2.7 runDevtest           # the same driver on 1.20.1 (both share src/mc1204)
```

Dedicated server checks start a real server whose classpath has no Minecraft client classes. A small server mod checks the clip registration, saves and loads a painted film through BBS's own storage and stops the server. Starting a Minecraft server means accepting its EULA, which `-PacceptEula` does for the test folder only:

```text
./gradlew -Ptarget=1.20.4-bbs2.7 -PacceptEula runDevserver                     # build/<target>/run/devserver
./gradlew -Ptarget=1.21.11-bbs2.7 -PacceptEula runClientGameTest                # the 1.21.11 game test also joins a dedicated server
./gradlew -Ptarget=1.20.4-bbs2.7 -PacceptEula -PserverCheckHost runDevserver   # 1.20.4: keep the server up for a client...
./gradlew -Ptarget=1.20.4-bbs2.7 -PdevtestServer=localhost:25601 runDevtest    # ...then, from a second terminal, join it
```

In the joined runs the client paints a full-size canvas while connected, saves it through BBS's network repository, leaves, joins again and loads it back over the network; the server checks its own copy on disk.

The test code lives in `src/mc12111/gametest`, `src/mc1204/devtest` and `src/devserver` and is never packed into the released jars. Both ignore the real mouse, keyboard and window focus while they run (Fabric's game tests do this themselves; the 1.20.4 driver blocks real input with its own mixins), so moving the mouse over the game window or switching apps does not disturb them. Do not run two Gradle builds of this project at once: they share build state and one can leave the other's run without classes.

## Features

- 120×68 default canvas, 30-tick default duration, 2-tick initial frame spacing, frame-count increase without deleting drawings;
- three default layers: over the final film frame, over opaque blocks but behind entities/models, and behind opaque blocks;
- grey painting grid with offset and rotation; the offset is measured in canvas cells so the grid and the rendered layers line up at every resolution;
- brush editor: pencil, marker, gradient, dither and spray patterns; square, circle, diamond, horizontal and vertical shapes; size 1–64; softness, brush opacity, density and gradient length where the pattern uses them; eraser mode; a live preview of a sample stroke; every pattern remembers its own settings;
- strokes keep their coverage per pixel, so translucent markers do not darken where dabs overlap and soft edges never eat into the middle of the same stroke;
- outline of the brush under the cursor;
- eyedropper button: the next left click anywhere in a BBS menu takes that pixel's colour (film preview, canvas, panels, swatches); right click or Escape cancels;
- Alt+click on the canvas takes the composited colour of all visible layers with its alpha, falling back to the screen colour on an empty cell;
- primary and secondary colours with swap, recent colours, quick swatches, separate paint opacity; colours and brushes persist in `bbs_fsp_brushes.json` next to the BBS settings;
- translucent previous-frame preview; frame strip with thumbnails; layer list, adding and duplicating layers across all frames;
- layered compositing in the world pass and before BBS copies its framebuffer for preview and export;
- texture cache with revision-based uploads that releases textures of layers not drawn for ten seconds.

Not yet exposed in the interface: the line, rectangle, ellipse, fill, gradient-fill and blur raster tools in `CanvasRasterTools`, layer reordering/renaming/band/opacity controls.

The two world bands are drawn before entities, so whatever Minecraft draws later in the world pass lands on top of them: entities and the player's hand, but also translucent geometry such as water, stained glass and clouds (a band seen through water is tinted by it). The foreground band covers the whole frame, the hand included, and stays under the HUD.

## Graffiti

A form in the "Extra" category of the form palette, next to the video, the mobs and the rest. It is BBS's own billboard underneath, so it is moved, scaled, turned, parented and keyframed through the usual transform, hides models standing behind it and is hidden by models in front of it, and works with translucency and shader packs like a billboard does.

- **Pixel art track.** What the graffiti shows comes from the `pixel_art` property: a keyframe type (`bbs_fsp:graffiti_picture`) whose every keyframe is a whole drawn frame with its layers, held until the next keyframe. Without keyframes the form shows its own picture.
- **Editor.** A paint program over the whole screen, opened from the spray button in the film preview's icon bar (shown while the selected replay wears a graffiti), from the form's panel or from a pixel art keyframe. The middle is the scene the film renders and the brush lands on the graffiti wherever it stands: the cursor is cast as a ray onto the quad, so painting follows any position, rotation, scale, parent or camera. `F` flies the camera as in the film editor (`W` `A` `S` `D`, `Space` and `Shift` move it, the right mouse button held down looks around, the wheel sets the speed), and the brush keeps painting while it flies; BBS's free look stays off while the editor is open, since the pointer is needed to paint. `V` switches to a flat view of the picture alone (zoom with the wheel, pan with the right or middle button); outside a film only the flat view exists. While the editor is open the keyboard is its alone, so no key reaches the film editor under it (`Ctrl+S` still saves the film).
- **Tools** (the texture editor's set plus the canvas brush engine): brush with every canvas pattern and shape, eraser, fill, line, rectangle and ellipse (filled with the shapes toggle), blur brush, move layer, pipette (or Alt+click); clear, flip horizontally/vertically, blur layer; resize every frame; layers with visibility, glow, opacity, add, duplicate, remove, reorder, merge down; colours with swap, screen eyedropper, palette and recent colours; undo/redo through the film's history (`Ctrl+Z`/`Ctrl+Y`).
- **Frames.** The bottom of the editor stands in for the film's timeline under it: a ruler of ticks with the pixel art keyframes and the cursor on it (click or drag to move the cursor, the wheel steps it) and a strip of thumbnails of the frames (a click moves the cursor to that frame). `,` and `.` go to the previous and next frame, `←` and `→` step a tick, `Space` plays the film. The first stroke on a graffiti without keyframes creates one at the cursor. `N` adds an empty frame and `Shift+N` a copy of the current one: at the cursor when no frame starts there, otherwise right after the current frame, one frame length later (`Frame length`, 2 ticks by default), the frames after it moving along when they are in the way. The previous frame is shown faintly under the one being painted (`Previous frame`).
- **Settings.** The film draws its own copy of each replay's form, so a setting changed in the editor (blur, glow, light, grid, billboard, shading) is written into that copy, the replay's form and the form editor's copy at once, and shows right away. Animated settings are kept within the ranges of their fields when drawn.
- **Grid.** Drawn along the graffiti only while it is selected in the film editor or in the pixel art editor, never in the render or the export.
- **Blur.** A keyframeable `blur` softens the whole picture at render time; the blur brush and "blur layer" soften the pixels themselves.
- **Glowing paint.** Layers can glow (the sun toggle, or the glowing paint mode that sends strokes to a glowing layer). Glowing layers are drawn at full brightness over a halo (`glow`, `glow_radius`). With [IRLights](https://modrinth.com/mod/irlights) (mod id `irlite`) installed the glowing paint also becomes real light: every lit quarter of the picture registers a point light of its average colour into IRLights' light registry each frame (`light_intensity`, `light_radius`), so it falls on models and blocks under a patched shader pack. IRLights is not a dependency: its registry is looked up by name and the light is skipped when it is missing.

## Verification status

Compiled and checked for all four targets, with every mixin target and injection point confirmed in the bytecode of BBS 2.6, 2.6.1 and 2.7 for both Minecraft versions. The in-game checks above pass in a running client for all four targets (32 checks each): painting, the brush editor, the eyedropper on the preview and on the panel, Alt+click picking, the three render bands (behind structures stays behind terrain, behind entities covers it, foreground on top) and playback of the film in the world.

Dedicated server: on all four targets a real server without client classes registers the canvas clip and saves and loads a painted film through BBS. On all four targets a client joined a dedicated server, painted a full-size 120×68 canvas, saved it, and after reconnecting got the film back with every frame. The server's copy on disk was checked too. The classes a server loads reference no client-only class of Minecraft, BBS or Fabric API.

Graffiti: the in-game checks register the form, find it in the form palette and its renderer, render it in a film, open the pixel art editor, paint into a new pixel art keyframe through the scene, paint glowing paint into a glowing layer, switch to the flat view, change a setting and find it on the copy the film draws, add a frame with `N` right after the current one and paint it, walk the frames with `,` and `.`, add a frame in the middle and see the next one move along, copy a frame, step the cursor with the arrows and the ruler, fly the camera with `F` and `W` (with free look on in BBS's settings, and the pointer still the editor's) and close the editor; they pass on 1.20.4 (BBS 2.6 and 2.7) and 1.21.11 (BBS 2.6 and 2.7). On all four targets a dedicated server registers the graffiti form and saves and loads a film whose replay wears a graffiti with two pixel art keyframes, a glowing layer and translucent paint.

Not verified yet: video export (needs ffmpeg), Iris/IRLights (the graffiti lights are written against the IRLights 1.1.8 source, never run against the real jar).
