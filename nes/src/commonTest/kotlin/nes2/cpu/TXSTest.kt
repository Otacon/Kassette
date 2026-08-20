package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class TXSTest : FreeSpec({
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



    "copies X into SP" {
        state = CpuState(
            pc = 0x8000,
            x = 0x42,
            sp = 0x00,
        )

        memory[state.pc] = 0x9A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.sp shouldBe 0x42
        state.x shouldBe 0x42

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "copies zero into SP without setting zero flag" {
        state = CpuState(
            pc = 0x8000,
            x = 0x00,
            sp = 0xFF,
            status = 0x20,
        )

        memory[state.pc] = 0x9A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.sp shouldBe 0x00

        // TXS does not affect flags.
        state.z shouldBe false
    }

    "copies value with bit 7 set without setting negative flag" {
        state = CpuState(
            pc = 0x8000,
            x = 0x80,
            status = 0x20,
        )

        memory[state.pc] = 0x9A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.sp shouldBe 0x80
        state.n shouldBe false
    }

    "does not modify any flags" {
        state = CpuState(
            pc = 0x8000,
            x = 0x42,
            status = 0xEF,
        )

        memory[state.pc] = 0x9A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.c shouldBe true
        state.z shouldBe true
        state.i shouldBe true
        state.d shouldBe true
        state.v shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "does not modify X" {
        state = CpuState(
            pc = 0x8000,
            x = 0xAB,
            sp = 0x00,
        )

        memory[state.pc] = 0x9A

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.x shouldBe 0xAB
        state.sp shouldBe 0xAB
    }
})