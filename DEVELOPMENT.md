# Development

This document is for building, running, testing, and packaging Kassette locally. User-facing emulator details live
in [README.md](README.md); emulator internals and compatibility details live in [TECHNICAL.md](TECHNICAL.md).

## Requirements

Use JDK 21 or newer. The project uses Gradle with Kotlin DSL, Kotlin Multiplatform, Kotlin/JVM, Kotlin/Wasm, Compose
Multiplatform, Skiko/SkSL, WebGL, OpenAL/WebAudio, JInput, Metro, Kotlin Test, and JUnit 5.

Gradle toolchains are configured through Foojay, and the JVM toolchain version is read from the `jvmToolchainVersion`
Gradle property.

## Build

Run the full build:

```bash
./gradlew build
```

Run all tests:

```bash
./gradlew allTests
```

Run the most common focused JVM checks:

```bash
./gradlew :nes:jvmTest :frontend:jvmTest
```

The frontend build generates `frontend/src/commonMain/resources/nes20db.csv` from `nes20db.xml` when the CSV is missing.

## Run Desktop

Start the desktop app without a ROM, then choose `File` -> `Open ROM...`:

```bash
./gradlew run
```

Start with a legally obtained `.nes` ROM file:

```bash
./gradlew run --args="/path/to/game.nes"
```

Enable debug logging:

```bash
./gradlew run --args="--debug /path/to/game.nes"
```

Desktop play uses region-aware frame pacing derived from cartridge timing. Controller input is polled automatically
through JInput when a controller is available.

## Run Web

Run the browser development build:

```bash
./gradlew :frontend:wasmJsBrowserDevelopmentRun
```

Then choose a legally obtained `.nes` ROM from the browser menu. Browser audio is resumed from normal user gestures such
as opening a ROM or toggling a video option. Keyboard input is always available, and the first connected browser Gamepad
API controller is polled automatically.

The WebAssembly frontend includes a web app manifest. When the production distribution is served from `localhost` or
HTTPS, supported browsers can offer desktop installation using `manifest.webmanifest`.

Web assets live in `frontend/src/wasmJsMain/resources`: `index.html`, `favicon.ico`, `manifest.webmanifest`, and the
install icons under `icons/`. The manifest includes 192px, 512px, and maskable icon variants.

## Package Desktop

Create the Compose desktop distributable for the current host platform:

```bash
./gradlew :frontend:createDistributable
```

Create a ZIP containing the desktop app image:

```bash
./gradlew :frontend:zipDesktopDistribution
```

Pass `-PappVersion=<version>` to override the app version used by packaging tasks.
