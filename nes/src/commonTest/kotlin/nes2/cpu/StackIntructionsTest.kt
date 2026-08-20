package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class StackInstructionsTest : FreeSpec({
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


    "PHA" - {

        "pushes accumulator onto stack" {
            state = CpuState(
                pc = 0x8000,
                a = 0x42,
                sp = 0xFD,
            )

            memory[0x8000] = 0x48

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            memory[0x01FD] shouldBe 0x42

            state.a shouldBe 0x42
            state.sp shouldBe 0xFC
            state.pc shouldBe 0x8001

            cycles shouldBe 3
        }

        "stack pointer wraps" {
            state = CpuState(
                pc = 0x8000,
                a = 0x42,
                sp = 0x00,
            )

            memory[0x8000] = 0x48

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            cpu.step()

            memory[0x0100] shouldBe 0x42
            state.sp shouldBe 0xFF
        }

        "does not modify flags" {
            state = CpuState(
                pc = 0x8000,
                a = 0x00,
                sp = 0xFD,
                status = 0xEF,
            )

            memory[0x8000] = 0x48

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            cpu.step()

            state.c shouldBe true
            state.z shouldBe true
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true
        }
    }

    "PLA" - {

        "pulls accumulator from stack" {
            state = CpuState(
                pc = 0x8000,
                a = 0x00,
                sp = 0xFC,
            )

            memory[0x8000] = 0x68
            memory[0x01FD] = 0x42

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            state.a shouldBe 0x42
            state.sp shouldBe 0xFD

            state.z shouldBe false
            state.n shouldBe false

            state.pc shouldBe 0x8001
            cycles shouldBe 4
        }

        "sets zero flag" {
            state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x68
            memory[0x01FD] = 0x00

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            cpu.step()

            state.a shouldBe 0x00
            state.z shouldBe true
            state.n shouldBe false
        }

        "sets negative flag" {
            state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x68
            memory[0x01FD] = 0x80

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            cpu.step()

            state.a shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
        }
    }

    "PHP" - {

        "pushes status with B and U set" {
            state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
                status = 0x63,
            )

            memory[0x8000] = 0x08

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            val pushedStatus = memory[0x01FD]

            // B
            (pushedStatus and 0x10) shouldBe 0x10

            // U
            (pushedStatus and 0x20) shouldBe 0x20

            // Carry
            (pushedStatus and 0x01) shouldBe 0x01

            // Zero
            (pushedStatus and 0x02) shouldBe 0x02

            // Overflow
            (pushedStatus and 0x40) shouldBe 0x40

            state.sp shouldBe 0xFC
            cycles shouldBe 3
        }
    }

    "PLP" - {

        "restores status flags from stack" {
            state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x28

            // N V U B D I Z C
            // 1 1 1 0 1 1 0 1
            memory[0x01FD] = 0b1110_1101

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            val cycles = cpu.step()

            state.c shouldBe true
            state.z shouldBe false
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true

            state.sp shouldBe 0xFD
            state.pc shouldBe 0x8001

            cycles shouldBe 4
        }

        "normalizes B and U bits" {
            state = CpuState(
                pc = 0x8000,
                sp = 0xFC,
            )

            memory[0x8000] = 0x28

            // Deliberately pull U=0 and B=1.
            memory[0x01FD] = 0x10

            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)
            cpu.step()

            // Internal B is cleared.
            (state.status and 0x10) shouldBe 0

            // Internal U remains set.
            (state.status and 0x20) shouldBe 0x20
        }
    }

    "PLP clearing I delays IRQ by one instruction" {

        state = CpuState(
            pc = 0x8000,
            sp = 0xFC,
            irqLine = true,
            irqPollI = true,
            status = 0x24,
        )

        memory[0x8000] = 0x28 // PLP
        memory[0x8001] = 0xEA // NOP

        // Pull status with I clear.
        memory[0x01FD] = 0x20

        memory[0xFFFE] = 0x00
        memory[0xFFFF] = 0x90

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.step() shouldBe 4

        state.i shouldBe false

        // One instruction still executes.
        cpu.step() shouldBe 2
        state.pc shouldBe 0x8002

        cpu.step() shouldBe 7
        state.pc shouldBe 0x9000
    }

    "PHA followed by PLA restores accumulator" {
        state = CpuState(
            pc = 0x8000,
            a = 0xAB,
            sp = 0xFD,
        )

        memory[0x8000] = 0x48 // PHA
        memory[0x8001] = 0x68 // PLA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.step()

        state.sp shouldBe 0xFC
        memory[0x01FD] shouldBe 0xAB

        // Prove PLA actually restores from stack.
        state.a = 0x00

        cpu.step()

        state.a shouldBe 0xAB
        state.sp shouldBe 0xFD
        state.pc shouldBe 0x8002
    }
})