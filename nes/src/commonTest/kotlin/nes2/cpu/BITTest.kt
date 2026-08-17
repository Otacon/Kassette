package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.FakeBus

class BITTest : FreeSpec({

    val cases = listOf(
        BitCase(
            name = "sets zero when A AND operand is zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x0F,
            ),
            value = 0xF0,
            expectedZ = true,
            expectedV = true,
            expectedN = true,
        ),
        BitCase(
            name = "clears zero when A AND operand is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x0F,
            ),
            value = 0x01,
            expectedZ = false,
            expectedV = false,
            expectedN = false,
        ),
        BitCase(
            name = "copies operand bit 6 into overflow",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x40,
            expectedZ = true,
            expectedV = true,
            expectedN = false,
        ),
        BitCase(
            name = "copies operand bit 7 into negative",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x80,
            expectedZ = true,
            expectedV = false,
            expectedN = true,
        ),
        BitCase(
            name = "clears overflow and negative from operand",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ).also {
                it.v = true
                it.n = true
            },
            value = 0x00,
            expectedZ = true,
            expectedV = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testBitMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (IntArray, CpuState, BitCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialA = state.a
                    val initialC = state.c
                    val initialPc = state.pc

                    val cycles = Cpu6502(
                        bus = FakeBus(memory = memory),
                        state = state,
                    ).step()

                    state.a shouldBe initialA
                    state.c shouldBe initialC

                    state.z shouldBe case.expectedZ
                    state.v shouldBe case.expectedV
                    state.n shouldBe case.expectedN

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }

    "BIT" - {

        testBitMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0x24
            memory[state.pc + 1] = 0x20

            memory[0x0020] = case.value
        }

        testBitMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            memory[state.pc] = 0x2C
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1234] = case.value
        }
    }
})

private data class BitCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedZ: Boolean,
    val expectedV: Boolean,
    val expectedN: Boolean,
)