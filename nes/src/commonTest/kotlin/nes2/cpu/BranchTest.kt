package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class BranchTest : FreeSpec({

    "branch mechanics" - {

        "not taken consumes operand but does not change PC beyond instruction" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
            ).also {
                it.z = true
            }

            // BNE +5, but Z=true so branch is not taken
            memory[0x8000] = 0xD0
            memory[0x8001] = 0x05

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x8002
            cycles shouldBe 2
        }

        "taken branch moves PC forward" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
            ).also {
                it.z = false
            }

            memory[0x8000] = 0xD0 // BNE
            memory[0x8001] = 0x05

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            // PC after operand = $8002
            // $8002 + 5 = $8007
            state.pc shouldBe 0x8007
            cycles shouldBe 3
        }

        "taken branch moves PC backward" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8005,
            ).also {
                it.z = false
            }

            memory[0x8005] = 0xD0 // BNE
            memory[0x8006] = 0xFC // -4

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            // PC after operand = $8007
            // $8007 - 4 = $8003
            state.pc shouldBe 0x8003
            cycles shouldBe 3
        }

        "taken branch crossing page costs extra cycle" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x80FD,
            ).also {
                it.z = false
            }

            memory[0x80FD] = 0xD0 // BNE
            memory[0x80FE] = 0x02

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            // PC after operand = $80FF
            // $80FF + 2 = $8101
            state.pc shouldBe 0x8101
            cycles shouldBe 4
        }

        "backward branch crossing page costs extra cycle" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8100,
            ).also {
                it.z = false
            }

            memory[0x8100] = 0xD0 // BNE
            memory[0x8101] = 0xFC // -4

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            // PC after operand = $8102
            // $8102 - 4 = $80FE
            state.pc shouldBe 0x80FE
            cycles shouldBe 4
        }
    }

    "BCC" - {

        "branches when carry is clear" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000).also {
                it.c = false
            }

            memory[0x8000] = 0x90
            memory[0x8001] = 0x02

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x8004
            cycles shouldBe 3
        }

        "does not branch when carry is set" {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000).also {
                it.c = true
            }

            memory[0x8000] = 0x90
            memory[0x8001] = 0x02

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x8002
            cycles shouldBe 2
        }
    }

    "BCS branches when carry is set" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.c = true
        }

        memory[0x8000] = 0xB0
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }

    "BEQ branches when zero is set" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.z = true
        }

        memory[0x8000] = 0xF0
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }

    "BNE branches when zero is clear" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.z = false
        }

        memory[0x8000] = 0xD0
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }

    "BMI branches when negative is set" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.n = true
        }

        memory[0x8000] = 0x30
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }

    "BPL branches when negative is clear" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.n = false
        }

        memory[0x8000] = 0x10
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }

    "BVC branches when overflow is clear" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.v = false
        }

        memory[0x8000] = 0x50
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }

    "BVS branches when overflow is set" {
        val memory = IntArray(0x10_000)
        val state = CpuState(pc = 0x8000).also {
            it.v = true
        }

        memory[0x8000] = 0x70
        memory[0x8001] = 0x02

        Cpu6502(
            bus = CpuBus(memory = memory),
            state = state,
        ).step()

        state.pc shouldBe 0x8004
    }
})