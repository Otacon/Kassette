package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class NOPTest : FreeSpec({
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



    "does nothing except advance PC" {
        state = CpuState(
            pc = 0x8000,
            a = 0x11,
            x = 0x22,
            y = 0x33,
            sp = 0x44,
            status = 0xEF,
        )

        memory[0x8000] = 0xEA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x11
        state.x shouldBe 0x22
        state.y shouldBe 0x33
        state.sp shouldBe 0x44

        state.c shouldBe true
        state.z shouldBe true
        state.i shouldBe true
        state.d shouldBe true
        state.v shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }

    "implied unofficial NOPs do nothing except advance PC" {
        listOf(0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA).forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, a = 0x11, x = 0x22, y = 0x33, sp = 0x44, status = 0xEF)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode

            val cycles = cpu.step()

            state.a shouldBe 0x11
            state.x shouldBe 0x22
            state.y shouldBe 0x33
            state.sp shouldBe 0x44
            state.status shouldBe 0xEF
            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }
    }

    "immediate unofficial NOPs consume operand" {
        listOf(0x80, 0x82, 0x89, 0xC2, 0xE2).forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, a = 0x11, x = 0x22, y = 0x33, sp = 0x44, status = 0xEF)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode
            memory[0x8001] = 0x42

            val cycles = cpu.step()

            state.a shouldBe 0x11
            state.x shouldBe 0x22
            state.y shouldBe 0x33
            state.sp shouldBe 0x44
            state.status shouldBe 0xEF
            state.pc shouldBe 0x8002
            bus.reads shouldBe listOf(
                FakeBus.Read(0x8000),
                FakeBus.Read(0x8001),
            )
            cycles shouldBe 2
        }
    }

    "zero page unofficial NOPs read operand address" {
        listOf(0x04, 0x44, 0x64).forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, a = 0x11, x = 0x22, y = 0x33, sp = 0x44, status = 0xEF)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode
            memory[0x8001] = 0x20
            memory[0x0020] = 0x42

            val cycles = cpu.step()

            state.a shouldBe 0x11
            state.x shouldBe 0x22
            state.y shouldBe 0x33
            state.sp shouldBe 0x44
            state.status shouldBe 0xEF
            state.pc shouldBe 0x8002
            bus.reads shouldBe listOf(
                FakeBus.Read(0x8000),
                FakeBus.Read(0x8001),
                FakeBus.Read(0x0020),
            )
            cycles shouldBe 3
        }
    }

    "zero page X unofficial NOPs perform indexed dummy read" {
        listOf(0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4).forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, x = 0x10, a = 0x11, y = 0x33, sp = 0x44, status = 0xEF)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode
            memory[0x8001] = 0x20
            memory[0x0030] = 0x42

            val cycles = cpu.step()

            state.a shouldBe 0x11
            state.x shouldBe 0x10
            state.y shouldBe 0x33
            state.sp shouldBe 0x44
            state.status shouldBe 0xEF
            state.pc shouldBe 0x8002
            bus.reads shouldBe listOf(
                FakeBus.Read(0x8000),
                FakeBus.Read(0x8001),
                FakeBus.Read(0x0020),
                FakeBus.Read(0x0030),
            )
            cycles shouldBe 4
        }
    }

    "absolute unofficial NOP reads operand address" {
        memory[0x8000] = 0x0C
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1234] = 0x42
        state = CpuState(pc = 0x8000, a = 0x11, x = 0x22, y = 0x33, sp = 0x44, status = 0xEF)
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        val cycles = cpu.step()

        state.a shouldBe 0x11
        state.x shouldBe 0x22
        state.y shouldBe 0x33
        state.sp shouldBe 0x44
        state.status shouldBe 0xEF
        state.pc shouldBe 0x8003
        bus.reads shouldBe listOf(
            FakeBus.Read(0x8000),
            FakeBus.Read(0x8001),
            FakeBus.Read(0x8002),
            FakeBus.Read(0x1234),
        )
        cycles shouldBe 4
    }

    "absolute X unofficial NOP costs extra cycle when crossing page" {
        listOf(0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC).forEach { opcode ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, x = 0x01, a = 0x11, y = 0x33, sp = 0x44, status = 0xEF)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode
            memory[0x8001] = 0xFF
            memory[0x8002] = 0x12
            memory[0x1300] = 0x42

            val cycles = cpu.step()

            state.a shouldBe 0x11
            state.x shouldBe 0x01
            state.y shouldBe 0x33
            state.sp shouldBe 0x44
            state.status shouldBe 0xEF
            state.pc shouldBe 0x8003
            bus.reads shouldBe listOf(
                FakeBus.Read(0x8000),
                FakeBus.Read(0x8001),
                FakeBus.Read(0x8002),
                FakeBus.Read(0x1200),
                FakeBus.Read(0x1300),
            )
            cycles shouldBe 5
        }
    }
})
