package nes2.cpu

import nes.util.isNegative8Bit
import nes.util.low16Bits
import nes.util.low8Bits
import nes2.CpuBus
import nes2.cpu.AddressingMode.*

class Cpu6502(
    private val bus: CpuBus,
    private var state: CpuState = CpuState(),
) {

    private val instructions = arrayOfNulls<Instruction>(256)

    init {

        // ADC
        instructions[0x69] = Instruction(Operation.ADC, IMMEDIATE, 2)
        instructions[0x65] = Instruction(Operation.ADC, ZERO_PAGE, 3)
        instructions[0x75] = Instruction(Operation.ADC, ZERO_PAGE_X, 4)
        instructions[0x61] = Instruction(Operation.ADC, INDIRECT_X, 6)
        instructions[0x6D] = Instruction(Operation.ADC, ABSOLUTE, 4)
        instructions[0x71] = Instruction(Operation.ADC, INDIRECT_Y, 5)
        instructions[0x79] = Instruction(Operation.ADC, ABSOLUTE_Y, 4)
        instructions[0x7D] = Instruction(Operation.ADC, ABSOLUTE_X, 4)
    }

    fun reset() {
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
    }

    fun step() {
        val opCode = pcRead()
        val instruction = instructions[opCode] ?: throw IllegalArgumentException("Instruction#$opCode not found")
        execute(instruction = instruction)
    }

    private fun execute(instruction: Instruction) {
        when (instruction.operation) {
            Operation.ADC -> {
                val operand = readOperand(instruction.addressingMode)
                adc(operand)
            }
        }
    }

    private fun readOperand(mode: AddressingMode): Int {
        return when (mode) {
            IMMEDIATE -> pcRead()
            ZERO_PAGE -> {
                val address = pcRead()
                bus.read(address)
            }

            ZERO_PAGE_X -> {
                val address = (pcRead() + state.x).low8Bits()
                bus.read(address)
            }

            ABSOLUTE -> {
                val lo = pcRead()
                val hi = pcRead()
                val address = lo or (hi shl 8)
                bus.read(address)
            }

            IMPLIED -> TODO()
            ACCUMULATOR -> TODO()
            ZERO_PAGE_Y -> TODO()
            RELATIVE -> TODO()
            ABSOLUTE_X -> TODO()
            ABSOLUTE_Y -> TODO()
            INDIRECT -> TODO()
            INDIRECT_X -> TODO()
            INDIRECT_Y -> TODO()
        }
    }

    private fun adc(value: Int) {
        val a = state.a
        val carryIn = if (state.c) 1 else 0
        val sum = a + value + carryIn
        val result = sum.low8Bits()

        state.c = sum > 0xFF
        state.v = ((a xor result) and (value xor result)).isNegative8Bit()
        state.z = result == 0
        state.n = result.isNegative8Bit()
        state.a = result
    }

    private fun pcRead(): Int {
        val value = bus.read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

}

