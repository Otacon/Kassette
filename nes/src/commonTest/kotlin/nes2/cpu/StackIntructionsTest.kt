package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class StackInstructionsTest : FreeSpec({

    "PHA" - {

        "pushes accumulator onto stack" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
                sp = 0xFD,
            )

            memory[0x8000] = 0x48

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            memory[0x01FD] shouldBe 0x42

            state.a shouldBe 0x42
            state.sp shouldBe 0xFC
            state.pc shouldBe 0x8001

            cycles shouldBe 3
        }

        "stack pointer wraps" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
                sp = 0x00,
            )

            memory[0x8000] = 0x48

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            memory[0x0100] shouldBe 0x42
            state.sp shouldBe 0xFF
        }

        "does not modify flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x00,
                sp = 0xFD,
            ).also {
                it.c = true
                it.z = true
                it.i = true
                it.d = true
                it.v = true
                it.n = true
            }

            memory[0x8000] = 0x48

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.z shouldBe true
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true
        }
    }

    "PLA" - {

        "pulls accumulator from stack" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x00,
                sp = 0xFC,
            )

            memory[0x8000] = 0x68
            memory[0x01FD] = 0x42

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x42
            state.sp shouldBe 0xFD

            state.z shouldBe false
            state.n shouldBe false

            state.pc shouldBe 0x8001
            cycles shouldBe 4
        }

        "sets zero flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x68
            memory[0x01FD] = 0x00

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x00
            state.z shouldBe true
            state.n shouldBe false
        }

        "sets negative flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x68
            memory[0x01FD] = 0x80

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
        }
    }

    "PHP" - {

        "pushes status with B and U set" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
            ).also {
                it.c = true
                it.z = true
                it.i = false
                it.d = false
                it.v = true
                it.n = false
            }

            memory[0x8000] = 0x08

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            val pushedStatus = memory[0x01FD]

            // B
            (pushedStatus and 0x10) shouldBe 0x10

            // U
            (pushedStatus and 0x20) shouldBe 0x20

            // Carry
            (pushedStatus and 0x01) shouldBe 0x01

            // Zero
            (pushedStatus and 0x02) shouldBe 0x02

            // Overflow
            (pushedStatus and 0x40) shouldBe 0x40

            state.sp shouldBe 0xFC
            cycles shouldBe 3
        }
    }

    "PLP" - {

        "restores status flags from stack" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x28

            // N V U B D I Z C
            // 1 1 1 0 1 1 0 1
            memory[0x01FD] = 0b1110_1101

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.z shouldBe false
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true

            state.sp shouldBe 0xFD
            state.pc shouldBe 0x8001

            cycles shouldBe 4
        }

        "normalizes B and U bits" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x28

            // Deliberately pull U=0 and B=1.
            memory[0x01FD] = 0x10

            Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            // Internal B is cleared.
            (state.status and 0x10) shouldBe 0

            // Internal U remains set.
            (state.status and 0x20) shouldBe 0x20
        }
    }

    "PHA followed by PLA restores accumulator" {
        val memory = IntArray(0x10_000)
        val state = CpuState(
            pc = 0x8000,
            a = 0xAB,
            sp = 0xFD,
        )

        memory[0x8000] = 0x48 // PHA
        memory[0x8001] = 0x68 // PLA

        val cpu = Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        )

        cpu.step()

        state.sp shouldBe 0xFC
        memory[0x01FD] shouldBe 0xAB

        // Prove PLA actually restores from stack.
        state.a = 0x00

        cpu.step()

        state.a shouldBe 0xAB
        state.sp shouldBe 0xFD
        state.pc shouldBe 0x8002
    }
})