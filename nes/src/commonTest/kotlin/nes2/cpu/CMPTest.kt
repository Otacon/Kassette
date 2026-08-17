package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe

class CMPTest : FreeSpec({

    val cases = listOf(
        CmpCase(
            name = "sets carry and zero when accumulator equals value",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x42,
            ),
            value = 0x42,
            expectedC = true,
            expectedZ = true,
            expectedN = false,
        ),
        CmpCase(
            name = "sets carry when accumulator is greater than value",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x50,
            ),
            value = 0x20,
            expectedC = true,
            expectedZ = false,
            expectedN = false,
        ),
        CmpCase(
            name = "clears carry when accumulator is less than value",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x20,
            ),
            value = 0x50,
            expectedC = false,
            expectedZ = false,
            expectedN = true,
        ),
        CmpCase(
            name = "sets negative from subtraction result",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x01,
            expectedC = false,
            expectedZ = false,
            expectedN = true,
        ),
        CmpCase(
            name = "clears negative from subtraction result",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ),
            value = 0x01,
            expectedC = true,
            expectedZ = false,
            expectedN = true,
        ),
        CmpCase(
            name = "does not modify accumulator or overflow",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x80,
            ).also {
                it.v = true
            },
            value = 0x7F,
            expectedC = true,
            expectedZ = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testCmpMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: CmpCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialA = state.a
                    val initialV = state.v
                    val initialPc = state.pc

                    val cpu = Cpu6502(
                        bus = FakeBus(memory = memory),
                        state = state,
                    )

                    val cycles = cpu.step()

                    state.a shouldBe initialA

                    state.c shouldBe case.expectedC
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    // CMP must leave V unchanged.
                    state.v shouldBe initialV

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }

    "CMP" - {

        testCmpMode(
            name = "immediate",
            instructionSize = 2,
            expectedCycles = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0xC9
            memory[state.pc + 1] = case.value
        }

        testCmpMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0xC5
            memory[state.pc + 1] = 0x42

            memory[0x0042] = case.value
        }

        testCmpMode(
            name = "zero page X",
            instructionSize = 2,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x10

            memory[state.pc] = 0xD5
            memory[state.pc + 1] = 0x40

            memory[0x0050] = case.value
        }

        testCmpMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            memory[state.pc] = 0xCD
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1234] = case.value
        }

        testCmpMode(
            name = "absolute X",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x01

            memory[state.pc] = 0xDD
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1235] = case.value
        }

        "absolute X with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x50,
                x = 0x01,
            )

            memory[state.pc] = 0xDD
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            memory[0x1300] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x50
            state.c shouldBe true
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8003

            cycles shouldBe 5
        }

        testCmpMode(
            name = "absolute Y",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0xD9
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1235] = case.value
        }

        "absolute Y with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x50,
                y = 0x01,
            )

            memory[state.pc] = 0xD9
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            memory[0x1300] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x50
            state.c shouldBe true
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8003

            cycles shouldBe 5
        }

        testCmpMode(
            name = "indirect X",
            instructionSize = 2,
            expectedCycles = 6,
        ) { memory, state, case ->
            state.x = 0x04

            memory[state.pc] = 0xC1
            memory[state.pc + 1] = 0x20

            memory[0x0024] = 0x34
            memory[0x0025] = 0x12

            memory[0x1234] = case.value
        }

        testCmpMode(
            name = "indirect Y",
            instructionSize = 2,
            expectedCycles = 5,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0xD1
            memory[state.pc + 1] = 0x20

            memory[0x0020] = 0x34
            memory[0x0021] = 0x12

            memory[0x1235] = case.value
        }

        "indirect Y with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x50,
                y = 0x01,
            )

            memory[state.pc] = 0xD1
            memory[state.pc + 1] = 0x20

            memory[0x0020] = 0xFF
            memory[0x0021] = 0x12

            memory[0x1300] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x50
            state.c shouldBe true
            state.z shouldBe false
            state.n shouldBe false
            state.pc shouldBe 0x8002

            cycles shouldBe 6
        }
    }
})

private data class CmpCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedC: Boolean,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)