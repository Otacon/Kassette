package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class CPXTest : FreeSpec({

    val cases = listOf(
        CpxCase(
            name = "sets carry and zero when equal",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x42,
            ),
            value = 0x42,
            expectedC = true,
            expectedZ = true,
            expectedN = false,
        ),
        CpxCase(
            name = "sets carry when X is greater",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x50,
            ),
            value = 0x40,
            expectedC = true,
            expectedZ = false,
            expectedN = false,
        ),
        CpxCase(
            name = "clears carry when X is smaller",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x40,
            ),
            value = 0x50,
            expectedC = false,
            expectedZ = false,
            expectedN = true,
        ),
        CpxCase(
            name = "negative comes from subtraction result",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
            ),
            value = 0x01,
            expectedC = false,
            expectedZ = false,
            expectedN = true,
        ),
    )

    suspend fun FreeSpecContainerScope.testCpxMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (IntArray, CpuState, CpxCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialX = state.x
                    val initialV = state.v
                    val initialPc = state.pc

                    val cycles = Cpu6502(
                        bus = FakeBus(memory = memory),
                        state = state,
                    ).step()

                    state.x shouldBe initialX
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

    "CPX" - {

        testCpxMode(
            name = "immediate",
            instructionSize = 2,
            expectedCycles = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0xE0
            memory[state.pc + 1] = case.value
        }

        testCpxMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0xE4
            memory[state.pc + 1] = 0x20
            memory[0x0020] = case.value
        }

        testCpxMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            memory[state.pc] = 0xEC
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
            memory[0x1234] = case.value
        }
    }
})

private data class CpxCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedC: Boolean,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)