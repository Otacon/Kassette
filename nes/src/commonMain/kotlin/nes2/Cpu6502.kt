package nes2

import nes.util.low16Bits
import nes.util.low8Bits

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
            Operation.ADC -> adc(mode = instruction.addressingMode)
        }
    }

    /**
     * ADC
     */
    private fun adc(mode: AddressingMode) {
        when (mode) {

            AddressingMode.IMMEDIATE -> adcImmediate()
            AddressingMode.ZERO_PAGE -> TODO()
            AddressingMode.ZERO_PAGE_X -> TODO()
            AddressingMode.ZERO_PAGE_Y -> TODO()

            AddressingMode.ABSOLUTE -> TODO()
            AddressingMode.ABSOLUTE_X -> TODO()
            AddressingMode.ABSOLUTE_Y -> TODO()

            AddressingMode.INDIRECT_X -> TODO()
            AddressingMode.INDIRECT_Y -> TODO()
            AddressingMode.IMPLIED,
            AddressingMode.ACCUMULATOR,
            AddressingMode.RELATIVE,
            AddressingMode.INDIRECT -> throw IllegalArgumentException("Addressing mode $mode is not supported for ADC")
        }
    }

    private fun adcImmediate() {
        val value = pcRead()

        val a = state.a
        val carryIn = if (state.c) 1 else 0
        val sum = a + value + carryIn
        val result = sum.low8Bits()

        state.c = sum > 0xFF
        state.v = ((a xor result) and (value xor result) and 0x80) != 0
        state.z = result == 0
        state.n = (result and 0x80) != 0

        state.a = result
    }

    private fun pcRead(): Int {
        val value = bus.read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

}

data class CpuState(
    // Program Counter
    var pc: Int = 0,

    var a: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var sp: Int = 0,

    // Carry
    var c: Boolean = false,
    // Overflow
    var v: Boolean = false,
    // Zero
    var z: Boolean = false,
    // Negative
    var n: Boolean = false,
)

enum class AddressingMode {
    IMPLIED,
    ACCUMULATOR,
    IMMEDIATE,
    ZERO_PAGE,
    ZERO_PAGE_X,
    ZERO_PAGE_Y,
    RELATIVE,
    ABSOLUTE,
    ABSOLUTE_X,
    ABSOLUTE_Y,
    INDIRECT,
    INDIRECT_X,
    INDIRECT_Y
}

data class Instruction(
    val operation: Operation,
    val addressingMode: AddressingMode,
    val baseCycles: Int
)

enum class Operation { ADC }