package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class LDATest : FreeSpec({
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

    val cases = listOf(
        CpuInstructionCase(
            name = "loads value into accumulator",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x42,
            expectedA = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets zero when loaded value is zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ),
            value = 0x00,
            expectedA = 0x00,
            expectedZ = true,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "clears zero when loaded value is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
                status = 0x22,
            ),
            value = 0x01,
            expectedA = 0x01,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets negative when bit 7 is set",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x80,
            expectedA = 0x80,
            expectedZ = false,
            expectedN = true,
        ),
        CpuInstructionCase(
            name = "clears negative when bit 7 is not set",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
                status = 0xA0,
            ),
            value = 0x7F,
            expectedA = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
                status = 0x61,
            ),
            value = 0x42,
            expectedA = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
    )

    fun FreeSpecRootScope.testLdaMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: CpuInstructionCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    memory = IntArray(0x10_000)
                    state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialCarry = state.c
                    val initialOverflow = state.v
                    val initialPc = state.pc

                    bus = FakeBus(memory = memory)
                    cpu = Cpu6502(bus = bus, state = state)

                    val cycles = cpu.step()

                    state.a shouldBe case.expectedA
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    // LDA must leave C and V unchanged.
                    state.c shouldBe initialCarry
                    state.v shouldBe initialOverflow

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }


    testLdaMode(
        name = "immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0xA9
        memory[state.pc + 1] = case.value
    }

    testLdaMode(
        name = "zero page",
        instructionSize = 2,
        expectedCycles = 3,
    ) { memory, state, case ->
        memory[state.pc] = 0xA5
        memory[state.pc + 1] = 0x42

        memory[0x0042] = case.value
    }

    testLdaMode(
        name = "zero page X",
        instructionSize = 2,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x10

        memory[state.pc] = 0xB5
        memory[state.pc + 1] = 0x40

        memory[0x0050] = case.value
    }

    "zero page X performs dummy read before indexed read" {
        state = CpuState(pc = 0x8000, x = 0x10)

        memory[state.pc] = 0xB5
        memory[state.pc + 1] = 0x40
        memory[0x0050] = 0x42

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.step() shouldBe 4

        state.a shouldBe 0x42
        bus.reads shouldBe listOf(
            FakeBus.Read(0x8000),
            FakeBus.Read(0x8001),
            FakeBus.Read(0x0040),
            FakeBus.Read(0x0050),
        )
    }

    testLdaMode(
        name = "absolute",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        memory[state.pc] = 0xAD
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1234] = case.value
    }

    testLdaMode(
        name = "absolute X",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x01

        memory[state.pc] = 0xBD
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1235] = case.value
    }

    "absolute X with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            x = 0x01,
        )

        memory[state.pc] = 0xBD
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        memory[0x1300] = 0x80

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
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

    testLdaMode(
        name = "absolute Y",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0xB9
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1235] = case.value
    }

    "absolute Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            y = 0x01,
        )

        memory[state.pc] = 0xB9
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        memory[0x1300] = 0x80

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
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

    testLdaMode(
        name = "indirect X",
        instructionSize = 2,
        expectedCycles = 6,
    ) { memory, state, case ->
        state.x = 0x04

        memory[state.pc] = 0xA1
        memory[state.pc + 1] = 0x20

        // ($20 + X) = $24
        // Pointer at $24/$25 -> $1234
        memory[0x0024] = 0x34
        memory[0x0025] = 0x12

        memory[0x1234] = case.value
    }

    "indirect X performs dummy read before indexed pointer reads" {
        state = CpuState(pc = 0x8000, x = 0x04)

        memory[state.pc] = 0xA1
        memory[state.pc + 1] = 0x20
        memory[0x0024] = 0x34
        memory[0x0025] = 0x12
        memory[0x1234] = 0x42

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)

        cpu.step() shouldBe 6

        state.a shouldBe 0x42
        bus.reads shouldBe listOf(
            FakeBus.Read(0x8000),
            FakeBus.Read(0x8001),
            FakeBus.Read(0x0020),
            FakeBus.Read(0x0024),
            FakeBus.Read(0x0025),
            FakeBus.Read(0x1234),
        )
    }

    testLdaMode(
        name = "indirect Y",
        instructionSize = 2,
        expectedCycles = 5,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0xB1
        memory[state.pc + 1] = 0x20

        // Pointer at $20/$21 -> $1234
        memory[0x0020] = 0x34
        memory[0x0021] = 0x12

        // $1234 + Y($01) = $1235
        memory[0x1235] = case.value
    }

    "indirect Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            y = 0x01,
        )

        memory[state.pc] = 0xB1
        memory[state.pc + 1] = 0x20

        // Pointer at $20/$21 -> $12FF
        memory[0x0020] = 0xFF
        memory[0x0021] = 0x12

        // $12FF + Y($01) = $1300
        memory[0x1300] = 0x80

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8002
        bus.reads shouldBe listOf(
            FakeBus.Read(0x8000),
            FakeBus.Read(0x8001),
            FakeBus.Read(0x0020),
            FakeBus.Read(0x0021),
            FakeBus.Read(0x1200),
            FakeBus.Read(0x1300),
        )
        cycles shouldBe 6
    }
})
