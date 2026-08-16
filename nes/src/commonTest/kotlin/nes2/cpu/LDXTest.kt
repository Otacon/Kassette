package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class LDXTest : FreeSpec({

    val cases = listOf(
        LdxCase(
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
        LdxCase(
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
        LdxCase(
            name = "clears zero when loaded value is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
            ).also {
                it.z = true
            },
            value = 0x01,
            expectedX = 0x01,
            expectedZ = false,
            expectedN = false,
        ),
        LdxCase(
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
        LdxCase(
            name = "clears negative when bit 7 is not set",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0xFF,
            ).also {
                it.n = true
            },
            value = 0x7F,
            expectedX = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        LdxCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                x = 0x00,
            ).also {
                it.c = true
                it.v = true
            },
            value = 0x42,
            expectedX = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testLdxMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: LdxCase) -> Unit,
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
                        bus = CpuBus(memory = memory),
                        state = state,
                    )

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

    "LDX" - {

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
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                y = 0x01,
            )

            memory[state.pc] = 0xBE
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + Y($01) = $1300
            memory[0x1300] = 0x80

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8003

            cycles shouldBe 5
        }
    }
})

private data class LdxCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedX: Int,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)