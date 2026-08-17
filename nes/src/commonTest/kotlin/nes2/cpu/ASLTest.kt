package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.FakeBus

class ASLTest : FreeSpec({

    "ASL" - {

        "accumulator shifts left" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x21,
            )

            memory[state.pc] = 0x0A

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x42
            state.c shouldBe false
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "moves bit 7 into carry" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x80,
            )

            memory[state.pc] = 0x0A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x00
            state.c shouldBe true
            state.z shouldBe true
            state.n shouldBe false
        }

        "sets negative from result" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x40,
            )

            memory[state.pc] = 0x0A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x80
            state.c shouldBe false
            state.n shouldBe true
        }

        "zero page" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000)

            memory[state.pc] = 0x06
            memory[state.pc + 1] = 0x20
            memory[0x0020] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x0020] shouldBe 0x42
            state.pc shouldBe 0x8002
            cycles shouldBe 5
        }

        "zero page X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000, x = 0x10)

            memory[state.pc] = 0x16
            memory[state.pc + 1] = 0x20
            memory[0x0030] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x0030] shouldBe 0x42
            cycles shouldBe 6
        }

        "absolute" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000)

            memory[state.pc] = 0x0E
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1234] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x1234] shouldBe 0x42
            cycles shouldBe 6
        }

        "absolute X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000, x = 0x01)

            memory[state.pc] = 0x1E
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1235] = 0x21

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x1235] shouldBe 0x42
            cycles shouldBe 7
        }
    }
})