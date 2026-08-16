package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class ADCTest : FreeSpec({

    val cases = listOf(
        AdcCase(
            name = "simple addition",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x10,
            ),
            value = 0x20,
            expectedA = 0x30, expectedC = false, expectedV = false, expectedZ = false, expectedN = false,
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
            expectedA = 0x31, expectedC = false, expectedV = false, expectedZ = false, expectedN = false,
        ),
        AdcCase(
            name = "sets carry and zero",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xFF,
            ),
            value = 0x01,
            expectedA = 0x00, expectedC = true, expectedV = false, expectedZ = true, expectedN = false,
        ),
        AdcCase(
            name = "sets negative",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
            ),
            value = 0x80,
            expectedA = 0x80, expectedC = false, expectedV = false, expectedZ = false, expectedN = true,
        ),
        AdcCase(
            name = "sets overflow when positive plus positive becomes negative",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x50,
            ),
            value = 0x50,
            expectedA = 0xA0, expectedC = false, expectedV = true, expectedZ = false, expectedN = true,
        ),
        AdcCase(
            name = "sets overflow when negative plus negative becomes positive",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0xD0,
            ),
            value = 0x90,
            expectedA = 0x60, expectedC = true, expectedV = true, expectedZ = false, expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testAdcMode(
        name: String,
        instructionSize: Int,
        setup: (memory: IntArray, state: CpuState, case: AdcCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)

                    // Important: don't reuse the CpuState instance stored in the case,
                    // because the CPU mutates it.
                    val state = case.cpuState.copy()

                    setup(memory, state, case)

                    val bus = CpuBus(memory = memory)
                    val cpu = Cpu6502(
                        bus = bus,
                        state = state,
                    )

                    val initialPc = state.pc

                    cpu.step()

                    state.a shouldBe case.expectedA
                    state.c shouldBe case.expectedC
                    state.v shouldBe case.expectedV
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN

                    state.pc shouldBe initialPc + instructionSize
                }
            }
        }
    }

    "ADC" - {

        testAdcMode(
            name = "immediate",
            instructionSize = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0x69
            memory[state.pc + 1] = case.value
        }

        testAdcMode(
            name = "zero page",
            instructionSize = 2,
        ) { memory, state, case ->
            memory[state.pc] = 0x65
            memory[state.pc + 1] = 0x42

            memory[0x0042] = case.value
        }

        testAdcMode(
            name = "zero page X",
            instructionSize = 2,
        ) { memory, state, case ->
            state.x = 0x10

            memory[state.pc] = 0x75
            memory[state.pc + 1] = 0x40

            memory[0x0050] = case.value
        }

        testAdcMode(
            name = "absolute",
            instructionSize = 3,
        ) { memory, state, case ->
            memory[state.pc] = 0x6D

            memory[state.pc + 1] = 0x34 // low byte
            memory[state.pc + 2] = 0x12 // high byte

            memory[0x1234] = case.value
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