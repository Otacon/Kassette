package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class IRQTest : FreeSpec({
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



    "jumps to IRQ vector" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
            status = 0x20,
        )

        memory[0x8000] = 0xEA
        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)

        val cycles = cpu.step()

        state.pc shouldBe 0x1234
        cycles shouldBe 7
    }

    "services IRQ instead of executing next opcode" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
            status = 0x20,
        )

        memory[0x8000] = 0xEA
        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)

        cpu.step() shouldBe 7

        state.pc shouldBe 0x9000
        memory[0x01FC] shouldBe 0x00
    }

    "CLI delays IRQ until after the following instruction" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = true,
            status = 0x24,
        )

        memory[0x8000] = 0x58 // CLI
        memory[0x8001] = 0xEA // NOP
        memory[0x8002] = 0xEA // Would execute if IRQ were delayed too long.
        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)

        cpu.step() shouldBe 2
        state.pc shouldBe 0x8001

        cpu.step() shouldBe 2
        state.pc shouldBe 0x8002

        cpu.step() shouldBe 7
        state.pc shouldBe 0x9000
    }

    "SEI prevents IRQ on the following instruction" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
            status = 0x20,
        )

        memory[0x8000] = 0x78 // SEI
        memory[0x8001] = 0xEA // NOP
        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.step() shouldBe 2

        cpu.setIrqLine(true)

        cpu.step() shouldBe 2
        state.pc shouldBe 0x8002
    }

    "pushes current PC" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
            status = 0x20,
        )

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)
        cpu.step()

        memory[0x01FD] shouldBe 0x80
        memory[0x01FC] shouldBe 0x00

        state.sp shouldBe 0xFA
    }

    "pushes status with B clear and U set" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            irqPollI = false,
            status = 0x61,
        )

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)
        cpu.step()

        val pushedStatus = memory[0x01FB]

        (pushedStatus and 0x10) shouldBe 0
        (pushedStatus and 0x20) shouldBe 0x20
        (pushedStatus and 0x01) shouldBe 0x01
        (pushedStatus and 0x40) shouldBe 0x40
    }

    "sets interrupt disable flag" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x20,
        )

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)
        cpu.step()

        state.i shouldBe true
    }

    "does not service IRQ when interrupt disable is set" {
        state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
            status = 0x24,
        )

        // NOP
        memory[0x8000] = 0xEA

        memory[0xFFFE] = 0x34
        memory[0xFFFF] = 0x12

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.setIrqLine(true)

        val cycles = cpu.step()

        // NOP executed instead.
        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }
})
