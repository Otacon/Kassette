package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class LDXTest : FreeSpec({
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
            name = "loads value into X",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
            ),
            value = 0x42,
            expectedX = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets zero when loaded value is zero",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0xFF,
            ),
            value = 0x00,
            expectedX = 0x00,
            expectedZ = true,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "clears zero when loaded value is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
                status = 0x22,
            ),
            value = 0x01,
            expectedX = 0x01,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets negative when bit 7 is set",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
            ),
            value = 0x80,
            expectedX = 0x80,
            expectedZ = false,
            expectedN = true,
        ),
        CpuInstructionCase(
            name = "clears negative when bit 7 is not set",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0xFF,
                status = 0xA0,
            ),
            value = 0x7F,
            expectedX = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
                status = 0x61,
            ),
            value = 0x42,
            expectedX = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
    )

    fun FreeSpecRootScope.testLdxMode(
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

                    state.x shouldBe case.expectedX
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


    testLdxMode(
        name = "immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0xA2
        memory[state.pc + 1] = case.value
    }

    testLdxMode(
        name = "zero page",
        instructionSize = 2,
        expectedCycles = 3,
    ) { memory, state, case ->
        memory[state.pc] = 0xA6
        memory[state.pc + 1] = 0x42

        memory[0x0042] = case.value
    }

    testLdxMode(
        name = "zero page Y",
        instructionSize = 2,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.y = 0x10

        memory[state.pc] = 0xB6
        memory[state.pc + 1] = 0x40

        // $40 + Y($10) = $50
        memory[0x0050] = case.value
    }

    testLdxMode(
        name = "absolute",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        memory[state.pc] = 0xAE
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        memory[0x1234] = case.value
    }

    testLdxMode(
        name = "absolute Y",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        state.y = 0x01

        memory[state.pc] = 0xBE
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12

        // $1234 + Y($01) = $1235
        memory[0x1235] = case.value
    }

    "absolute Y with page crossing penalty" {

        state = CpuState(
            pc = 0x8000,
            y = 0x01,
        )

        memory[state.pc] = 0xBE
        memory[state.pc + 1] = 0xFF
        memory[state.pc + 2] = 0x12

        // $12FF + Y($01) = $1300
        memory[0x1300] = 0x80

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.x shouldBe 0x80
        state.z shouldBe false
        state.n shouldBe true
        state.pc shouldBe 0x8003

        cycles shouldBe 5
    }
})
