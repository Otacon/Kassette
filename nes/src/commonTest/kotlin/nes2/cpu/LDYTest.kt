package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class LDYTest : FreeSpec({
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
            name = "loads value into Y",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
            ),
            value = 0x42,
            expectedY = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets zero when loaded value is zero",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0xFF,
            ),
            value = 0x00,
            expectedY = 0x00,
            expectedZ = true,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "clears zero when loaded value is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
                status = 0x22,
            ),
            value = 0x01,
            expectedY = 0x01,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets negative when bit 7 is set",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
            ),
            value = 0x80,
            expectedY = 0x80,
            expectedZ = false,
            expectedN = true,
        ),
        CpuInstructionCase(
            name = "clears negative when bit 7 is not set",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0xFF,
                status = 0xA0,
            ),
            value = 0x7F,
            expectedY = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
                status = 0x61,
            ),
            value = 0x42,
            expectedY = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
    )

    fun FreeSpecRootScope.testLdyMode(
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

                    state.y shouldBe case.expectedY
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    state.c shouldBe initialCarry
                    state.v shouldBe initialOverflow

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }


    testLdyMode(
        name = "immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0xA0
        memory[state.pc + 1] = case.value
    }

    testLdyMode(
        name = "zero page",
        instructionSize = 2,
        expectedCycles = 3,
    ) { memory, state, case ->
        memory[state.pc] = 0xA4
        memory[state.pc + 1] = 0x42

        memory[0x0042] = case.value
    }

    testLdyMode(
        name = "zero page X",
        instructionSize = 2,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x10

        memory[state.pc] = 0xB4
        memory[state.pc + 1] = 0x40

        // $40 + X($10) = $50
        memory[0x0050] = case.value
    }

    testLdyMode(
        name = "absolute",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        memory[state.pc] = 0xAC
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1234] = case.value
    }

    testLdyMode(
        name = "absolute X",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.x = 0x01

        memory[state.pc] = 0xBC
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        // $1234 + X($01) = $1235
        memory[0x1235] = case.value
    }

    "absolute X with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            x = 0x01,
        )

        memory[state.pc] = 0xBC
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        // $12FF + X($01) = $1300
        memory[0x1300] = 0x80

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.y shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8003

        cycles shouldBe 5
    }
})
