package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class LDATest : FreeSpec({

    val cases = listOf(
        LdaCase(
            name = "loads value into accumulator",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x42,
            expectedA = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
        LdaCase(
            name = "sets zero when loaded value is zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ),
            value = 0x00,
            expectedA = 0x00,
            expectedZ = true,
            expectedN = false,
        ),
        LdaCase(
            name = "clears zero when loaded value is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ).also {
                it.z = true
            },
            value = 0x01,
            expectedA = 0x01,
            expectedZ = false,
            expectedN = false,
        ),
        LdaCase(
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
        LdaCase(
            name = "clears negative when bit 7 is not set",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ).also {
                it.n = true
            },
            value = 0x7F,
            expectedA = 0x7F,
            expectedZ = false,
            expectedN = false,
        ),
        LdaCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ).also {
                it.c = true
                it.v = true
            },
            value = 0x42,
            expectedA = 0x42,
            expectedZ = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testLdaMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: LdaCase) -> Unit,
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

                    state.a shouldBe case.expectedA
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    // LDA must leave C and V unchanged.
                    state.c shouldBe initialCarry
                    state.v shouldBe initialOverflow

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }

    "LDA" - {

        testLdaMode(
            name = "immediate",
            instructionSize = 2,
            expectedCycles = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0xA9
            memory[state.pc + 1] = case.value
        }

        testLdaMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0xA5
            memory[state.pc + 1] = 0x42

            memory[0x0042] = case.value
        }

        testLdaMode(
            name = "zero page X",
            instructionSize = 2,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x10

            memory[state.pc] = 0xB5
            memory[state.pc + 1] = 0x40

            memory[0x0050] = case.value
        }

        testLdaMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            memory[state.pc] = 0xAD
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1234] = case.value
        }

        testLdaMode(
            name = "absolute X",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x01

            memory[state.pc] = 0xBD
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1235] = case.value
        }

        "absolute X with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                x = 0x01,
            )

            memory[state.pc] = 0xBD
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            memory[0x1300] = 0x80

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8003
            cycles shouldBe 5
        }

        testLdaMode(
            name = "absolute Y",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0xB9
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1235] = case.value
        }

        "absolute Y with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                y = 0x01,
            )

            memory[state.pc] = 0xB9
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            memory[0x1300] = 0x80

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8003
            cycles shouldBe 5
        }

        testLdaMode(
            name = "indirect X",
            instructionSize = 2,
            expectedCycles = 6,
        ) { memory, state, case ->
            state.x = 0x04

            memory[state.pc] = 0xA1
            memory[state.pc + 1] = 0x20

            // ($20 + X) = $24
            // Pointer at $24/$25 -> $1234
            memory[0x0024] = 0x34
            memory[0x0025] = 0x12

            memory[0x1234] = case.value
        }

        testLdaMode(
            name = "indirect Y",
            instructionSize = 2,
            expectedCycles = 5,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0xB1
            memory[state.pc + 1] = 0x20

            // Pointer at $20/$21 -> $1234
            memory[0x0020] = 0x34
            memory[0x0021] = 0x12

            // $1234 + Y($01) = $1235
            memory[0x1235] = case.value
        }

        "indirect Y with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                y = 0x01,
            )

            memory[state.pc] = 0xB1
            memory[state.pc + 1] = 0x20

            // Pointer at $20/$21 -> $12FF
            memory[0x0020] = 0xFF
            memory[0x0021] = 0x12

            // $12FF + Y($01) = $1300
            memory[0x1300] = 0x80

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8002
            cycles shouldBe 6
        }
    }
})

private data class LdaCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedA: Int,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)