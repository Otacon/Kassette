package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class BranchTest : FreeSpec({
    lateinit var memory: IntArray
    lateinit var state: CpuState
    lateinit var bus: FakeBus
    lateinit var cpu: Cpu6502

    beforeTest {
        memory = IntArray(0x10_000)
        state = CpuState()
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
    }


    "branch mechanics" - {

        "not taken consumes operand but does not change PC beyond instruction" {
            state = CpuState(
                pc = 0x8000,
                status = 0x22,
            )

            // BNE +5, but Z=true so branch is not taken
            memory[0x8000] = 0xD0
            memory[0x8001] = 0x05

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            state.pc shouldBe 0x8002
            cycles shouldBe 2
        }

        "taken branch moves PC forward" {
            state = CpuState(
                pc = 0x8000,
                status = 0x20,
            )

            memory[0x8000] = 0xD0 // BNE
            memory[0x8001] = 0x05

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            // PC after operand = $8002
            // $8002 + 5 = $8007
            state.pc shouldBe 0x8007
            cycles shouldBe 3
        }

        "taken branch moves PC backward" {
            state = CpuState(
                pc = 0x8005,
                status = 0x20,
            )

            memory[0x8005] = 0xD0 // BNE
            memory[0x8006] = 0xFC // -4

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            // PC after operand = $8007
            // $8007 - 4 = $8003
            state.pc shouldBe 0x8003
            cycles shouldBe 3
        }

        "taken branch crossing page costs extra cycle" {
            state = CpuState(
                pc = 0x80FD,
                status = 0x20,
            )

            memory[0x80FD] = 0xD0 // BNE
            memory[0x80FE] = 0x02

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            // PC after operand = $80FF
            // $80FF + 2 = $8101
            state.pc shouldBe 0x8101
            cycles shouldBe 4
        }

        "backward branch crossing page costs extra cycle" {
            state = CpuState(
                pc = 0x8100,
                status = 0x20,
            )

            memory[0x8100] = 0xD0 // BNE
            memory[0x8101] = 0xFC // -4

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            // PC after operand = $8102
            // $8102 - 4 = $80FE
            state.pc shouldBe 0x80FE
            cycles shouldBe 4
        }
    }

    "BCC branches when carry is clear" {
        state = CpuState(pc = 0x8000, status = 0x20)

        memory[0x8000] = 0x90
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x8004
        cycles shouldBe 3
    }

    "BCC does not branch when carry is set" {
        state = CpuState(pc = 0x8000, status = 0x21)

        memory[0x8000] = 0x90
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.pc shouldBe 0x8002
        cycles shouldBe 2
    }

    "BCS branches when carry is set" {
        state = CpuState(pc = 0x8000, status = 0x21)

        memory[0x8000] = 0xB0
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }

    "BEQ branches when zero is set" {
        state = CpuState(pc = 0x8000, status = 0x22)

        memory[0x8000] = 0xF0
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }

    "BNE branches when zero is clear" {
        state = CpuState(pc = 0x8000, status = 0x20)

        memory[0x8000] = 0xD0
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }

    "BMI branches when negative is set" {
        state = CpuState(pc = 0x8000, status = 0xA0)

        memory[0x8000] = 0x30
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }

    "BPL branches when negative is clear" {
        state = CpuState(pc = 0x8000, status = 0x20)

        memory[0x8000] = 0x10
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }

    "BVC branches when overflow is clear" {
        state = CpuState(pc = 0x8000, status = 0x20)

        memory[0x8000] = 0x50
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }

    "BVS branches when overflow is set" {
        state = CpuState(pc = 0x8000, status = 0x60)

        memory[0x8000] = 0x70
        memory[0x8001] = 0x02

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.pc shouldBe 0x8004
    }
})
