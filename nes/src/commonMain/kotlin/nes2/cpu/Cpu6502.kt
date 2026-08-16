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

        instructions[0xAA] = Instruction(Operation.TAX, IMPLIED, 2)
        instructions[0xA8] = Instruction(Operation.TAY, IMPLIED, 2)
        instructions[0x8A] = Instruction(Operation.TXA, IMPLIED, 2)
        instructions[0x98] = Instruction(Operation.TYA, IMPLIED, 2)
        instructions[0xBA] = Instruction(Operation.TSX, IMPLIED, 2)
        instructions[0x9A] = Instruction(Operation.TXS, IMPLIED, 2)
        instructions[0xE8] = Instruction(Operation.INX, IMPLIED, 2)
        instructions[0xC8] = Instruction(Operation.INY, IMPLIED, 2)
        instructions[0xCA] = Instruction(Operation.DEX, IMPLIED, 2)
        instructions[0x88] = Instruction(Operation.DEY, IMPLIED, 2)

        // CPX
        instructions[0xE0] = Instruction(Operation.CPX, IMMEDIATE, 2)
        instructions[0xE4] = Instruction(Operation.CPX, ZERO_PAGE, 3)
        instructions[0xEC] = Instruction(Operation.CPX, ABSOLUTE, 4)

        // CPY
        instructions[0xC0] = Instruction(Operation.CPY, IMMEDIATE, 2)
        instructions[0xC4] = Instruction(Operation.CPY, ZERO_PAGE, 3)
        instructions[0xCC] = Instruction(Operation.CPY, ABSOLUTE, 4)

        // BIT
        instructions[0x24] = Instruction(Operation.BIT, ZERO_PAGE, 3)
        instructions[0x2C] = Instruction(Operation.BIT, ABSOLUTE, 4)

        instructions[0x18] = Instruction(Operation.CLC, IMPLIED, 2)
        instructions[0x38] = Instruction(Operation.SEC, IMPLIED, 2)
        instructions[0x58] = Instruction(Operation.CLI, IMPLIED, 2)
        instructions[0x78] = Instruction(Operation.SEI, IMPLIED, 2)
        instructions[0xB8] = Instruction(Operation.CLV, IMPLIED, 2)
        instructions[0xD8] = Instruction(Operation.CLD, IMPLIED, 2)
        instructions[0xF8] = Instruction(Operation.SED, IMPLIED, 2)

        // INC
        instructions[0xE6] = Instruction(Operation.INC, ZERO_PAGE, 5)
        instructions[0xF6] = Instruction(Operation.INC, ZERO_PAGE_X, 6)
        instructions[0xEE] = Instruction(Operation.INC, ABSOLUTE, 6)
        instructions[0xFE] = Instruction(Operation.INC, ABSOLUTE_X, 7)

        // DEC
        instructions[0xC6] = Instruction(Operation.DEC, ZERO_PAGE, 5)
        instructions[0xD6] = Instruction(Operation.DEC, ZERO_PAGE_X, 6)
        instructions[0xCE] = Instruction(Operation.DEC, ABSOLUTE, 6)
        instructions[0xDE] = Instruction(Operation.DEC, ABSOLUTE_X, 7)

        // ASL
        instructions[0x0A] = Instruction(Operation.ASL, ACCUMULATOR, 2)
        instructions[0x06] = Instruction(Operation.ASL, ZERO_PAGE, 5)
        instructions[0x16] = Instruction(Operation.ASL, ZERO_PAGE_X, 6)
        instructions[0x0E] = Instruction(Operation.ASL, ABSOLUTE, 6)
        instructions[0x1E] = Instruction(Operation.ASL, ABSOLUTE_X, 7)

        // LSR
        instructions[0x4A] = Instruction(Operation.LSR, ACCUMULATOR, 2)
        instructions[0x46] = Instruction(Operation.LSR, ZERO_PAGE, 5)
        instructions[0x56] = Instruction(Operation.LSR, ZERO_PAGE_X, 6)
        instructions[0x4E] = Instruction(Operation.LSR, ABSOLUTE, 6)
        instructions[0x5E] = Instruction(Operation.LSR, ABSOLUTE_X, 7)

        // ROL
        instructions[0x2A] = Instruction(Operation.ROL, ACCUMULATOR, 2)
        instructions[0x26] = Instruction(Operation.ROL, ZERO_PAGE, 5)
        instructions[0x36] = Instruction(Operation.ROL, ZERO_PAGE_X, 6)
        instructions[0x2E] = Instruction(Operation.ROL, ABSOLUTE, 6)
        instructions[0x3E] = Instruction(Operation.ROL, ABSOLUTE_X, 7)

        // ROR
        instructions[0x6A] = Instruction(Operation.ROR, ACCUMULATOR, 2)
        instructions[0x66] = Instruction(Operation.ROR, ZERO_PAGE, 5)
        instructions[0x76] = Instruction(Operation.ROR, ZERO_PAGE_X, 6)
        instructions[0x6E] = Instruction(Operation.ROR, ABSOLUTE, 6)
        instructions[0x7E] = Instruction(Operation.ROR, ABSOLUTE_X, 7)

        // Branching
        instructions[0x90] = Instruction(Operation.BCC, RELATIVE, 2)
        instructions[0xB0] = Instruction(Operation.BCS, RELATIVE, 2)

        instructions[0xF0] = Instruction(Operation.BEQ, RELATIVE, 2)
        instructions[0xD0] = Instruction(Operation.BNE, RELATIVE, 2)

        instructions[0x30] = Instruction(Operation.BMI, RELATIVE, 2)
        instructions[0x10] = Instruction(Operation.BPL, RELATIVE, 2)

        instructions[0x50] = Instruction(Operation.BVC, RELATIVE, 2)
        instructions[0x70] = Instruction(Operation.BVS, RELATIVE, 2)

        // JMP
        instructions[0x4C] = Instruction(Operation.JMP, ABSOLUTE, 3)
        instructions[0x6C] = Instruction(Operation.JMP, INDIRECT, 5)
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
        var cyclesPenalty = if (pageCrossingPenalty) pageCrossingPenalty(instruction.addressingMode) else 0
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

            Operation.TAX -> {
                tax()
            }

            Operation.TAY -> {
                tay()
            }

            Operation.TXA -> {
                txa()
            }

            Operation.TYA -> {
                tya()
            }

            Operation.TSX -> {
                tsx()
            }

            Operation.TXS -> {
                txs()
            }

            Operation.INX -> {
                inx()
            }

            Operation.INY -> {
                iny()
            }

            Operation.DEX -> {
                dex()
            }

            Operation.DEY -> {
                dey()
            }

            Operation.CPX -> {
                val operand = readOperand(instruction.addressingMode)
                cpx(operand)
            }

            Operation.CPY -> {
                val operand = readOperand(instruction.addressingMode)
                cpy(operand)
            }

            Operation.BIT -> {
                val operand = readOperand(instruction.addressingMode)
                bit(operand)
            }

            Operation.CLC -> {
                clc()
            }

            Operation.SEC -> {
                sec()
            }

            Operation.CLI -> {
                cli()
            }

            Operation.SEI -> {
                sei()
            }

            Operation.CLV -> {
                clv()
            }

            Operation.CLD -> {
                cld()
            }

            Operation.SED -> {
                sed()
            }

            Operation.INC -> {
                val address = resolveAddress(instruction.addressingMode)
                inc(address)
            }

            Operation.DEC -> {
                val address = resolveAddress(instruction.addressingMode)
                dec(address)
            }

            Operation.ASL -> {
                asl(instruction.addressingMode)
            }

            Operation.LSR -> {
                lsr(instruction.addressingMode)
            }

            Operation.ROL -> {
                rol(instruction.addressingMode)
            }

            Operation.ROR -> {
                ror(instruction.addressingMode)
            }

            Operation.BCC -> {
                cyclesPenalty += branch(!state.c)
            }

            Operation.BCS -> {
                cyclesPenalty += branch(state.c)
            }

            Operation.BEQ -> {
                cyclesPenalty += branch(state.z)
            }

            Operation.BNE -> {
                cyclesPenalty += branch(!state.z)
            }

            Operation.BMI -> {
                cyclesPenalty += branch(state.n)
            }

            Operation.BPL -> {
                cyclesPenalty += branch(!state.n)
            }

            Operation.BVC -> {
                cyclesPenalty += branch(!state.v)
            }

            Operation.BVS -> {
                cyclesPenalty += branch(state.v)
            }

            Operation.JMP -> {
                jmp(instruction.addressingMode)
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

    private fun tax() {
        state.x = state.a.low8Bits()
        state.z = state.x == 0
        state.n = state.x.isNegative8Bit()
    }

    private fun tay() {
        state.y = state.a.low8Bits()
        state.z = state.y == 0
        state.n = state.y.isNegative8Bit()
    }

    private fun txa() {
        state.a = state.x.low8Bits()
        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun tya() {
        state.a = state.y.low8Bits()
        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun tsx() {
        state.x = state.sp.low8Bits()
        state.z = state.x == 0
        state.n = state.x.isNegative8Bit()
    }

    private fun txs() {
        state.sp = state.x.low8Bits()
    }

    private fun inx() {
        state.x = (state.x + 1).low8Bits()
        state.z = state.x == 0
        state.n = state.x.isNegative8Bit()
    }

    private fun iny() {
        state.y = (state.y + 1).low8Bits()
        state.z = state.y == 0
        state.n = state.y.isNegative8Bit()
    }

    private fun dex() {
        state.x = (state.x - 1).low8Bits()
        state.z = state.x == 0
        state.n = state.x.isNegative8Bit()
    }

    private fun dey() {
        state.y = (state.y - 1).low8Bits()
        state.z = state.y == 0
        state.n = state.y.isNegative8Bit()
    }

    private fun cpx(value: Int) {
        val result = (state.x - value).low8Bits()

        state.c = state.x >= value
        state.z = state.x == value
        state.n = result.isNegative8Bit()
    }

    private fun cpy(value: Int) {
        val result = (state.y - value).low8Bits()

        state.c = state.y >= value
        state.z = state.y == value
        state.n = result.isNegative8Bit()
    }

    private fun bit(value: Int) {
        state.z = (state.a and value).low8Bits() == 0
        state.v = (value and 0x40) != 0
        state.n = value.isNegative8Bit()
    }

    private fun clc() {
        state.c = false
    }

    private fun sec() {
        state.c = true
    }

    private fun cli() {
        state.i = false
    }

    private fun sei() {
        state.i = true
    }

    private fun clv() {
        state.v = false
    }

    private fun cld() {
        state.d = false
    }

    private fun sed() {
        state.d = true
    }

    private fun inc(address: Int) {
        val result = (bus.read(address) + 1).low8Bits()

        bus.write(address, result)

        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun dec(address: Int) {
        val result = (bus.read(address) - 1).low8Bits()

        bus.write(address, result)

        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun asl(mode: AddressingMode) {
        if (mode == ACCUMULATOR) {
            state.a = aslValue(state.a)
        } else {
            val address = resolveAddress(mode)
            bus.write(address, aslValue(bus.read(address)))
        }
    }

    private fun aslValue(value: Int): Int {
        state.c = value.isNegative8Bit()

        val result = (value shl 1).low8Bits()

        state.z = result == 0
        state.n = result.isNegative8Bit()

        return result
    }

    private fun lsr(mode: AddressingMode) {
        if (mode == ACCUMULATOR) {
            state.a = lsrValue(state.a)
        } else {
            val address = resolveAddress(mode)
            bus.write(address, lsrValue(bus.read(address)))
        }
    }

    private fun lsrValue(value: Int): Int {
        state.c = (value and 0x01) != 0

        val result = value ushr 1

        state.z = result == 0
        state.n = result.isNegative8Bit()

        return result
    }

    private fun rol(mode: AddressingMode) {
        if (mode == ACCUMULATOR) {
            state.a = rolValue(state.a)
        } else {
            val address = resolveAddress(mode)
            bus.write(address, rolValue(bus.read(address)))
        }
    }

    private fun rolValue(value: Int): Int {
        val carryIn = if (state.c) 1 else 0
        val carryOut = value.isNegative8Bit()

        val result = ((value shl 1) or carryIn).low8Bits()

        state.c = carryOut
        state.z = result == 0
        state.n = result.isNegative8Bit()

        return result
    }

    private fun ror(mode: AddressingMode) {
        if (mode == ACCUMULATOR) {
            state.a = rorValue(state.a)
        } else {
            val address = resolveAddress(mode)
            bus.write(address, rorValue(bus.read(address)))
        }
    }

    private fun rorValue(value: Int): Int {
        val carryIn = if (state.c) 0x80 else 0
        val carryOut = (value and 0x01) != 0

        val result = ((value ushr 1) or carryIn).low8Bits()

        state.c = carryOut
        state.z = result == 0
        state.n = result.isNegative8Bit()

        return result
    }

    private fun branch(condition: Boolean): Int {
        val offset = readRelativeOffset()

        if (!condition) {
            return 0
        }

        val oldPc = state.pc
        val newPc = (oldPc + offset).low16Bits()

        state.pc = newPc

        return if (oldPc.pageBase() != newPc.pageBase()) 2 else 1
    }

    private fun jmp(mode: AddressingMode) {
        state.pc = when (mode) {
            ABSOLUTE -> {
                val lo = pcRead()
                val hi = pcRead()

                lo or (hi shl 8)
            }

            INDIRECT -> {
                val lo = pcRead()
                val hi = pcRead()
                val pointer = lo or (hi shl 8)

                val targetLo = bus.read(pointer)

                val targetHiAddress =
                    if ((pointer and 0x00FF) == 0x00FF) {
                        // 6502 hardware bug:
                        // $12FF reads high byte from $1200, not $1300.
                        pointer and 0xFF00
                    } else {
                        (pointer + 1).low16Bits()
                    }

                val targetHi = bus.read(targetHiAddress)

                targetLo or (targetHi shl 8)
            }

            else -> throw IllegalArgumentException(
                "Unsupported JMP addressing mode: $mode"
            )
        }
    }

    // endregion

    // region utils
    private fun pcRead(): Int {
        val value = bus.read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

    private fun readRelativeOffset(): Int {
        val value = pcRead()

        return if (value < 0x80) value else value - 0x100
    }

    // endregion

}

