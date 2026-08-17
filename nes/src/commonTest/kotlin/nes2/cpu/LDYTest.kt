package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe

class LDYTest : FreeSpec({

    val cases = listOf(
        LdyCase(
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
        LdyCase(
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
        LdyCase(
            name = "clears zero when loaded value is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
            ).also {
                it.z = true
            },
            value = 0x01,
            expectedY = 0x01,
            expectedZ = false,
            expectedN = false,
        ),
        LdyCase(
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
        LdyCase(
            name = "clears negative when bit 7 is not set",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0xFF,
            ).also {
                it.n = true
            },
            value = 0x7F,
            expectedY = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        LdyCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                y = 0x00,
            ).also {
                it.c = true
                it.v = true
            },
            value = 0x42,
            expectedY = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testLdyMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: LdyCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialCarry = state.c
                    val initialOverflow = state.v
                    val initialPc = state.pc

                    val cpu = Cpu6502(
                        bus = FakeBus(memory = memory),
                        state = state,
                    )

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

    "LDY" - {

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
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                x = 0x01,
            )

            memory[state.pc] = 0xBC
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + X($01) = $1300
            memory[0x1300] = 0x80

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.y shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8003

            cycles shouldBe 5
        }
    }
})

private data class LdyCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedY: Int,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)