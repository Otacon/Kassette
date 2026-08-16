package nes2.cpu

import nes.util.isNegative8Bit
import nes.util.low16Bits
import nes.util.low8Bits
import nes2.CpuBus

class Cpu6502(
    private val bus: CpuBus,
    private var state: CpuState = CpuState(),
) {

    private val instructions = arrayOfNulls<Instruction>(256)

    init {
        instructions[0x61] = Instruction(Operation.ADC, AddressingMode.INDIRECT_X, 6)
        instructions[0x69] = Instruction(Operation.ADC, AddressingMode.IMMEDIATE, 2)
        instructions[0x65] = Instruction(Operation.ADC, AddressingMode.ZERO_PAGE, 3)
        instructions[0x6D] = Instruction(Operation.ADC, AddressingMode.ABSOLUTE, 4)
        instructions[0x71] = Instruction(Operation.ADC, AddressingMode.INDIRECT_Y, 5)
        instructions[0x75] = Instruction(Operation.ADC, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x79] = Instruction(Operation.ADC, AddressingMode.ABSOLUTE_Y, 4)
        instructions[0x7D] = Instruction(Operation.ADC, AddressingMode.ABSOLUTE_X, 4)
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
            Operation.ADC -> when (instruction.addressingMode) {
                AddressingMode.IMMEDIATE -> adcImmediate()
                AddressingMode.ZERO_PAGE -> adcZeroPage()
                AddressingMode.ZERO_PAGE_X -> TODO()


                AddressingMode.ABSOLUTE -> TODO()
                AddressingMode.ABSOLUTE_X -> TODO()
                AddressingMode.ABSOLUTE_Y -> TODO()

                AddressingMode.INDIRECT_X -> TODO()
                AddressingMode.INDIRECT_Y -> TODO()

                AddressingMode.ZERO_PAGE_Y,
                AddressingMode.IMPLIED,
                AddressingMode.ACCUMULATOR,
                AddressingMode.RELATIVE,
                AddressingMode.INDIRECT -> throw IllegalArgumentException("Addressing mode ${instruction.addressingMode} is not supported for ADC")
            }
        }
    }

    /**
     * ADC
     */
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

    private fun adcImmediate() {
        val value = pcRead()
        adc(value)
    }

    private fun adcZeroPage() {
        val address = pcRead()
        val value = bus.read(address)
        adc(value)
    }


    private fun pcRead(): Int {
        val value = bus.read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

}

