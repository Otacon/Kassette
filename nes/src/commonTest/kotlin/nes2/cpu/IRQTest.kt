package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class IRQTest : FreeSpec({

    "IRQ" - {

        "jumps to IRQ vector" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
            ).also {
                it.i = false
            }

            memory[0xFFFE] = 0x34
            memory[0xFFFF] = 0x12

            val cpu = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            )

            cpu.setIrqLine(true)

            val cycles = cpu.step()

            state.pc shouldBe 0x1234
            cycles shouldBe 7
        }

        "pushes current PC" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
                irqPollI = false,
            ).also {
                it.i = false
            }

            memory[0xFFFE] = 0x34
            memory[0xFFFF] = 0x12

            val cpu = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            )

            cpu.setIrqLine(true)
            cpu.step()

            memory[0x01FD] shouldBe 0x80
            memory[0x01FC] shouldBe 0x00

            state.sp shouldBe 0xFA
        }

        "pushes status with B clear and U set" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
                irqPollI = false,
            ).also {
                it.i = false
                it.c = true
                it.v = true
            }

            memory[0xFFFE] = 0x34
            memory[0xFFFF] = 0x12

            val cpu = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            )

            cpu.setIrqLine(true)
            cpu.step()

            val pushedStatus = memory[0x01FB]

            (pushedStatus and 0x10) shouldBe 0
            (pushedStatus and 0x20) shouldBe 0x20
            (pushedStatus and 0x01) shouldBe 0x01
            (pushedStatus and 0x40) shouldBe 0x40
        }

        "sets interrupt disable flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
            ).also {
                it.i = false
            }

            memory[0xFFFE] = 0x34
            memory[0xFFFF] = 0x12

            val cpu = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            )

            cpu.setIrqLine(true)
            cpu.step()

            state.i shouldBe true
        }

        "does not service IRQ when interrupt disable is set" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
            ).also {
                it.i = true
            }

            // NOP
            memory[0x8000] = 0xEA

            memory[0xFFFE] = 0x34
            memory[0xFFFF] = 0x12

            val cpu = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            )

            cpu.setIrqLine(true)

            val cycles = cpu.step()

            // NOP executed instead.
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }
    }
})