package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class STYTest : FreeSpec({

    "STY" - {

        "zero page" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x42,
            )

            memory[state.pc] = 0x84
            memory[state.pc + 1] = 0x20

            val initialC = state.c
            val initialZ = state.z
            val initialV = state.v
            val initialN = state.n

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x0020] shouldBe 0x42

            state.y shouldBe 0x42
            state.c shouldBe initialC
            state.z shouldBe initialZ
            state.v shouldBe initialV
            state.n shouldBe initialN

            state.pc shouldBe 0x8002
            cycles shouldBe 3
        }

        "zero page X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x10,
                y = 0x42,
            )

            memory[state.pc] = 0x94
            memory[state.pc + 1] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            // $20 + X($10) = $30
            memory[0x0030] shouldBe 0x42

            state.y shouldBe 0x42
            state.pc shouldBe 0x8002
            cycles shouldBe 4
        }

        "zero page X wraps around zero page" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x02,
                y = 0x7A,
            )

            memory[state.pc] = 0x94
            memory[state.pc + 1] = 0xFF

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            // $FF + X($02) = $01
            memory[0x0001] shouldBe 0x7A

            state.pc shouldBe 0x8002
            cycles shouldBe 4
        }

        "absolute" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0xAB,
            )

            memory[state.pc] = 0x8C
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x1234] shouldBe 0xAB

            state.y shouldBe 0xAB
            state.pc shouldBe 0x8003
            cycles shouldBe 4
        }

        "does not modify flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                y = 0x80,
            ).also {
                it.c = true
                it.z = true
                it.v = true
                it.n = true
            }

            memory[state.pc] = 0x84
            memory[state.pc + 1] = 0x20

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.z shouldBe true
            state.v shouldBe true
            state.n shouldBe true
        }
    }
})