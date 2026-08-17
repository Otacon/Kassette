package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.FakeBus

class ADCTest : FreeSpec({

    val cases = listOf(
        AdcCase(
            name = "simple addition",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x10,
            ),
            value = 0x20,
            expectedA = 0x30,
            expectedC = false,
            expectedV = false,
            expectedZ = false,
            expectedN = false,
        ),
        AdcCase(
            name = "includes carry in",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x10,
            ).also {
                it.c = true
            },
            value = 0x20,
            expectedA = 0x31,
            expectedC = false,
            expectedV = false,
            expectedZ = false,
            expectedN = false,
        ),
        AdcCase(
            name = "sets carry and zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ),
            value = 0x01,
            expectedA = 0x00,
            expectedC = true,
            expectedV = false,
            expectedZ = true,
            expectedN = false,
        ),
        AdcCase(
            name = "sets negative",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x80,
            expectedA = 0x80,
            expectedC = false,
            expectedV = false,
            expectedZ = false,
            expectedN = true,
        ),
        AdcCase(
            name = "sets overflow when positive plus positive becomes negative",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x50,
            ),
            value = 0x50,
            expectedA = 0xA0,
            expectedC = false,
            expectedV = true,
            expectedZ = false,
            expectedN = true,
        ),
        AdcCase(
            name = "sets overflow when negative plus negative becomes positive",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xD0,
            ),
            value = 0x90,
            expectedA = 0x60,
            expectedC = true,
            expectedV = true,
            expectedZ = false,
            expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testAdcMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (memory: IntArray, state: CpuState, case: AdcCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val cpu = Cpu6502(
                        bus = FakeBus(memory = memory),
                        state = state,
                    )

                    val initialPc = state.pc
                    val cycles = cpu.step()

                    state.a shouldBe case.expectedA
                    state.c shouldBe case.expectedC
                    state.v shouldBe case.expectedV
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    state.pc shouldBe initialPc + instructionSize
                    cycles shouldBe expectedCycles
                }
            }
        }
    }

    "ADC" - {

        testAdcMode(
            name = "immediate",
            instructionSize = 2,
            expectedCycles = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0x69
            memory[state.pc + 1] = case.value
        }

        testAdcMode(
            name = "zero page",
            instructionSize = 2,
            expectedCycles = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0x65
            memory[state.pc + 1] = 0x42

            memory[0x0042] = case.value
        }

        testAdcMode(
            name = "zero page X",
            instructionSize = 2,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x10

            memory[state.pc] = 0x75
            memory[state.pc + 1] = 0x40

            memory[0x0050] = case.value
        }

        testAdcMode(
            name = "absolute",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            memory[state.pc] = 0x6D
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            memory[0x1234] = case.value
        }

        testAdcMode(
            name = "absolute X",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.x = 0x01

            memory[state.pc] = 0x7D
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            // $1234 + X($01) = $1235
            memory[0x1235] = case.value
        }

        "absolute X with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x10,
                x = 0x01,
            )

            memory[state.pc] = 0x7D
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + X($01) = $1300
            memory[0x1300] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x30
            state.pc shouldBe 0x8003
            cycles shouldBe 5
        }

        testAdcMode(
            name = "absolute Y",
            instructionSize = 3,
            expectedCycles = 4,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0x79
            memory[state.pc + 1] = 0x34
            memory[state.pc + 2] = 0x12

            // $1234 + Y($01) = $1235
            memory[0x1235] = case.value
        }

        "absolute Y with page crossing penalty" {
            val memory = IntArray(0x10_000)

            val state = CpuState(
                pc = 0x8000,
                a = 0x10,
                y = 0x01,
            )

            memory[state.pc] = 0x79
            memory[state.pc + 1] = 0xFF
            memory[state.pc + 2] = 0x12

            // $12FF + Y($01) = $1300
            memory[0x1300] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x30
            state.pc shouldBe 0x8003
            cycles shouldBe 5
        }

        testAdcMode(
            name = "indirect X",
            instructionSize = 2,
            expectedCycles = 6,
        ) { memory, state, case ->
            state.x = 0x04

            memory[state.pc] = 0x61
            memory[state.pc + 1] = 0x20

            // ($20 + X) = $24
            // Pointer at $24/$25 -> $1234
            memory[0x0024] = 0x34
            memory[0x0025] = 0x12

            memory[0x1234] = case.value
        }

        testAdcMode(
            name = "indirect Y",
            instructionSize = 2,
            expectedCycles = 5,
        ) { memory, state, case ->
            state.y = 0x01

            memory[state.pc] = 0x71
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
                a = 0x10,
                y = 0x01,
            )

            memory[state.pc] = 0x71
            memory[state.pc + 1] = 0x20

            // Pointer at $20/$21 -> $12FF
            memory[0x0020] = 0xFF
            memory[0x0021] = 0x12

            // $12FF + Y($01) = $1300
            memory[0x1300] = 0x20

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x30
            state.pc shouldBe 0x8002
            cycles shouldBe 6
        }
    }
})

private data class AdcCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int,
    val expectedA: Int,
    val expectedC: Boolean,
    val expectedV: Boolean,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)