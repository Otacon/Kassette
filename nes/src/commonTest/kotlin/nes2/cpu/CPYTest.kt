package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.FakeBus

class CPYTest : FreeSpec({

    val cases = listOf(
        CpyCase(
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
        CpyCase(
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
        CpyCase(
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
        CpyCase(
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

    suspend fun FreeSpecContainerScope.testCpyMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (IntArray, CpuState, CpyCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialY = state.y
                    val initialV = state.v
                    val initialPc = state.pc

                    val cycles = Cpu6502(
                        bus = FakeBus(memory = memory),
                        state = state,
                    ).step()

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

    "CPY" - {

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
    }
})

private data class CpyCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedC: Boolean,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)