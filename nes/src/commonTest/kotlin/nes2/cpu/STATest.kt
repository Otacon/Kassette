package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class STATest : FreeSpec({

    val cases = listOf(
        StaCase(
            name = "stores accumulator",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x42,
            ),
            expectedValue = 0x42,
        ),
        StaCase(
            name = "stores zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            expectedValue = 0x00,
        ),
        StaCase(
            name = "stores value with bit 7 set",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x80,
            ),
            expectedValue = 0x80,
        ),
        StaCase(
            name = "does not modify flags",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xAA,
            ).also {
                it.c = true
                it.z = true
                it.v = true
                it.n = true
            },
            expectedValue = 0xAA,
        ),
    )

    suspend fun FreeSpecContainerScope.testStaMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        expectedAddress: (CpuState) -> Int,
        setup: (memory: IntArray, state: CpuState, case: StaCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialA = state.a
                    val initialC = state.c
                    val initialZ = state.z
                    val initialV = state.v
                    val initialN = state.n
                    val initialPc = state.pc

                    val address = expectedAddress(state)

                    val cpu = Cpu6502(
                        bus = CpuBus(memory = memory),
                        state = state,
                    )

                    val cycles = cpu.step()

                    memory[address] shouldBe case.expectedValue

                    // STA does not modify A.
                    state.a shouldBe initialA

                    // STA does not modify any flags.
                    state.c shouldBe initialC
                    state.z shouldBe initialZ
                    state.v shouldBe initialV
                    state.n shouldBe initialN

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }

    "STA" - {

        testStaMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
            expectedAddress = { 0x0042 },
        ) { memory, state, _ ->
            memory[state.pc] = 0x85
            memory[state.pc + 1] = 0x42
        }

        testStaMode(
            name = "zero page X",
            instructionSize = 2,
            expectedCycles = 4,
            expectedAddress = { 0x0050 },
        ) { memory, state, _ ->
            state.x = 0x10

            memory[state.pc] = 0x95
            memory[state.pc + 1] = 0x40

            // $40 + X($10) = $50
        }

        testStaMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
            expectedAddress = { 0x1234 },
        ) { memory, state, _ ->
            memory[state.pc] = 0x8D
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12
        }

        testStaMode(
            name = "absolute X",
            instructionSize = 3,
            expectedCycles = 5,
            expectedAddress = { 0x1235 },
        ) { memory, state, _ ->
            state.x = 0x01

            memory[state.pc] = 0x9D
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            // $1234 + X($01) = $1235
        }

        "absolute X crossing a page still takes 5 cycles" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
                x = 0x01,
            )

            memory[state.pc] = 0x9D
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + X($01) = $1300

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            memory[0x1300] shouldBe 0x42
            state.pc shouldBe 0x8003

            // No conditional +1 penalty for STA.
            cycles shouldBe 5
        }

        testStaMode(
            name = "absolute Y",
            instructionSize = 3,
            expectedCycles = 5,
            expectedAddress = { 0x1235 },
        ) { memory, state, _ ->
            state.y = 0x01

            memory[state.pc] = 0x99
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            // $1234 + Y($01) = $1235
        }

        "absolute Y crossing a page still takes 5 cycles" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
                y = 0x01,
            )

            memory[state.pc] = 0x99
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + Y($01) = $1300

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            memory[0x1300] shouldBe 0x42
            state.pc shouldBe 0x8003

            cycles shouldBe 5
        }

        testStaMode(
            name = "indirect X",
            instructionSize = 2,
            expectedCycles = 6,
            expectedAddress = { 0x1234 },
        ) { memory, state, _ ->
            state.x = 0x04

            memory[state.pc] = 0x81
            memory[state.pc + 1] = 0x20

            // ($20 + X) = $24
            // Pointer at $24/$25 -> $1234
            memory[0x0024] = 0x34
            memory[0x0025] = 0x12
        }

        testStaMode(
            name = "indirect Y",
            instructionSize = 2,
            expectedCycles = 6,
            expectedAddress = { 0x1235 },
        ) { memory, state, _ ->
            state.y = 0x01

            memory[state.pc] = 0x91
            memory[state.pc + 1] = 0x20

            // Pointer at $20/$21 -> $1234
            memory[0x0020] = 0x34
            memory[0x0021] = 0x12

            // $1234 + Y($01) = $1235
        }

        "indirect Y crossing a page still takes 6 cycles" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x42,
                y = 0x01,
            )

            memory[state.pc] = 0x91
            memory[state.pc + 1] = 0x20

            // Pointer at $20/$21 -> $12FF
            memory[0x0020] = 0xFF
            memory[0x0021] = 0x12

            // $12FF + Y($01) = $1300

            val cycles = Cpu6502(
                bus = CpuBus(memory = memory),
                state = state,
            ).step()

            memory[0x1300] shouldBe 0x42
            state.pc shouldBe 0x8002

            cycles shouldBe 6
        }
    }
})

private data class StaCase(
    val name: String,
    val cpuState: CpuState,
    val expectedValue: Int,
)