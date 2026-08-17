package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe

class ANDTest : FreeSpec({

    val cases = listOf(
        AndCase(
            name = "simple AND",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0b1111_0000,
            ),
            value = 0b1010_1010,
            expectedA = 0b1010_0000,
            expectedZ = false,
            expectedN = true,
        ),
        AndCase(
            name = "sets zero when result is zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0b1111_0000,
            ),
            value = 0b0000_1111,
            expectedA = 0x00,
            expectedZ = true,
            expectedN = false,
        ),
        AndCase(
            name = "clears zero when result is non zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x0F,
            ).also {
                it.z = true
            },
            value = 0x03,
            expectedA = 0x03,
            expectedZ = false,
            expectedN = false,
        ),
        AndCase(
            name = "sets negative when bit 7 is set",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ),
            value = 0x80,
            expectedA = 0x80,
            expectedZ = false,
            expectedN = true,
        ),
        AndCase(
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
        AndCase(
            name = "does not modify carry or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ).also {
                it.c = true
                it.v = true
            },
            value = 0x0F,
            expectedA = 0x0F,
            expectedZ = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testAndMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: AndCase) -> Unit,
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

                    state.a shouldBe case.expectedA
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    // AND must leave C and V unchanged.
                    state.c shouldBe initialCarry
                    state.v shouldBe initialOverflow

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }

    "AND" - {

        testAndMode(
            name = "immediate",
            instructionSize = 2,
            expectedCycles = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0x29
            memory[state.pc + 1] = case.value
        }

        testAndMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0x25
            memory[state.pc + 1] = 0x42

            memory[0x0042] = case.value
        }

        testAndMode(
            name = "zero page X",
            instructionSize = 2,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x10

            memory[state.pc] = 0x35
            memory[state.pc + 1] = 0x40

            memory[0x0050] = case.value
        }

        testAndMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            memory[state.pc] = 0x2D
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1234] = case.value
        }

        testAndMode(
            name = "absolute X",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x01

            memory[state.pc] = 0x3D
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            // $1234 + X($01) = $1235
            memory[0x1235] = case.value
        }

        "absolute X with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0xF0,
                x = 0x01,
            )

            memory[state.pc] = 0x3D
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + X($01) = $1300
            memory[0x1300] = 0xAA

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0xA0
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8003
            cycles shouldBe 5
        }

        testAndMode(
            name = "absolute Y",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0x39
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            // $1234 + Y($01) = $1235
            memory[0x1235] = case.value
        }

        "absolute Y with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0xF0,
                y = 0x01,
            )

            memory[state.pc] = 0x39
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + Y($01) = $1300
            memory[0x1300] = 0xAA

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0xA0
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8003
            cycles shouldBe 5
        }

        testAndMode(
            name = "indirect X",
            instructionSize = 2,
            expectedCycles = 6,
        ) { memory, state, case ->
            state.x = 0x04

            memory[state.pc] = 0x21
            memory[state.pc + 1] = 0x20

            // ($20 + X) = $24
            // Pointer at $24/$25 -> $1234
            memory[0x0024] = 0x34
            memory[0x0025] = 0x12

            memory[0x1234] = case.value
        }

        testAndMode(
            name = "indirect Y",
            instructionSize = 2,
            expectedCycles = 5,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0x31
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
                a = 0xF0,
                y = 0x01,
            )

            memory[state.pc] = 0x31
            memory[state.pc + 1] = 0x20

            // Pointer at $20/$21 -> $12FF
            memory[0x0020] = 0xFF
            memory[0x0021] = 0x12

            // $12FF + Y($01) = $1300
            memory[0x1300] = 0xAA

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0xA0
            state.z shouldBe false
            state.n shouldBe true
            state.pc shouldBe 0x8002
            cycles shouldBe 6
        }
    }
})

private data class AndCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedA: Int,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)