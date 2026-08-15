# NES APU Behavior Reference

This document is the implementation contract for the NES audio processing unit. Register effects, channel timers,
frame sequencing, DMA, interrupts, reset, and savestate changes should remain consistent with these rules and should be
protected with CPU-cycle-level tests.

## Clocking And Frame Counter

The APU advances once per CPU cycle. Triangle, noise, and DMC timers use CPU cycles; pulse timers advance on alternating
APU cycles. Frame-counter work occurs before pending length reloads and channel timer work for the same CPU cycle.

Four-step NTSC and Dendy events occur at cycles 7457, 14913, 22371, 29828, 29829, and 29830. PAL uses 8313, 16627,
24939, 33252, 33253, and 33254. The first three events clock quarter, half, and quarter units. The fourth asserts the
frame flag, the fifth clocks quarter and half units and asserts the flag, and the sixth asserts the flag and ends the
sequence.

Five-step NTSC and Dendy events occur at 7457, 14913, 22371, 29829, 37281, and 37282. PAL uses 8313, 16627, 24939,
33253, 41565, and 41566. The fourth event is idle, the fifth clocks quarter and half units, and the sixth ends the
sequence. Five-step mode does not generate a frame IRQ.

A `$4017` write changes IRQ inhibit immediately but applies its mode and sequence reset after three or four CPU cycles,
depending on APU parity. Applying five-step mode clocks both quarter- and half-frame units unless a nearby sequencer tick
blocks the duplicate clock.

## Status And Interrupts

`$4015` bits 0-3 report nonzero length counters, bit 4 reports DMC bytes remaining, bit 5 preserves CPU open bus, bit 6
reports the frame-counter flag, and bit 7 reports DMC IRQ. A normal read clears the frame IRQ line and starts delayed
clearing of the frame flag. It does not clear DMC IRQ.

Any `$4015` write clears DMC IRQ. Disabling a tonal or noise channel clears its length counter. Clearing DMC IRQ enable
through `$4010` also clears DMC IRQ. Write-only APU register reads preserve CPU open bus.

## Envelopes And Length Counters

Quarter-frame clocks update pulse and noise envelopes and the triangle linear counter. Half-frame clocks additionally
update all length counters and both pulse sweep units.

Envelope restart loads decay 15 and the divider period on the next quarter-frame clock. Divider periods are inclusive;
looping envelopes wrap decay from zero to 15. Constant-volume mode selects the register volume without stopping envelope
state progression. Envelope wrapping observes the active loop/halt state, so a loop-bit write coincident with a
quarter-frame clock takes effect only after that clock.

Length loads and halt changes are pending until frame-counter processing for the following APU cycle completes. If a
half-frame clock changes a counter on that cycle, its pending reload is suppressed.

## Pulse Channels

Pulse timers produce one duty step every `2 * (period + 1)` CPU cycles. High-period writes reset duty phase and restart
the envelope without reloading the active timer countdown. Periods below 8 are muted.

Sweep divider period is the encoded value plus one. Positive sweep adds `period >> shift`; negative sweep subtracts it,
with pulse 1 subtracting one additional unit. Positive targets above `$7FF` mute output even when sweep is disabled.

## Triangle Channel

The triangle sequencer advances through `15..0, 0..15` when both length and linear counters are nonzero. Closing either
gate holds the current DAC value rather than forcing zero. The sequencer still advances at timer periods below 2; analog
ultrasonic suppression is not part of the base hardware contract.

## Noise Channel

The 15-bit noise shift register starts at 1. Feedback is bit 0 XOR bit 1 in normal mode and bit 0 XOR bit 6 in short
mode, inserted at bit 14 after shifting right. A set low bit mutes channel output. NTSC/Dendy and PAL use their respective
hardware period tables. The timer countdown starts at zero, so reset is followed by an immediate first LFSR clock.
Writing `$400F` does not reset the shift register.

## DMC Channel And DMA

`$4012` programs `$C000 | (value << 6)` and `$4013` programs `(value << 4) | 1` bytes. Address increment wraps from
`$FFFF` to `$8000`. Sample bits play least-significant first and move the seven-bit DAC by two without wrapping.
`$4011` changes the DAC immediately.

Enabling an inactive sample initializes its address and length immediately, then delays the first DMA request by two or
three CPU cycles according to parity. Disabling similarly delays termination, cancels before halt without a stall, and
can consume only the halt stall before aborting the remaining transfer. DMA uses halt, dummy, and get phases and
arbitrates with OAM DMA. PAL starts DMC DMA only on opcode-fetch
reads; NTSC and Dendy allow eligible CPU reads.

The final fetched byte restarts a looping sample or raises DMC IRQ when enabled. Buffered data continues through the
output unit after the reader is disabled.

## Mixing And Output

The two pulses use the NES nonlinear pulse mixer. Triangle, noise, and DMC use the nonlinear TND mixer. The resulting
mono stream is sampled at 44.1 kHz and passed through fixed high-pass and low-pass filters. This approximates consumer
audio output; expansion audio, analog component tolerances, and configurable post-processing are outside this contract.

## Reset And State

Power reset initializes noise shift state to 1 and initializes noise and DMC rate index zero for the active region. Soft
reset preserves frame mode, programmed DMC sample address and length, and triangle length-counter state where hardware
does, while resetting channel playback state.

Captured state includes frame-write delays, frame-flag clearing, pending length operations, channel timers and units,
DMC enable/disable delays, and the CPU-side DMC DMA pipeline. Restoring state must copy mutable arrays so advancing the
machine cannot mutate the caller's snapshot.
