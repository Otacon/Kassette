package nes2.cpu

import nes.util.isNegative8Bit
import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.pageBase
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
        instructions[0x7D] = Instruction(Operation.ADC, ABSOLUTE_X, 4)
        instructions[0x79] = Instruction(Operation.ADC, ABSOLUTE_Y, 4)

        // AND
        instructions[0x29] = Instruction(Operation.AND, IMMEDIATE, 2)
        instructions[0x25] = Instruction(Operation.AND, ZERO_PAGE, 3)
        instructions[0x35] = Instruction(Operation.AND, ZERO_PAGE_X, 4)
        instructions[0x21] = Instruction(Operation.AND, INDIRECT_X, 6)
        instructions[0x2D] = Instruction(Operation.AND, ABSOLUTE, 4)
        instructions[0x31] = Instruction(Operation.AND, INDIRECT_Y, 5)
        instructions[0x3D] = Instruction(Operation.AND, ABSOLUTE_X, 4)
        instructions[0x39] = Instruction(Operation.AND, ABSOLUTE_Y, 4)
    }

    fun reset() {
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
    }

    fun step(): Int {
        val opCode = pcRead()
        val instruction = instructions[opCode] ?: throw IllegalArgumentException("Instruction#$opCode not found")
        return execute(instruction)
    }

    private fun execute(instruction: Instruction): Int {
        return when (instruction.operation) {
            Operation.ADC -> {
                val pageCrossingPenalty = pageCrossingPenalty(instruction.addressingMode)
                val operand = readOperand(instruction.addressingMode)
                adc(operand)
                instruction.baseCycles + pageCrossingPenalty
            }

            Operation.AND -> {
                val pageCrossingPenalty = pageCrossingPenalty(instruction.addressingMode)
                val operand = readOperand(instruction.addressingMode)
                and(operand)
                instruction.baseCycles + pageCrossingPenalty
            }
        }
    }

    // region Addressing modes
    private fun readOperand(mode: AddressingMode): Int {
        return when (mode) {
            IMMEDIATE -> {
                pcRead()
            }

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

            ABSOLUTE_X -> {
                val lo = pcRead()
                val hi = pcRead()
                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.x).low16Bits()
                bus.read(address)
            }

            ABSOLUTE_Y -> {
                val lo = pcRead()
                val hi = pcRead()
                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()
                bus.read(address)
            }

            INDIRECT_X -> {
                val pointer = (pcRead() + state.x).low8Bits()
                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())
                val address = lo or (hi shl 8)
                bus.read(address)
            }

            INDIRECT_Y -> {
                val pointer = pcRead()

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()

                bus.read(address)
            }

            IMPLIED -> TODO()
            ACCUMULATOR -> TODO()
            ZERO_PAGE_Y -> TODO()
            RELATIVE -> TODO()
            INDIRECT -> TODO()
        }
    }

    // This function can be collapsed with the one above and not perform the same operation twice.
    // However, for clarity and readability, I'm just keeping it as it is and then improve performance later on.
    private fun pageCrossingPenalty(mode: AddressingMode): Int {
        return when (mode) {
            ABSOLUTE_X -> {
                val lo = bus.read(state.pc)
                val hi = bus.read((state.pc + 1).low16Bits())

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.x).low16Bits()

                if (baseAddress.pageBase() != address.pageBase()) 1 else 0
            }

            ABSOLUTE_Y -> {
                val lo = bus.read(state.pc)
                val hi = bus.read((state.pc + 1).low16Bits())

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()

                if (baseAddress.pageBase() != address.pageBase()) 1 else 0
            }

            INDIRECT_Y -> {
                val pointer = bus.read(state.pc)

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()

                if (baseAddress.pageBase() != address.pageBase()) 1 else 0
            }

            else -> 0
        }
    }

    // endregion

    // region OPs execution

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

    private fun and(value: Int) {
        state.a = (state.a and value).low8Bits()

        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    // endregion

    // region utils
    private fun pcRead(): Int {
        val value = bus.read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

    // endregion

}

