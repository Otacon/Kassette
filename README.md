<p style="text-align: center;">
  <img src="frontend/src/wasmJsMain/resources/icons/icon-192.png" alt="Kassette NES icon" width="192" height="192">
</p>
<h1 style="text-align: center;">Kassette</h1>

<p style="text-align: center;">
  <a href="https://orfeociano.substack.com/">
    <img src="https://img.shields.io/badge/Substack-orfeociano-FF6719?logo=substack&logoColor=white" alt="Substack blog">
  </a>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Kotlin-WasmJs-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform Web">
  <img src="https://img.shields.io/badge/Kotlin-JVM-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform JVM">
  <a href="https://www.hydraulic.dev/">
    <img src="https://img.shields.io/badge/Packaged%20with-Conveyor-3B82F6" alt="Packaged with Conveyor">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License">
  </a>
</p>

Kassette is a small, focused NES emulator for desktop and the browser. It is built for people who want to load their own
legally obtained `.nes` files and play them with a simple menu-driven interface.

No ROMs, BIOS files, Nintendo assets, screenshots, extracted game data, disassemblies, or ROM patches are included in
this repository.

## Features

* Desktop and browser frontends.
* Load local `.nes` ROM files from the app menu.
* Keyboard and controller input.
* Customisable input bindings from `Input` -> `Bindings...`.
* Pause, reset, CRT effect, and cast-shadow video filter toggles from the menu bar.
* Region-aware frame pacing for NTSC, PAL, Dendy, multi-region, and Japan/Famicom-timed cartridges.
* iNES 1.0 and NES 2.0 ROM header support for the mapper set listed in [Technical Details](TECHNICAL.md).

## Playing

Open Kassette, then choose `File` -> `Open ROM...` and select a legally obtained `.nes` file.

The desktop app can also be started with a ROM path by developers:

```bash
./gradlew run --args="/path/to/game.nes"
```

See [Development](DEVELOPMENT.md) for local run, build, test, and distribution commands.

## Controls

Controls support both keyboard and controller input. Input customisation is available from the app menu, but this
feature is still in development.

## Compatibility

Kassette currently supports these mapper families: NROM, MMC1, UxROM, CNROM, MMC3, AxROM, Color Dreams, BNROM/NINA-001,
GxROM, BF909x, NINA-03/06, Jaleco JF-xx, and NINA multicart software.

This is an emulator MVP, not a cycle-perfect emulator. For the full supported ROM matrix, current emulator scope, known
limitations, and architecture notes, see [Technical Details](TECHNICAL.md).

## Legal Notice

Users must provide their own legally obtained ROM files. Do not commit ROMs or copyrighted game assets to this
repository. `.nes` files are ignored by `.gitignore` to help prevent accidental commits.

## License

Kassette is available under the [MIT License](LICENSE).
