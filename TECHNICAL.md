# Technical Details

This document describes Kassette's current emulator support, known limitations, and architecture. Build and run commands
live in [Development](DEVELOPMENT.md).

## Supported ROM Format

The loader supports iNES 1.0 and NES 2.0 ROMs for these mapper configurations:

| Mapper | Board          | Supported configurations                                                                                                     |
|--------|----------------|------------------------------------------------------------------------------------------------------------------------------|
| 0      | NROM           | NROM-128, NROM-256, 8 KiB CHR ROM, or 8 KiB CHR RAM                                                                          |
| 1      | MMC1           | Submapper 0, 32 KiB to 256 KiB PRG ROM, 4/8 KiB CHR ROM banking, 8 KiB CHR RAM, runtime mirroring                            |
| 2      | UxROM/UNROM    | Submapper 0 and submapper 2 bus conflicts, 32 KiB to 256 KiB PRG ROM, fixed last upper PRG bank, 8 KiB CHR RAM               |
| 3      | CNROM          | Submapper 0 and submapper 2 bus conflicts, 16 KiB or 32 KiB PRG ROM, switchable 8 KiB CHR ROM banks                          |
| 4      | MMC3           | Submapper 0, 32 KiB to 512 KiB PRG ROM, 8 KiB PRG banking, CHR ROM/RAM banking, PRG RAM, runtime mirroring, scanline IRQs    |
| 7      | AxROM          | Submapper 0 and submapper 2 bus conflicts, 32 KiB to 512 KiB PRG ROM, 8 KiB CHR RAM, one-screen mirroring                    |
| 11     | Color Dreams   | 32 KiB PRG bank switching, 8 KiB CHR ROM bank switching, bus-conflicted writes                                               |
| 34     | BNROM/NINA-001 | Mesen factory behavior: CHR RAM defaults to BNROM, CHR ROM defaults to NINA-001, NES 2.0 submappers 1/2 force NINA-001/BNROM |
| 66     | GxROM          | 32 KiB PRG bank switching and 8 KiB CHR ROM bank switching                                                                   |
| 71     | BF909x         | Codemasters switchable lower 16 KiB PRG bank, fixed upper PRG bank, 8 KiB CHR RAM, submapper 1 one-screen mirroring mode     |
| 79     | NINA-03/06     | `$4100-$5FFF` register writes, 32 KiB PRG banking, 8 KiB CHR ROM banking                                                     |
| 87     | Jaleco JF-xx   | Fixed PRG ROM, 8 KiB CHR ROM banking with Jaleco bit order                                                                   |
| 113    | NINA multicart | Mapper 79 multicart mode with larger PRG/CHR selection and mapper-controlled mirroring                                       |

Unsupported mapper-related configurations include:

| Area                                | Unsupported configurations                                                                                                                                      |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Mapper IDs                          | Any mapper not listed above                                                                                                                                     |
| Submappers                          | Unsupported submappers for listed mappers, including MMC6 / Mapper 4 submapper 1                                                                                |
| MMC1 variants                       | SUROM, SOROM, SXROM-style extended PRG/WRAM banking and other non-submapper-0 variants                                                                          |
| ROM layout                          | Mixed CHR ROM and CHR RAM boards, miscellaneous NES 2.0 ROM regions, invalid PRG/CHR sizes                                                                      |
| Hardware                            | Nonstandard NES 2.0 console types and accessories                                                                                                               |
| Complex mappers not yet implemented | MMC2/MMC4 (`9/10`), MMC5 (`5`), Bandai FCG (`16/159`), Jaleco SS88006 (`18`), Namco 163 (`19`), VRC2/VRC4 (`21/22/23/25`), VRC6 (`24/26`), Sunsoft FME-7 (`69`) |

The parser also supports horizontal, vertical, single-screen, and four-screen nametable mirroring, NES 2.0 extended mapper numbers, submapper
validation, linear and exponent/multiplier ROM sizes, explicit CHR RAM/NVRAM sizes, and NTSC/PAL/Dendy timing modes.

Unsupported formats are rejected with clear startup errors, including unsupported mappers/submappers, nonstandard
console types, miscellaneous ROM regions, mixed CHR ROM/RAM boards, invalid mapper PRG/CHR sizes,
invalid headers, and truncated data.

## Current Emulator Scope

Implemented:

* Kotlin Multiplatform core emulator module and desktop/browser frontend module.
* iNES 1.0 / NES 2.0 parser with nes20db metadata overrides and Mapper 0 / Mapper 1 / Mapper 2 / Mapper 3 / Mapper 4 /
  Mapper 7 / Mapper 11 / Mapper 34 / Mapper 66 / Mapper 71 / Mapper 79 / Mapper 87 / Mapper 113 cartridge mapping.
* Cartridge socket abstraction for insertion/removal and CPU/PPU cartridge access.
* 2A03-style 6502 CPU core for official opcodes.
* CPU bus RAM/register/controller/OAM DMA mapping, with cartridge space routed through the cartridge socket.
* Dedicated PPU bus for CHR ROM/RAM, nametable memory, palette memory, and PPU-side mirroring.
* Background rendering, 8x8 and 8x16 sprite rendering, palette selection, sprite priority, sprite-zero hit
  approximation, and basic sprite overflow detection.
* VBlank flag behavior, status read side effects, NMI triggering, buffered PPUDATA reads, and Loopy scroll state.
* Region-timed 2A03/2A07 APU audio with pulse, triangle, noise, DMC, nonlinear mixing, frame/DMC IRQs, pulse sweep
  periods, DMC startup bit-counter behavior, and an NES-style output filter chain.
