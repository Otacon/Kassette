package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class RTITest : FreeSpec({

    "RTI" - {

        "restores processor status and program counter" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x9000,
                sp = 0xFA,
            )

            memory[0x9000] = 0x40

            // RTI pulls status first.
            memory[0x01FB] = 0b1110_1101

            // Then PC low/high.
            memory[0x01FC] = 0x34
            memory[0x01FD] = 0x12

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x1234

            state.c shouldBe true
            state.z shouldBe false
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true

            state.sp shouldBe 0xFD

            cycles shouldBe 6
        }

        "does not increment restored PC like RTS" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x9000,
                sp = 0xFA,
            )

            memory[0x9000] = 0x40

            memory[0x01FB] = 0x20
            memory[0x01FC] = 0xFF
            memory[0x01FD] = 0x7F

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x7FFF
        }

        "normalizes B and U bits when restoring status" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x9000,
                sp = 0xFA,
            )

            memory[0x9000] = 0x40

            // Deliberately B=1 and U=0.
            memory[0x01FB] = 0x10

            memory[0x01FC] = 0x34
            memory[0x01FD] = 0x12

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            (state.status and 0x10) shouldBe 0
            (state.status and 0x20) shouldBe 0x20
        }

        "stack pointer wraps while pulling" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x9000,
                sp = 0xFE,
            )

            memory[0x9000] = 0x40

            // First pull -> $01FF
            memory[0x01FF] = 0x20

            // Second pull wraps -> $0100
            memory[0x0100] = 0x34

            // Third pull -> $0101
            memory[0x0101] = 0x12

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x1234
            state.sp shouldBe 0x01
        }
    }

    "BRK followed by RTI returns after BRK" {
        val memory = IntArray(0x10_000)
        val state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        // BRK
        memory[0x8000] = 0x00

        // BRK's unused/signature byte.
        memory[0x8001] = 0xEA

        // IRQ/BRK vector -> $9000
        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        // Interrupt handler: RTI
        memory[0x9000] = 0x40

        val cpu = Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        )

        cpu.step() shouldBe 7

        state.pc shouldBe 0x9000
        state.sp shouldBe 0xFA

        cpu.step() shouldBe 6

        // BRK returns after its second byte.
        state.pc shouldBe 0x8002
        state.sp shouldBe 0xFD
    }

    "RTI clearing I allows IRQ immediately" {
        val memory = IntArray(0x10_000)

        val state = CpuState(
            pc = 0x9000,
            sp = 0xFA,
            irqLine = true,
            irqPollI = true,
        ).also {
            it.i = true
        }

        memory[0x9000] = 0x40 // RTI

        // Restore status with I clear.
        memory[0x01FB] = 0x20

        // Return to $8000.
        memory[0x01FC] = 0x00
        memory[0x01FD] = 0x80

        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0xA0

        val cpu = Cpu6502(
            bus = CpuBus(memory),
            state = state,
        )

        cpu.step() shouldBe 6
        state.pc shouldBe 0x8000
        state.i shouldBe false

        // No normal instruction at $8000 executes.
        cpu.step() shouldBe 7
        state.pc shouldBe 0xA000
    }
})