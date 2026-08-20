package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class SBCTest : FreeSpec({
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
            name = "simple subtraction with carry set",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x50,
                status = 0x21,
            ),
            value = 0x20,
            expectedA = 0x30,
            expectedC = true,
            expectedV = false,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "subtracts extra one when carry is clear",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x50,
                status = 0x20,
            ),
            value = 0x20,
            expectedA = 0x2F,
            expectedC = true,
            expectedV = false,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets zero when result is zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x42,
                status = 0x21,
            ),
            value = 0x42,
            expectedA = 0x00,
            expectedC = true,
            expectedV = false,
            expectedZ = true,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "clears carry when borrow occurs",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x10,
                status = 0x21,
            ),
            value = 0x20,
            expectedA = 0xF0,
            expectedC = false,
            expectedV = false,
            expectedZ = false,
            expectedN = true,
        ),
        CpuInstructionCase(
            name = "sets overflow when positive minus negative becomes negative",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x50,
                status = 0x21,
            ),
            value = 0xB0,
            expectedA = 0xA0,
            expectedC = false,
            expectedV = true,
            expectedZ = false,
            expectedN = true,
        ),
        CpuInstructionCase(
            name = "sets overflow when negative minus positive becomes positive",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xD0,
                status = 0x21,
            ),
            value = 0x50,
            expectedA = 0x80,
            expectedC = true,
            expectedV = false,
            expectedZ = false,
            expectedN = true,
        ),
    )

    fun FreeSpecRootScope.testSbcMode(
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

                    val initialPc = state.pc

                    bus = FakeBus(memory = memory)
                    cpu = Cpu6502(bus = bus, state = state)

                    val cycles = cpu.step()

                    state.a shouldBe case.expectedA
                    state.c shouldBe case.expectedC
                    state.v shouldBe case.expectedV
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }


    testSbcMode(
        name = "immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0xE9
        memory[state.pc + 1] = case.value
    }

    testSbcMode(
        name = "unofficial immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0xEB
        memory[state.pc + 1] = case.value
    }

    testSbcMode(
        name = "zero page",
        instructionSize = 2,
        expectedCycles = 3,
    ) { memory, state, case ->
        memory[state.pc] = 0xE5
        memory[state.pc + 1] = 0x42

        memory[0x0042] = case.value
    }

    testSbcMode(
        name = "zero page X",
        instructionSize = 2,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x10

        memory[state.pc] = 0xF5
        memory[state.pc + 1] = 0x40

        memory[0x0050] = case.value
    }

    testSbcMode(
        name = "absolute",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        memory[state.pc] = 0xED
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1234] = case.value
    }

    testSbcMode(
        name = "absolute X",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x01

        memory[state.pc] = 0xFD
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1235] = case.value
    }

    "absolute X with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            a = 0x50,
            x = 0x01,
            status = 0x21,
        )

        memory[state.pc] = 0xFD
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        memory[0x1300] = 0x20

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x30
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }

    testSbcMode(
        name = "absolute Y",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0xF9
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1235] = case.value
    }

    "absolute Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            a = 0x50,
            y = 0x01,
            status = 0x21,
        )

        memory[state.pc] = 0xF9
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        memory[0x1300] = 0x20

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x30
        state.pc shouldBe 0x8003
        cycles shouldBe 5
    }

    testSbcMode(
        name = "indirect X",
        instructionSize = 2,
        expectedCycles = 6,
    ) { memory, state, case ->
        state.x = 0x04

        memory[state.pc] = 0xE1
        memory[state.pc + 1] = 0x20

        memory[0x0024] = 0x34
        memory[0x0025] = 0x12

        memory[0x1234] = case.value
    }

    testSbcMode(
        name = "indirect Y",
        instructionSize = 2,
        expectedCycles = 5,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0xF1
        memory[state.pc + 1] = 0x20

        memory[0x0020] = 0x34
        memory[0x0021] = 0x12

        memory[0x1235] = case.value
    }

    "indirect Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            a = 0x50,
            y = 0x01,
            status = 0x21,
        )

        memory[state.pc] = 0xF1
        memory[state.pc + 1] = 0x20

        memory[0x0020] = 0xFF
        memory[0x0021] = 0x12

        memory[0x1300] = 0x20

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x30
        state.pc shouldBe 0x8002
        cycles shouldBe 6
    }
})
