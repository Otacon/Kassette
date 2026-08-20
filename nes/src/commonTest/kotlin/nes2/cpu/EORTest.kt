package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class EORTest : FreeSpec({
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
            name = "simple EOR",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0b1010_1010,
            ),
            value = 0b1111_0000,
            expectedA = 0b0101_1010,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets zero when values are equal",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xAA,
            ),
            value = 0xAA,
            expectedA = 0x00,
            expectedZ = true,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "clears zero when result is non zero",
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
            value = 0x80,
            expectedA = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xF0,
                status = 0x61,
            ),
            value = 0x0F,
            expectedA = 0xFF,
            expectedZ = false,
            expectedN = true,
        ),
    )

    fun FreeSpecRootScope.testEorMode(
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

                    // EOR must leave C and V unchanged.
                    state.c shouldBe initialCarry
                    state.v shouldBe initialOverflow

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }


    testEorMode(
        name = "immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0x49
        memory[state.pc + 1] = case.value
    }

    testEorMode(
        name = "zero page",
        instructionSize = 2,
        expectedCycles = 3,
    ) { memory, state, case ->
        memory[state.pc] = 0x45
        memory[state.pc + 1] = 0x42

        memory[0x0042] = case.value
    }

    testEorMode(
        name = "zero page X",
        instructionSize = 2,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x10

        memory[state.pc] = 0x55
        memory[state.pc + 1] = 0x40

        memory[0x0050] = case.value
    }

    testEorMode(
        name = "absolute",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        memory[state.pc] = 0x4D
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1234] = case.value
    }

    testEorMode(
        name = "absolute X",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x01

        memory[state.pc] = 0x5D
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1235] = case.value
    }

    "absolute X with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            a = 0xF0,
            x = 0x01,
        )

        memory[state.pc] = 0x5D
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        memory[0x1300] = 0x0F

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0xFF
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }

    testEorMode(
        name = "absolute Y",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0x59
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1235] = case.value
    }

    "absolute Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            a = 0xF0,
            y = 0x01,
        )

        memory[state.pc] = 0x59
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        memory[0x1300] = 0x0F

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0xFF
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }

    testEorMode(
        name = "indirect X",
        instructionSize = 2,
        expectedCycles = 6,
    ) { memory, state, case ->
        state.x = 0x04

        memory[state.pc] = 0x41
        memory[state.pc + 1] = 0x20

        memory[0x0024] = 0x34
        memory[0x0025] = 0x12

        memory[0x1234] = case.value
    }

    testEorMode(
        name = "indirect Y",
        instructionSize = 2,
        expectedCycles = 5,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0x51
        memory[state.pc + 1] = 0x20

        memory[0x0020] = 0x34
        memory[0x0021] = 0x12

        memory[0x1235] = case.value
    }

    "indirect Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            a = 0xF0,
            y = 0x01,
        )

        memory[state.pc] = 0x51
        memory[state.pc + 1] = 0x20

        memory[0x0020] = 0xFF
        memory[0x0021] = 0x12

        memory[0x1300] = 0x0F

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0xFF
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }
})
