package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class KILTest : FreeSpec({
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

    "halts CPU at the opcode address" {
        state.pc = 0x8000
        memory[0x8000] = 0x02

        val cycles = cpu.step()

        state.halted shouldBe true
        state.pc shouldBe 0x8000
        cycles shouldBe 1
    }

    "all KIL opcodes halt CPU" {
        val opcodes = listOf(
            0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72,
            0x92, 0xB2, 0xD2, 0xF2,
        )

        opcodes.forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode

            cpu.step() shouldBe 1

            state.halted shouldBe true
            state.pc shouldBe 0x8000
        }
    }

    "halted CPU does not advance PC" {
        state.pc = 0x8000
        memory[0x8000] = 0x02

        cpu.step()
        val cycles = cpu.step()

        state.halted shouldBe true
        state.pc shouldBe 0x8000
        cycles shouldBe 1
    }

    "halted CPU ignores pending interrupts" {
        state.pc = 0x8000
        memory[0x8000] = 0x02
        memory[0xFFFA] = 0x00
        memory[0xFFFB] = 0x90
        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0xA0

        cpu.step()
        cpu.requestNmi()
        cpu.setIrqLine(true)

        cpu.step()

        state.halted shouldBe true
        state.pc shouldBe 0x8000
    }
})