* One standard NES controller via `$4016` serial protocol.
* Customisable keyboard/controller bindings persisted through multiplatform settings.
* Desktop Compose/Skiko presentation of a software framebuffer, SkSL video effects, OpenAL audio playback, and JInput
  controller input.
* Kotlin/Wasm browser frontend with DOM menu, WebGL presentation, WebAudio playback, keyboard input, and Gamepad API
  controller input.
* Region-aware frame pacing for NTSC, PAL, Dendy, multi-region, and Japan/Famicom-timed cartridges.
* ROM loading/reloading, pause/resume, reset, CRT/cast-shadow toggles, and a desktop exit menu item.

## Known Limitations

This is an MVP, not a cycle-perfect emulator.

* APU channel timers, counters, sweep muting, triangle DAC hold, DMC output, nonlinear mixing, and output filtering are
  modeled, with pulse sweep divider periods, DMC bit-counter startup, and DMC NTSC/PAL period tables; register writes
  and frame-counter events are still advanced in instruction-sized batches rather than on exact CPU bus cycles.
* DMC DMA starts on eligible CPU reads, observes 3/4-cycle alignment, and arbitrates with OAM DMA; PCM generation still
  uses point sampling rather than band-limited synthesis.
* Mapper 0, Mapper 1, Mapper 2, Mapper 3, Mapper 4, Mapper 7, Mapper 11, Mapper 34, Mapper 66, Mapper 71, Mapper 79,
  Mapper 87, and Mapper 113 only.
* Mapper 1 supports basic MMC1/submapper 0 boards; SUROM/SOROM/SXROM-style extended banking variants are not supported.
* Mapper 4 uses filtered PPU A12 transitions; clone-specific filters and revision-specific IRQ behavior remain
  approximate.
* Region timing is approximate and selected from nes20db metadata when available, then ROM header metadata or filename
  markers; multi-region software defaults to NTSC timing.
* No save states, rewind, cheats, debugger UI, two-player input, ZIP loading, network features, downloading, or
  patching.
* PPU rendering remains approximate for full sprite shifter timing, OAM corruption/decay, open-bus decay, and per-pixel
  color emphasis.
* Sprite-zero hit is approximate.
* Sprite overflow uses simple ninth-sprite detection and does not emulate the hardware evaluation bug.
* Mid-scanline PPU changes are dot-driven, but uncommon register collision glitches remain approximate.
* The steady-state CPU path avoids collections in dispatch, but address helper objects remain and should be removed
  before claiming strict allocation-free operation.

## Architecture

Core emulator code is under `nes/src/commonMain/kotlin/nes` and does not depend on frontend graphics, input, audio, or
ROM loading APIs.

* `nes.NesMachine`: core CPU/PPU/APU/controller orchestration, cartridge insertion, power/reset, frame execution,
  interrupt sampling, and input polling cadence.
* `nes.cartridge`: cartridge metadata, cartridge socket, Mapper abstraction, Mapper 0, Mapper 1, Mapper 2, Mapper 3,
  Mapper 4, Mapper 7, Mapper 11, Mapper 34, Mapper 66, Mapper 71, Mapper 79, Mapper 87, Mapper 113 behavior.
* `nes.cpu`: CPU core, CPU bus memory map, interrupt polling, and OAM/DMC DMA arbitration.
* `nes.apu`: region-aware pulse, triangle, noise, and DMC generation, frame/DMC IRQ state, nonlinear mixing, and output
  filtering.
* `nes.ppu`: PPU registers, PPU bus, memory, timing, and framebuffer generation.
* `nes.input`: NES controller strobe/serial protocol.
* `nes.di`: Metro dependency graph for the core emulator.

The CPU bus and PPU bus do not depend on mapper classes directly. They communicate with `CartridgeSocket`, which
delegates to the mapper stored by the currently inserted `Cartridge`. Parsed iNES ROMs are validated in the frontend
parser package; unsupported mapper numbers are rejected there before a cartridge is created.

Frontend code is under `frontend/src`.

* `frontend/ComposeSkiaScreen`: Compose drawing surface for the shared framebuffer.
* `frontend/ComposeMenuBar`: shared app menu with file, game, video, and input actions.
* `frontend/MainScreen` and `frontend/MainScreenViewModel`: shared app state, ROM loading, pause handling, title
  updates, and preference-backed video filters.
* `frontend/EmulatorRuntimeHost` and `frontend/EmulatorRuntime`: coroutine-based emulator lifecycle, frame pacing, input
  polling, audio submission, and framebuffer submission.
* `frontend/controllerSettings`: custom input binding state, mapping lookup, labels, and bindings dialog.
* `KeyboardInput`: platform key events mapped through `ControllerInputMapper`.
* `ControllerInput`: JInput gamepad bindings on desktop and Gamepad API bindings on web, also mapped through
  `ControllerInputMapper`.
* `AudioPipeline`: queues generated mono PCM samples to OpenAL or WebAudio.
* `Renderer`: presents the 256x240 framebuffer through Skiko/SkSL on desktop or WebGL on web.
* `nes.cartridge` under `frontend/src/commonMain/kotlin`: iNES 1.0 / NES 2.0 parsing and nes20db metadata application.

Desktop CLI code is under `frontend/src/jvmMain/kotlin/app`.
