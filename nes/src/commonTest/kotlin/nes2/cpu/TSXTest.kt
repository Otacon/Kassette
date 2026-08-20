package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class TSXTest : FreeSpec({
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



    "copies SP into X" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x42,
            x = 0x00,
        )

        memory[state.pc] = 0xBA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.x shouldBe 0x42
        state.sp shouldBe 0x42

        state.z shouldBe false
        state.n shouldBe false

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "sets zero flag when SP is zero" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x00,
            x = 0xFF,
        )

        memory[state.pc] = 0xBA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.x shouldBe 0x00
        state.z shouldBe true
        state.n shouldBe false
    }

    "sets negative flag when bit 7 is set" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x80,
        )

        memory[state.pc] = 0xBA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.x shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
    }

    "clears zero and negative flags" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x42,
            status = 0xA2,
        )

        memory[state.pc] = 0xBA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.x shouldBe 0x42
        state.z shouldBe false
        state.n shouldBe false
    }

    "does not modify unrelated flags" {
        state = CpuState(
            pc = 0x8000,
            sp = 0x42,
            status = 0x6D,
        )

        memory[state.pc] = 0xBA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.c shouldBe true
        state.v shouldBe true
        state.i shouldBe true
        state.d shouldBe true
    }

    "does not modify SP" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xAB,
        )

        memory[state.pc] = 0xBA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        cpu.step()

        state.sp shouldBe 0xAB
        state.x shouldBe 0xAB
    }
})