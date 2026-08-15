# Agent Handoff

Kassette is a Kotlin Multiplatform NES emulator MVP with desktop and browser frontends. The practical goal is local
playback of user-supplied `.nes` ROMs with a compact menu-driven UI, customisable input bindings, and clear rejection of
unsupported ROM formats.

Do not add ROMs, BIOS files, Nintendo assets, screenshots, extracted data, disassemblies, or ROM patches.

## Repository

This is a Git repository. Check the worktree before edits:

```bash
git status --short
```

Do not revert user changes unless explicitly requested.

## Commands

Java target: JDK 21 or newer. The JVM toolchain version comes from the `jvmToolchainVersion` Gradle property.

Main commands:

```bash
./gradlew build
./gradlew allTests
./gradlew :nes:jvmTest :frontend:jvmTest
./gradlew run
./gradlew run --args="/path/to/game.nes"
./gradlew run --args="--debug /path/to/game.nes"
./gradlew :frontend:wasmJsBrowserDevelopmentRun
./gradlew :frontend:createDistributable
./gradlew :frontend:zipDesktopDistribution
```

The desktop CLI currently accepts `--debug` and an optional ROM path. Controller support, CRT, and cast-shadow filters
are runtime UI/preference features, not CLI flags.

## Documentation

Public docs are split by audience:

* `README.md`: consumer-facing overview, features, playing instructions, controls, legal notice.
* `DEVELOPMENT.md`: local build, test, run, and packaging commands.
* `TECHNICAL.md`: supported ROM format, emulator scope, limitations, and architecture.
* `AGENT.md`: this implementation handoff for coding agents.
* `NES_CPU.md`: cycle-level NES CPU, interrupt, unofficial-opcode, and DMA behavior contract.

Keep the README concise and user-focused. Put internal architecture or command detail in `TECHNICAL.md` or
`DEVELOPMENT.md` instead.

## Project Layout

Core emulator code is under `nes/src/commonMain/kotlin/nes` and should not depend on frontend graphics, input, audio,
UI, preferences, or ROM loading APIs.

Key packages:

```text
nes/src/commonMain/kotlin/nes/                 Machine orchestration and region timing
nes/src/commonMain/kotlin/nes/apu/             2A03/2A07-style APU generation and DMC DMA
nes/src/commonMain/kotlin/nes/cartridge/       Cartridge socket and Mapper 0, 1, 2, 3, 4, 7, 11, 34, 66, 71, 79, 87, 113
nes/src/commonMain/kotlin/nes/cpu/             6502 CPU, CPU bus, interrupts, and DMA arbitration
nes/src/commonMain/kotlin/nes/input/           NES controller strobe/serial protocol
nes/src/commonMain/kotlin/nes/ppu/             PPU registers, memory, timing, and software rendering
nes/src/commonMain/kotlin/nes/util/            Shared bit/byte helpers for hot paths
nes/src/commonMain/kotlin/nes/di/              Metro graph for core emulator dependencies
frontend/src/commonMain/kotlin/frontend/       Shared UI, runtime host, platform contracts, preferences-backed state
frontend/src/commonMain/kotlin/frontend/controllerSettings/ Custom input binding UI and lookup mapping
frontend/src/commonMain/kotlin/nes/cartridge/  iNES 1.0 / NES 2.0 parser and nes20db metadata application
frontend/src/jvmMain/kotlin/app/               Desktop CLI entry point and argument parsing
frontend/src/jvmMain/kotlin/frontend/          Desktop Compose/Skiko, OpenAL, JInput, keyboard, renderer, file chooser
frontend/src/wasmJsMain/kotlin/app/            Browser app entry point
frontend/src/wasmJsMain/kotlin/frontend/       Browser page activity, WebGL, WebAudio, File API, keyboard, Gamepad API
```

Tests are in:

```text
nes/src/commonTest/kotlin/
frontend/src/commonTest/kotlin/
frontend/src/jvmTest/kotlin/
```

## Implemented Scope

ROM/cartridge:

* iNES 1.0 and NES 2.0 parsing.
* nes20db SHA-1 metadata overrides for known ROM region, mapper, submapper, and mirroring.
* Filename region fallback for common USA/Japan/Europe/PAL markers when header metadata is missing or ambiguous.
* Mapper 0 / NROM, Mapper 1 / MMC1, Mapper 2 / UxROM, Mapper 3 / CNROM, Mapper 4 / MMC3, Mapper 7 / AxROM, Mapper 11 /
  Color Dreams, Mapper 34 / BNROM/NINA-001, Mapper 66 / GxROM, Mapper 71 / BF909x, Mapper 79 / NINA-03/06, Mapper 87 /
  Jaleco JF-xx, and Mapper 113 / multicart NINA.
* NROM-128 and NROM-256.
* MMC1 with serial register loading, PRG/CHR banking, PRG RAM where applicable, and runtime
  one-screen/horizontal/vertical mirroring.
