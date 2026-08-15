# NES CPU Behavior Reference

This document is the implementation contract for the Ricoh 2A03/2A07 CPU core. Keep CPU, bus, interrupt, and DMA
changes consistent with these rules and protect timing-sensitive behavior with cycle-level tests.

## Execution Model

The CPU executes the NMOS 6502 instruction set used by the NES, including the stable unofficial opcodes implemented by
commercial software and test ROMs. Every opcode fetch, operand read, dummy read, write, and dummy write is observable on
the CPU bus and consumes one CPU cycle unless DMA extends that read.

The status register is represented internally with the unused bit set and the break bit clear. The break bit is not a
physical latch: BRK and PHP set it only in the stack copy, while hardware interrupts clear it in the stack copy. PLP and
RTI ignore pulled break and unused bits.

The decimal flag can be set and cleared, but ADC and SBC always use binary arithmetic because the NES CPU disables
decimal correction.

## Addressing And Bus Cycles

* Instruction operands and vectors are little-endian.
* Zero-page indexed and zero-page indirect pointer accesses wrap within page zero.
* Indexed reads perform a dummy read only when the effective address crosses a page.
* Indexed writes always perform the preliminary dummy read.
* Memory read-modify-write instructions read the old value, write the old value as a dummy write, then write the result.
* Indirect JMP wraps its high-byte lookup within the pointer's page when the pointer ends in `$FF`.
* A taken branch performs one dummy read. A taken branch crossing a page performs another read from the partially
  corrected address.

## Stack And Control Flow

The stack occupies `$0100-$01FF`, grows downward, and wraps through its eight-bit stack pointer. Push writes before
decrementing SP; pull increments SP before reading.

JSR pushes the address of its final operand byte. RTS pulls that address and increments it after the final dummy read.
BRK performs a dummy read of its padding byte and pushes the address after that byte. RTI restores status and PC without
incrementing PC.

## Interrupt Polling

NMI is edge-detected during the second half of a CPU cycle. The detected edge becomes eligible on the following cycle,
so an edge first observed on an instruction's final cycle does not interrupt before the next instruction.

IRQ is level-sensitive and masked by I. Instruction-boundary recognition uses the level sampled near the end of the
second-to-last instruction cycle. CLI, SEI, and PLP/RTI therefore exhibit delayed IRQ effects. Taken branches preserve
the offset-fetch poll; page-crossing branches combine the relevant branch polls rather than replacing the earlier one.

NMI has priority over IRQ and can hijack IRQ or BRK entry before vector selection. An NMI detected after BRK has selected
its vector must not prevent the first handler instruction from executing.

Interrupt entry performs two discarded PC reads, pushes PC high then low, pushes status, sets I, and reads the vector.
The vectors are `$FFFA` for NMI, `$FFFC` for reset, and `$FFFE` for IRQ/BRK.

## Reset

Reset reads the reset vector without clocking mapped devices, initializes power-on registers when requested, and clocks
eight startup cycles before opcode execution. Soft reset preserves A, X, and Y, decrements SP by three, and sets I.

## DMA

DMA starts only when the CPU attempts an eligible read; writes delay the halt. PAL DMC DMA waits for an opcode-fetch
read. The halt cycle repeats the CPU's pending read address.

OAM DMA performs a halt cycle, an optional parity-alignment cycle, and 256 alternating source reads and `$2004` writes.
Its writes use DMA/read-cycle phase timing even though the memory operation is a write.

DMC DMA performs halt and dummy cycles before its sample get cycle. Depending on alignment it occupies three or four
cycles. DMC and OAM DMA arbitrate cycle by cycle: a ready DMC get steals an OAM get slot and extends OAM DMA rather than
waiting until the complete OAM transfer finishes.

DMA reads preserve open-bus behavior. If a CPU read from `$4000-$401F` is halted, internal APU/controller register reads
can overlap DMA's external read; changes in this area require dedicated tests for `$4015-$4017` side effects.

## Unofficial Opcodes

SLO, RLA, SRE, RRA, DCP, and ISB use the normal read-modify-write bus sequence before their combined accumulator
operation. SAX stores `A and X`; LAX loads both A and X; LAS loads `memory and SP` into A, X, and SP.

TAS sets `SP = A and X` before applying unstable-store behavior. AHX, SHX, SHY, and TAS mask their stored value with the
base-address high byte plus one. On a page crossing, the register value also corrupts the destination high byte. If DMA
extends their preliminary dummy read, the written value is not high-byte-masked.

KIL repeatedly fetches itself and ignores IRQ/NMI until reset.

## Regression Testing

Prefer tests that assert both final state and the ordered `CpuBus.Cycle` trace. Timing tests should control the interrupt
line or DMA request from a cycle listener/phase listener so they identify the exact cycle being exercised. Any change to
interrupt polling, branch timing, DMA arbitration, unstable stores, or dummy access classification requires a focused
regression test.
