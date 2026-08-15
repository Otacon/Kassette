# NES PPU Behavior Reference

This document is the implementation contract for the NES picture processing unit. Keep register, rendering, sprite,
address-bus, and mapper changes consistent with these rules and protect timing-sensitive behavior with dot-level tests.

## Timeline

Each scanline has 341 dots. NTSC has 262 scanlines; PAL and Dendy have 312. Scanline `-1` is pre-render, `0-239` are
visible, and 240 is post-render. Vblank begins at dot 1 of scanline 241 on NTSC/PAL and scanline 291 on Dendy. Vblank,
sprite-zero hit, and sprite overflow clear at pre-render dot 1.

Rendered odd NTSC frames skip pre-render dot 340. PAL and Dendy do not skip a dot. PAL performs forced OAM refresh on
scanlines 265-310.

## CPU Registers

Registers mirror every eight CPU addresses. Reads of write-only registers return the PPU I/O latch. `$2002` returns
status bits 5-7 and latch bits 0-4, clears vblank, lowers NMI, and resets the `$2005/$2006` write toggle.

`$2005` and `$2006` share the write toggle. The second `$2006` write commits the temporary address to `v` after three
PPU dots. During rendering, the commit changes the scroll address but does not immediately replace the address currently
driven by rendering fetches.

`$2007` completes its bus operation after five PPU dots and increments `v` one dot later. Non-palette reads return the
old read buffer. Palette reads return palette data immediately while preserving latch bits 6-7, then fill the read
buffer from the corresponding nametable shadow. A second `$2007` read during the suppression window returns the I/O
latch and does not restart or replace the first pending access.

## Scrolling And Background Fetches

Visible and pre-render fetches repeat an eight-dot sequence: nametable, attribute, pattern low, pattern high, and shifter
reload. Coarse X increments at tile boundaries, Y increments at dot 256, horizontal scroll bits copy at dot 257, and
vertical bits copy during pre-render dots 280-304. Dots 337 and 339 perform terminal nametable reads.

The physical PPU address bus is distinct from `v`. Rendering fetches, `$2006` commits outside rendering, `$2007`
increments, rendering transitions, and scanline boundaries all drive this bus. Every transition is visible to the
cartridge so address-sensitive mappers can observe CHR A12 edges.

## Sprites

Dots 1-64 clear secondary OAM, dots 65-256 evaluate sprites for the next line, and dots 257-320 fetch sprite data.
Evaluation starts at OAMADDR and reproduces the post-eight-sprite diagonal scan that causes false overflow results. The
first in-range sprite selected by an evaluation pass is the sprite-zero candidate even when OAMADDR is nonzero.

Sprite pixels are selected in OAM order. A transparent sprite pixel allows later sprites to compete; an opaque earlier
sprite wins even when its priority places it behind an opaque background. Sprite-zero hit requires opaque sprite and
background pixels, both layers enabled at that pixel, and x other than 255.

OAM attribute writes mask bits 2-4. `$2004` writes during rendering do not update OAM and instead increment the high six
bits of OAMADDR. OAM DMA starts at OAMADDR, wraps after 256 bytes, and leaves OAMADDR unchanged.

## Palette And Output

Palette RAM is 32 bytes with universal-background mirrors at `$3F10/$3F14/$3F18/$3F1C`. Stored values are six bits.
Grayscale masks colors with `$30`. PPUMASK emphasis bits affect every emitted pixel; PAL and Dendy swap red and green
emphasis interpretation. Mid-frame mask changes apply only to subsequently emitted pixels.

Any derived palette cache must be invalidated after direct palette writes, delayed `$2007` writes, bus-state restore,
or PPU-state restore.

## NMI And Vblank Races

The NMI output is `PPUCTRL.7 && PPUSTATUS.7`. Enabling NMI during vblank raises the line; disabling it or reading `$2002`
lowers the line. Reading `$2002` at vblank dot 0 suppresses that frame's vblank flag and NMI. The CPU detects edges from
the line itself, not from a separate persistent PPU event queue.

## Reset And State

Soft reset preserves primary OAM and VRAM contents while resetting register and rendering-pipeline state. Power-on RAM
contents are not generally guaranteed by hardware; deterministic initialization is acceptable when explicitly treated
as an emulator policy.

Captured state must include every value that affects future bus accesses or pixels. Derived host-framebuffer and palette
caches are rebuilt or invalidated after restoration.

## Compatibility Boundaries

The implementation targets the standard home-console NTSC, PAL, and Dendy behavior described above. Power-on memory is
initialized deterministically rather than randomized. Revision-selectable hardware glitches and arcade-specific PPU
palette, security, and register variants are outside this contract unless they are added as explicit machine types.

The RGB palette and emphasis attenuation are display approximations. Pixel color IDs, grayscale masking, emphasis-bit
timing, and PAL/Dendy emphasis-bit ordering are emulated; analog composite artifacts and revision-specific color output
are not.

## Regression Testing

Prefer tests that assert scanline/dot position, ordered PPU bus addresses, register return values, and final pixels.
Changes to `$2006/$2007`, NMI timing, rendering enable transitions, sprite evaluation, odd-frame skipping, or mapper
address notification require focused regression tests.