* UxROM/UNROM with switchable 16 KiB lower PRG bank and fixed last 16 KiB upper PRG bank.
* CNROM with fixed PRG ROM and switchable 8 KiB CHR ROM banks.
* Mappers 2, 3, and 7 support Mesen-style NES 2.0 submapper 2 bus-conflict behavior.
* MMC3 with precomputed 8 KiB PRG/1 KiB CHR page offsets, PRG RAM, runtime mirroring control, and approximate scanline
  IRQs.
* AxROM with switchable 32 KiB PRG banks up to 512 KiB PRG ROM, CHR RAM, and mapper-controlled one-screen mirroring.
* Mapper 11 / Color Dreams with bus-conflicted 32 KiB PRG and 8 KiB CHR ROM banking.
* Mapper 34 follows Mesen's mapper-factory split: CHR RAM is BNROM, CHR ROM is NINA-001, and NES 2.0 submappers 1/2
  force NINA-001/BNROM.
* Mapper 66 / GxROM with 32 KiB PRG and 8 KiB CHR ROM banking.
* Mapper 71 / BF909x, Mapper 79 / NINA-03/06, Mapper 87 / Jaleco JF-xx, and Mapper 113 / multicart NINA follow Mesen's
  discrete mapper behavior.
* CHR ROM and CHR RAM, but not mixed CHR ROM plus CHR RAM boards.
* Horizontal and vertical mirroring.
* PAL, NTSC, Dendy, and multi-region timing metadata; multi-region currently maps to NTSC timing.
* `Cartridge` stores ROM metadata/data plus a generic `Mapper` instance.
* `CartridgeSocket` simulates cartridge insertion/removal and is the only cartridge access point for CPU/PPU buses.
* Clear rejection for invalid headers, truncated ROMs, unsupported mappers/submappers, unsupported NES 2.0 hardware,
  four-screen mirroring, miscellaneous ROM regions, and invalid mapper sizes.

CPU/bus:

* 6502/Ricoh 2A03-style CPU with official opcodes.
* Reset, NMI, IRQ, BRK/RTI, stack behavior, page-cross penalties, branch penalties, zero-page wrapping, and indirect JMP
  wrap bug.
* Stable unofficial NMOS opcodes, including read-modify-write combinations and unstable indexed stores.
* CPU memory map for RAM, PPU registers, APU registers, OAM DMA, controller, and cartridge space.
* Cartridge CPU-space reads/writes go through `CartridgeSocket`, not directly through mapper classes.
* Follow `NES_CPU.md` when changing instruction semantics, interrupt polling, bus cycles, or DMA.

PPU:

* 256x240 software framebuffer.
* PPU register interface `$2000-$2007` with mirroring.
* Dedicated PPU bus for CHR ROM/RAM, nametable RAM, palette RAM, and PPU-side mirroring.
* CHR ROM/RAM access goes through `CartridgeSocket`, not directly through mapper classes.
* Buffered PPUDATA reads and palette read behavior.
* VBlank flag, status read side effects, and NMI generation.
* Background rendering with attributes, scrolling, and nametable-bit handling.
* Sprite rendering with 8x8 and 8x16 sprites, flips, priority, approximate sprite-zero hit, and basic overflow
  detection.
* Loopy scroll state: `v`, `t`, fine X, write latch, coarse X/Y increments, horizontal/vertical transfers.
* Rendered odd frames skip one pre-render PPU dot.

APU/audio:

* Region-aware mono PCM generation at 44.1 kHz for the 2A03/2A07-style APU.
* Pulse 1, pulse 2, triangle, noise, and DMC channels with region-specific noise and DMC periods.
* Length counters, envelopes, pulse sweep and overflow muting, triangle linear counter/DAC hold, and frame-counter
  clocks with frame IRQ status, inhibit, and acknowledgement behavior.
* APU behavior is partially aligned with MesenCE for pulse sweep divider periods, DMC startup bit-counter silence, and
  NTSC/PAL/Dendy DMC period tables.
* NES nonlinear pulse/TND mixing followed by 90 Hz and 440 Hz high-pass filters and a 14 kHz low-pass filter.
* OpenAL desktop playback and WebAudio browser playback.
* DMC sample playback with CPU-memory reads, buffered output after reader disable, looping, IRQ state, address wrapping,
  and cycle-aligned CPU DMA.

Input/frontend:

* Desktop Compose/Skiko window and browser Compose viewport.
* Shared menu bar has `File`, `Game`, `Video`, and `Input` menus.
* `File` opens local ROMs and exits on desktop.
* `Game` pauses/resumes and resets when a ROM is running.
* `Video` toggles preference-backed `CRT` or `NONE` filters.
* `Input` opens `Bindings...` for primary and secondary mappings per NES button.
* Default bindings are A=`Z`/gamepad button 1, B=`X`/button 0, Select=`Left Shift`/button 8, Start=`Enter`/button 9,
  D-pad mapped to arrows and gamepad axes 0/1.
