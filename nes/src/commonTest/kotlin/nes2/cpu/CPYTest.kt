package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class CPYTest : FreeSpec({
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
            name = "sets carry and zero when equal",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x42,
            ),
            value = 0x42,
            expectedC = true,
            expectedZ = true,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "sets carry when Y is greater",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x50,
            ),
            value = 0x40,
            expectedC = true,
            expectedZ = false,
            expectedN = false,
        ),
        CpuInstructionCase(
            name = "clears carry when Y is smaller",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x40,
            ),
            value = 0x50,
            expectedC = false,
            expectedZ = false,
            expectedN = true,
        ),
        CpuInstructionCase(
            name = "negative comes from subtraction result",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
            ),
            value = 0x01,
            expectedC = false,
            expectedZ = false,
            expectedN = true,
        ),
    )

    fun FreeSpecRootScope.testCpyMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (IntArray, CpuState, CpuInstructionCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    memory = IntArray(0x10_000)
                    state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialY = state.y
                    val initialV = state.v
                    val initialPc = state.pc

                    bus = FakeBus(memory = memory)
                    cpu = Cpu6502(bus = bus, state = state)

                    val cycles = cpu.step()

                    state.y shouldBe initialY
                    state.c shouldBe case.expectedC
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN
                    state.v shouldBe initialV

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }


    testCpyMode(
        name = "immediate",
        instructionSize = 2,
        expectedCycles = 2,
    ) { memory, state, case ->
        memory[state.pc] = 0xC0
        memory[state.pc + 1] = case.value
    }

    testCpyMode(
        name = "zero page",
        instructionSize = 2,
        expectedCycles = 3,
    ) { memory, state, case ->
        memory[state.pc] = 0xC4
        memory[state.pc + 1] = 0x20
        memory[0x0020] = case.value
    }

    testCpyMode(
        name = "absolute",
        instructionSize = 3,
        expectedCycles = 4,
    ) { memory, state, case ->
        memory[state.pc] = 0xCC
        memory[state.pc + 1] = 0x34
        memory[state.pc + 2] = 0x12
        memory[0x1234] = case.value
    }
})
