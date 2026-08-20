package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class Cpu6502Test : FreeSpec({
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

    "reset loads PC from reset vector" {
        memory[0xFFFC] = 0x34
        memory[0xFFFD] = 0x12

        state.pc = 0x8000
        state.sp = 0x10
        state.status = 0x00

        val cycles = cpu.reset()

        state.pc shouldBe 0x1234
        cycles shouldBe 7
    }

    "reset initializes stack pointer and status" {
        state.pc = 0x8000
        state.sp = 0x10
        state.status = 0x00

        cpu.reset()

        state.sp shouldBe 0xFD
        state.status shouldBe 0x24
        state.i shouldBe true
    }

    "soft reset preserves registers and reloads PC from reset vector" {
        memory[0xFFFC] = 0x78
        memory[0xFFFD] = 0x56

        state.pc = 0x8000
        state.a = 0x12
        state.x = 0x34
        state.y = 0x56
        state.sp = 0x80
        state.status = 0xC9
        state.irqLine = true
        state.nmiPending = true
        state.irqPollI = false

        val cycles = cpu.softReset()

        state.pc shouldBe 0x5678
        state.a shouldBe 0x12
        state.x shouldBe 0x34
        state.y shouldBe 0x56
        cycles shouldBe 7
    }

    "soft reset decrements stack pointer by three and sets interrupt disable" {
        state.sp = 0x01
        state.status = 0xE9

        cpu.softReset()

        state.sp shouldBe 0xFE
        state.status shouldBe 0xED
        state.i shouldBe true
    }
    "reset clears CPU halt" {
        state.pc = 0x8000
        memory[0x8000] = 0x02
        memory[0xFFFC] = 0x34
        memory[0xFFFD] = 0x12

        cpu.step()
        cpu.reset()

        state.halted shouldBe false
        state.pc shouldBe 0x1234
    }

    "soft reset clears CPU halt" {
        state.pc = 0x8000
        memory[0x8000] = 0x02
        memory[0xFFFC] = 0x78
        memory[0xFFFD] = 0x56

        cpu.step()
        cpu.softReset()

        state.halted shouldBe false
        state.pc shouldBe 0x5678
    }
})