* Bindings are stored with `multiplatform-settings` serialization and applied through `ControllerInputMapper`.
* Desktop controller support uses JInput and polls the first available controller automatically.
* Browser controller support polls the first connected Gamepad API device automatically.
* Opposite directions are filtered in `NesController`.
* Input state is sampled before each frame and approximately every 2 ms during frame emulation to reduce
  input-to-emulation latency.

Diagnostics:

* `--debug` enables debug-level Kermit logging.
* Runtime window title includes app version, ROM name, selected region, pause state, and measured FPS where available.

## Architecture Notes

* `NesMachine` starts without a constructor cartridge argument; call `insert(cartridge)` before `powerOn()` for normal
  use.
* `NesMachine.powerOn()` performs a hard reset of core components and marks the machine powered on. `powerOff()` only
  changes powered state.
* `NesMachine.reset()` resets CPU, PPU, APU, controller protocol, pending DMA, and active mapper runtime state while
  preserving RAM where soft reset behavior applies.
* `NesMachine.runUntilFrame()` clears frame-complete state, begins an APU frame, steps CPU instructions until the PPU
  completes a frame, and invokes an optional input poll callback during CPU phases.
* NMI is edge-latched with one-cycle recognition latency; IRQ is level-sampled each CPU cycle from mapper and APU
  sources and accepted with instruction-specific polling behavior.
* CPU and PPU buses should depend on `CartridgeSocket`, not concrete mapper classes.
* `InesParserComposite` hashes ROM data excluding the iNES header/trainer payload, consults nes20db, routes to iNES 1.0
  or NES 2.0 parsing, and applies database metadata when present.
* `InesParserUtils` validates mapper/submapper/size combinations and creates concrete mapper instances after validation.
* `FrontendComponent` owns app-scoped Metro dependencies, including parser, machine, renderer, audio, input, runtime
  host, view model, preferences, and controller settings view model.
* `MainScreenViewModel` owns ROM loading, machine power cycling, pause state, video filter preferences, and title
  updates.
* `EmulatorRuntimeHost` owns the background coroutine loop and guards runtime stepping with a mutex for pause/resume.
* `EmulatorRuntime.step()` polls input, updates the emulated controller, runs one frame, submits APU samples, and
  submits the software framebuffer.
* Use shared bit/byte helpers from `nes.util.BitExtensions` instead of repeating raw truncation masks:
  `Byte.toUnsignedInt()` for byte-array reads, `Int.low8Bits()` for byte/register truncation, `Int.low16Bits()` for CPU
  address truncation, and `Int.pageBase()` for 6502 page-crossing checks. Keep explicit `and` masks for local flag or
  bitfield checks when the mask itself is meaningful.

## Known Limitations

* No mappers beyond Mapper 0, Mapper 1, Mapper 2, Mapper 3, Mapper 4, Mapper 7, Mapper 11, Mapper 34, Mapper 66, Mapper
  71, Mapper 79, Mapper 87, and Mapper 113.
* Larger chips such as MMC5, MMC2/MMC4, Bandai FCG, Jaleco SS88006, Namco 163, VRC2/VRC4, VRC6, and Sunsoft FME-7 still
  need dedicated IRQ/audio/timing infrastructure before they can be ported accurately.
* Mapper 1 does not support SUROM/SOROM/SXROM-style extended banking variants.
* Mapper 4 scanline IRQ timing is approximate, not cycle-perfect MMC3 A12 timing.
* NTSC/PAL/Dendy timing is supported from nes20db, ROM header metadata, and filename fallback; timing remains
  approximate.
* PPU is approximate, not cycle-perfect.
* Sprite-zero hit is approximate.
* Sprite overflow uses simple ninth-sprite detection and does not emulate the hardware evaluation bug.
* APU register effects and frame-counter events are instruction-batched; `$4017`'s parity-dependent 3/4-cycle reset
  delay, exact frame IRQ edge timing, and Mesen's full event scheduler are not modeled.
* APU PCM uses point sampling plus the output filter chain, not band-limited synthesis, so high-frequency aliasing can
  remain.
* No save states, rewind, cheats, debugger UI, two-player input, ZIP loading, networking, ROM downloading, or ROM
  patching.
* PPU rendering remains scanline-based, so mid-scanline palette, scroll, CHR bank, mask, and OAM changes are
  approximate.

## Development Guidance

Prefer small targeted fixes with deterministic tests. For rendering issues, inspect PPU scroll/sprite-zero behavior
first, then mapper IRQ timing, input/controller protocol, CPU details, and APU details.

Run focused tests while iterating:

```bash
./gradlew :nes:jvmTest :frontend:jvmTest
```

Run the full build before considering work complete:

```bash
./gradlew build
```
