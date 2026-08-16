package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecContainerScope
import io.kotest.matchers.shouldBe
import nes2.CpuBus

class ADCTest : FreeSpec({

    val cases = listOf(
        AdcCase(
            name = "simple addition",
            a = 0x10, value = 0x20, carryIn = false,
            expectedA = 0x30, expectedC = false, expectedV = false, expectedZ = false, expectedN = false,
        ),
        AdcCase(
            name = "includes carry in",
            a = 0x10, value = 0x20, carryIn = true,
            expectedA = 0x31, expectedC = false, expectedV = false, expectedZ = false, expectedN = false,
        ),
        AdcCase(
            name = "sets carry and zero",
            a = 0xFF, value = 0x01, carryIn = false,
            expectedA = 0x00, expectedC = true, expectedV = false, expectedZ = true, expectedN = false,
        ),
        AdcCase(
            name = "sets negative",
            a = 0x00, value = 0x80, carryIn = false,
            expectedA = 0x80, expectedC = false, expectedV = false, expectedZ = false, expectedN = true,
        ),
        AdcCase(
            name = "sets overflow when positive plus positive becomes negative",
            a = 0x50, value = 0x50, carryIn = false,
            expectedA = 0xA0, expectedC = false, expectedV = true, expectedZ = false, expectedN = true,
        ),
        AdcCase(
            name = "sets overflow when negative plus negative becomes positive",
            a = 0xD0, value = 0x90, carryIn = false,
            expectedA = 0x60, expectedC = true, expectedV = true, expectedZ = false, expectedN = false,
        ),
    )

    suspend fun FreeSpecContainerScope.testAdcMode(
        name: String,
        setup: (memory: IntArray, case: AdcCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    val memory = IntArray(0x10_000)

                    setup(memory, case)

                    val bus = CpuBus(memory = memory)

                    val state = CpuState(
                        pc = 0x8000,
                        a = case.a,
                    ).also {
                        it.c = case.carryIn
                    }

                    Cpu6502(
                        bus = bus,
                        state = state,
                    ).step()

                    state.a shouldBe case.expectedA
                    state.c shouldBe case.expectedC
                    state.v shouldBe case.expectedV
                    state.z shouldBe case.expectedZ
                    state.n shouldBe case.expectedN
                    state.pc shouldBe 0x8002
                }
            }
        }
    }

    "ADC" - {

        testAdcMode("immediate") { memory, case ->
            memory[0x8000] = 0x69
            memory[0x8001] = case.value
        }

        testAdcMode("zero page") { memory, case ->
            memory[0x8000] = 0x65
            memory[0x8001] = 0x42
            memory[0x0042] = case.value
        }
    }
})

private data class AdcCase(
    val name: String,
    val a: Int,
    val value: Int,
    val carryIn: Boolean,
    val expectedA: Int,
    val expectedC: Boolean,
    val expectedV: Boolean,
    val expectedZ: Boolean,
    val expectedN: Boolean,
)