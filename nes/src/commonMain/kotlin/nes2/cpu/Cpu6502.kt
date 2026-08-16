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

        // ORA
        instructions[0x09] = Instruction(Operation.ORA, IMMEDIATE, 2)
        instructions[0x05] = Instruction(Operation.ORA, ZERO_PAGE, 3)
        instructions[0x15] = Instruction(Operation.ORA, ZERO_PAGE_X, 4)
        instructions[0x01] = Instruction(Operation.ORA, INDIRECT_X, 6)
        instructions[0x0D] = Instruction(Operation.ORA, ABSOLUTE, 4)
        instructions[0x11] = Instruction(Operation.ORA, INDIRECT_Y, 5)
        instructions[0x1D] = Instruction(Operation.ORA, ABSOLUTE_X, 4)
        instructions[0x19] = Instruction(Operation.ORA, ABSOLUTE_Y, 4)

        // EOR
        instructions[0x49] = Instruction(Operation.EOR, IMMEDIATE, 2)
        instructions[0x45] = Instruction(Operation.EOR, ZERO_PAGE, 3)
        instructions[0x55] = Instruction(Operation.EOR, ZERO_PAGE_X, 4)
        instructions[0x41] = Instruction(Operation.EOR, INDIRECT_X, 6)
        instructions[0x4D] = Instruction(Operation.EOR, ABSOLUTE, 4)
        instructions[0x51] = Instruction(Operation.EOR, INDIRECT_Y, 5)
        instructions[0x5D] = Instruction(Operation.EOR, ABSOLUTE_X, 4)
        instructions[0x59] = Instruction(Operation.EOR, ABSOLUTE_Y, 4)

        // LDA
        instructions[0xA9] = Instruction(Operation.LDA, IMMEDIATE, 2)
        instructions[0xA5] = Instruction(Operation.LDA, ZERO_PAGE, 3)
        instructions[0xB5] = Instruction(Operation.LDA, ZERO_PAGE_X, 4)
        instructions[0xA1] = Instruction(Operation.LDA, INDIRECT_X, 6)
        instructions[0xAD] = Instruction(Operation.LDA, ABSOLUTE, 4)
        instructions[0xB1] = Instruction(Operation.LDA, INDIRECT_Y, 5)
        instructions[0xBD] = Instruction(Operation.LDA, ABSOLUTE_X, 4)
        instructions[0xB9] = Instruction(Operation.LDA, ABSOLUTE_Y, 4)

        // CMP
        instructions[0xC9] = Instruction(Operation.CMP, IMMEDIATE, 2)
        instructions[0xC5] = Instruction(Operation.CMP, ZERO_PAGE, 3)
        instructions[0xD5] = Instruction(Operation.CMP, ZERO_PAGE_X, 4)
        instructions[0xC1] = Instruction(Operation.CMP, INDIRECT_X, 6)
        instructions[0xCD] = Instruction(Operation.CMP, ABSOLUTE, 4)
        instructions[0xD1] = Instruction(Operation.CMP, INDIRECT_Y, 5)
        instructions[0xDD] = Instruction(Operation.CMP, ABSOLUTE_X, 4)
        instructions[0xD9] = Instruction(Operation.CMP, ABSOLUTE_Y, 4)

        // SBC
        instructions[0xE9] = Instruction(Operation.SBC, IMMEDIATE, 2)
        instructions[0xE5] = Instruction(Operation.SBC, ZERO_PAGE, 3)
        instructions[0xF5] = Instruction(Operation.SBC, ZERO_PAGE_X, 4)
        instructions[0xE1] = Instruction(Operation.SBC, INDIRECT_X, 6)
        instructions[0xED] = Instruction(Operation.SBC, ABSOLUTE, 4)
        instructions[0xF1] = Instruction(Operation.SBC, INDIRECT_Y, 5)
        instructions[0xFD] = Instruction(Operation.SBC, ABSOLUTE_X, 4)
        instructions[0xF9] = Instruction(Operation.SBC, ABSOLUTE_Y, 4)

        // LDX
        instructions[0xA2] = Instruction(Operation.LDX, IMMEDIATE, 2)
        instructions[0xA6] = Instruction(Operation.LDX, ZERO_PAGE, 3)
        instructions[0xB6] = Instruction(Operation.LDX, ZERO_PAGE_Y, 4)
        instructions[0xAE] = Instruction(Operation.LDX, ABSOLUTE, 4)
        instructions[0xBE] = Instruction(Operation.LDX, ABSOLUTE_Y, 4)

        // LDY
        instructions[0xA0] = Instruction(Operation.LDY, IMMEDIATE, 2)
        instructions[0xA4] = Instruction(Operation.LDY, ZERO_PAGE, 3)
        instructions[0xB4] = Instruction(Operation.LDY, ZERO_PAGE_X, 4)
        instructions[0xAC] = Instruction(Operation.LDY, ABSOLUTE, 4)
        instructions[0xBC] = Instruction(Operation.LDY, ABSOLUTE_X, 4)

        // STA
        instructions[0x85] = Instruction(Operation.STA, ZERO_PAGE, 3)
        instructions[0x95] = Instruction(Operation.STA, ZERO_PAGE_X, 4)
        instructions[0x8D] = Instruction(Operation.STA, ABSOLUTE, 4)
        instructions[0x9D] = Instruction(Operation.STA, ABSOLUTE_X, 5)
        instructions[0x99] = Instruction(Operation.STA, ABSOLUTE_Y, 5)
        instructions[0x81] = Instruction(Operation.STA, INDIRECT_X, 6)
        instructions[0x91] = Instruction(Operation.STA, INDIRECT_Y, 6)

        // STX
        instructions[0x86] = Instruction(Operation.STX, ZERO_PAGE, 3)
        instructions[0x96] = Instruction(Operation.STX, ZERO_PAGE_Y, 4)
        instructions[0x8E] = Instruction(Operation.STX, ABSOLUTE, 4)

        // STY
        instructions[0x84] = Instruction(Operation.STY, ZERO_PAGE, 3)
        instructions[0x94] = Instruction(Operation.STY, ZERO_PAGE_X, 4)
        instructions[0x8C] = Instruction(Operation.STY, ABSOLUTE, 4)

    }

    fun reset() {
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
    }

    fun step(): Int {
        val opCode = pcRead()
        val instruction = instructions[opCode] ?: throw IllegalArgumentException("Instruction#$opCode not found")
        return execute(instruction)
    }

    // If performance is needed, use a plain INT and do all you need to do plainly without relying on
    // data representation. It's ugly AF, but it is more performant.
    private fun execute(instruction: Instruction): Int {
        val pageCrossingPenalty = instruction.operation.pageCrossingPenalty
        val cyclesPenalty = if (pageCrossingPenalty) pageCrossingPenalty(instruction.addressingMode) else 0
        when (instruction.operation) {
            Operation.ADC -> {
                val operand = readOperand(instruction.addressingMode)
                adc(operand)
            }

            Operation.AND -> {
                val operand = readOperand(instruction.addressingMode)
                and(operand)
            }

            Operation.ORA -> {
                val operand = readOperand(instruction.addressingMode)
                ora(operand)
            }

            Operation.EOR -> {
                val operand = readOperand(instruction.addressingMode)
                eor(operand)
            }

            Operation.LDA -> {
                val operand = readOperand(instruction.addressingMode)
                lda(operand)
            }

            Operation.CMP -> {
                val operand = readOperand(instruction.addressingMode)
                cmp(operand)
            }

            Operation.SBC -> {
                val operand = readOperand(instruction.addressingMode)
                sbc(operand)
            }

            Operation.LDX -> {
                val operand = readOperand(instruction.addressingMode)
                ldx(operand)
            }

            Operation.LDY -> {
                val operand = readOperand(instruction.addressingMode)
                ldy(operand)
            }

            Operation.STA -> {
                val address = resolveAddress(instruction.addressingMode)
                sta(address)
            }

            Operation.STX -> {
                val address = resolveAddress(instruction.addressingMode)
                stx(address)
            }

            Operation.STY -> {
                val address = resolveAddress(instruction.addressingMode)
                sty(address)
            }
        }
        return instruction.baseCycles + cyclesPenalty
    }

    // region Addressing modes
    private fun readOperand(mode: AddressingMode): Int {
        return when (mode) {
            IMMEDIATE -> pcRead()
            else -> bus.read(resolveAddress(mode))
        }
    }

    private fun resolveAddress(mode: AddressingMode): Int {
        return when (mode) {
            ZERO_PAGE -> {
                pcRead()
            }

            ZERO_PAGE_X -> {
                (pcRead() + state.x).low8Bits()
            }

            ZERO_PAGE_Y -> {
                (pcRead() + state.y).low8Bits()
            }

            ABSOLUTE -> {
                val lo = pcRead()
                val hi = pcRead()

                lo or (hi shl 8)
            }

            ABSOLUTE_X -> {
                val lo = pcRead()
                val hi = pcRead()

                val baseAddress = lo or (hi shl 8)

                (baseAddress + state.x).low16Bits()
            }

            ABSOLUTE_Y -> {
                val lo = pcRead()
                val hi = pcRead()

                val baseAddress = lo or (hi shl 8)

                (baseAddress + state.y).low16Bits()
            }

            INDIRECT_X -> {
                val pointer = (pcRead() + state.x).low8Bits()

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                lo or (hi shl 8)
            }

            INDIRECT_Y -> {
                val pointer = pcRead()

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                val baseAddress = lo or (hi shl 8)

                (baseAddress + state.y).low16Bits()
            }

            IMMEDIATE,
            IMPLIED,
            ACCUMULATOR,
            RELATIVE,
            INDIRECT -> throw IllegalArgumentException(
                "Addressing mode $mode cannot resolve a data address"
            )
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

    private fun ora(value: Int) {
        state.a = (state.a or value).low8Bits()
        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun eor(value: Int) {
        state.a = (state.a xor value).low8Bits()
        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun lda(value: Int) {
        state.a = value.low8Bits()
        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun ldx(value: Int) {
        state.x = value.low8Bits()
        state.z = state.x == 0
        state.n = state.x.isNegative8Bit()
    }

    private fun cmp(value: Int) {
        val result = (state.a - value).low8Bits()
        state.c = state.a >= value
        state.z = state.a == value
        state.n = result.isNegative8Bit()
    }

    private fun sbc(value: Int) {
        val a = state.a
        val borrow = if (state.c) 0 else 1
        val difference = a - value - borrow
        val result = difference.low8Bits()
        state.c = difference >= 0
        state.v = ((a xor value) and (a xor result)).isNegative8Bit()
        state.z = result == 0
        state.n = result.isNegative8Bit()
        state.a = result
    }

    private fun ldy(value: Int) {
        state.y = value.low8Bits()
        state.z = state.y == 0
        state.n = state.y.isNegative8Bit()
    }

    private fun sta(address: Int) {
        bus.write(address, state.a)
    }

    private fun stx(address: Int) {
        bus.write(address, state.x)
    }

    private fun sty(address: Int) {
        bus.write(address, state.y)
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

