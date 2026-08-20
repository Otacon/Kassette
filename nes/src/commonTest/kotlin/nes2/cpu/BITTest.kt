package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class BITTest : FreeSpec({
    lateinit var memory: IntArray
    lateinit var state: CpuState
    lateinit var bus: FakeBus
    lateinit var cpu: Cpu6502

    beforeTest {
        memory = IntArray(0x10_000)
        state = CpuState()
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
    }

    val cases = listOf(
        CpuInstructionCase(
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
        CpuInstructionCase(
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
        CpuInstructionCase(
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
        CpuInstructionCase(
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
        CpuInstructionCase(
            name = "clears overflow and negative from operand",
            cpuState = CpuState(
                pc = 0x8000,
                a = 0x00,
                status = 0xE0,
            ),
            value = 0x00,
            expectedZ = true,
            expectedV = false,
            expectedN = false,
        ),
    )

    fun FreeSpecRootScope.testBitMode(
        name: String,
        instructionSize: Int,
        expectedCycles: Int,
        setup: (IntArray, CpuState, CpuInstructionCase) -> Unit,
    ) {
        name - {
            cases.forEach { case ->
                case.name {
                    memory = IntArray(0x10_000)
                    state = case.cpuState.copy()

                    setup(memory, state, case)

                    val initialA = state.a
                    val initialC = state.c
                    val initialPc = state.pc

                    bus = FakeBus(memory = memory)
                    cpu = Cpu6502(bus = bus, state = state)

                    val cycles = cpu.step()

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
})
