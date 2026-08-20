package nes2.cpu

import nes.util.isNegative8Bit
import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.pageBase
import nes2.CpuBus

interface Cpu {
    fun reset(): Int
    fun setIrqLine(active: Boolean)
    fun requestNmi()
    fun step(): Int
}

class Cpu6502(
    private val bus: CpuBus,
    private var state: CpuState = CpuState(),
) : Cpu {

    private val instructions = Array(256) { Instruction(Operation.NOP, AddressingMode.IMPLIED, 2) }
    private var pageCrossed = false

    init {
        // ADC
        instructions[0x69] = Instruction(Operation.ADC, AddressingMode.IMMEDIATE, 2)
        instructions[0x65] = Instruction(Operation.ADC, AddressingMode.ZERO_PAGE, 3)
        instructions[0x75] = Instruction(Operation.ADC, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x61] = Instruction(Operation.ADC, AddressingMode.INDIRECT_X, 6)
        instructions[0x6D] = Instruction(Operation.ADC, AddressingMode.ABSOLUTE, 4)
        instructions[0x71] = Instruction(Operation.ADC, AddressingMode.INDIRECT_Y, 5)
        instructions[0x7D] = Instruction(Operation.ADC, AddressingMode.ABSOLUTE_X, 4)
        instructions[0x79] = Instruction(Operation.ADC, AddressingMode.ABSOLUTE_Y, 4)

        // AND
        instructions[0x29] = Instruction(Operation.AND, AddressingMode.IMMEDIATE, 2)
        instructions[0x25] = Instruction(Operation.AND, AddressingMode.ZERO_PAGE, 3)
        instructions[0x35] = Instruction(Operation.AND, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x21] = Instruction(Operation.AND, AddressingMode.INDIRECT_X, 6)
        instructions[0x2D] = Instruction(Operation.AND, AddressingMode.ABSOLUTE, 4)
        instructions[0x31] = Instruction(Operation.AND, AddressingMode.INDIRECT_Y, 5)
        instructions[0x3D] = Instruction(Operation.AND, AddressingMode.ABSOLUTE_X, 4)
        instructions[0x39] = Instruction(Operation.AND, AddressingMode.ABSOLUTE_Y, 4)

        // ORA
        instructions[0x09] = Instruction(Operation.ORA, AddressingMode.IMMEDIATE, 2)
        instructions[0x05] = Instruction(Operation.ORA, AddressingMode.ZERO_PAGE, 3)
        instructions[0x15] = Instruction(Operation.ORA, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x01] = Instruction(Operation.ORA, AddressingMode.INDIRECT_X, 6)
        instructions[0x0D] = Instruction(Operation.ORA, AddressingMode.ABSOLUTE, 4)
        instructions[0x11] = Instruction(Operation.ORA, AddressingMode.INDIRECT_Y, 5)
        instructions[0x1D] = Instruction(Operation.ORA, AddressingMode.ABSOLUTE_X, 4)
        instructions[0x19] = Instruction(Operation.ORA, AddressingMode.ABSOLUTE_Y, 4)

        // EOR
        instructions[0x49] = Instruction(Operation.EOR, AddressingMode.IMMEDIATE, 2)
        instructions[0x45] = Instruction(Operation.EOR, AddressingMode.ZERO_PAGE, 3)
        instructions[0x55] = Instruction(Operation.EOR, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x41] = Instruction(Operation.EOR, AddressingMode.INDIRECT_X, 6)
        instructions[0x4D] = Instruction(Operation.EOR, AddressingMode.ABSOLUTE, 4)
        instructions[0x51] = Instruction(Operation.EOR, AddressingMode.INDIRECT_Y, 5)
        instructions[0x5D] = Instruction(Operation.EOR, AddressingMode.ABSOLUTE_X, 4)
        instructions[0x59] = Instruction(Operation.EOR, AddressingMode.ABSOLUTE_Y, 4)

        // LDA
        instructions[0xA9] = Instruction(Operation.LDA, AddressingMode.IMMEDIATE, 2)
        instructions[0xA5] = Instruction(Operation.LDA, AddressingMode.ZERO_PAGE, 3)
        instructions[0xB5] = Instruction(Operation.LDA, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0xA1] = Instruction(Operation.LDA, AddressingMode.INDIRECT_X, 6)
        instructions[0xAD] = Instruction(Operation.LDA, AddressingMode.ABSOLUTE, 4)
        instructions[0xB1] = Instruction(Operation.LDA, AddressingMode.INDIRECT_Y, 5)
        instructions[0xBD] = Instruction(Operation.LDA, AddressingMode.ABSOLUTE_X, 4)
        instructions[0xB9] = Instruction(Operation.LDA, AddressingMode.ABSOLUTE_Y, 4)

        // CMP
        instructions[0xC9] = Instruction(Operation.CMP, AddressingMode.IMMEDIATE, 2)
        instructions[0xC5] = Instruction(Operation.CMP, AddressingMode.ZERO_PAGE, 3)
        instructions[0xD5] = Instruction(Operation.CMP, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0xC1] = Instruction(Operation.CMP, AddressingMode.INDIRECT_X, 6)
        instructions[0xCD] = Instruction(Operation.CMP, AddressingMode.ABSOLUTE, 4)
        instructions[0xD1] = Instruction(Operation.CMP, AddressingMode.INDIRECT_Y, 5)
        instructions[0xDD] = Instruction(Operation.CMP, AddressingMode.ABSOLUTE_X, 4)
        instructions[0xD9] = Instruction(Operation.CMP, AddressingMode.ABSOLUTE_Y, 4)

        // SBC
        instructions[0xE9] = Instruction(Operation.SBC, AddressingMode.IMMEDIATE, 2)
        instructions[0xE5] = Instruction(Operation.SBC, AddressingMode.ZERO_PAGE, 3)
        instructions[0xF5] = Instruction(Operation.SBC, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0xE1] = Instruction(Operation.SBC, AddressingMode.INDIRECT_X, 6)
        instructions[0xED] = Instruction(Operation.SBC, AddressingMode.ABSOLUTE, 4)
        instructions[0xF1] = Instruction(Operation.SBC, AddressingMode.INDIRECT_Y, 5)
        instructions[0xFD] = Instruction(Operation.SBC, AddressingMode.ABSOLUTE_X, 4)
        instructions[0xF9] = Instruction(Operation.SBC, AddressingMode.ABSOLUTE_Y, 4)

        // LDX
        instructions[0xA2] = Instruction(Operation.LDX, AddressingMode.IMMEDIATE, 2)
        instructions[0xA6] = Instruction(Operation.LDX, AddressingMode.ZERO_PAGE, 3)
        instructions[0xB6] = Instruction(Operation.LDX, AddressingMode.ZERO_PAGE_Y, 4)
        instructions[0xAE] = Instruction(Operation.LDX, AddressingMode.ABSOLUTE, 4)
        instructions[0xBE] = Instruction(Operation.LDX, AddressingMode.ABSOLUTE_Y, 4)

        // LDY
        instructions[0xA0] = Instruction(Operation.LDY, AddressingMode.IMMEDIATE, 2)
        instructions[0xA4] = Instruction(Operation.LDY, AddressingMode.ZERO_PAGE, 3)
        instructions[0xB4] = Instruction(Operation.LDY, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0xAC] = Instruction(Operation.LDY, AddressingMode.ABSOLUTE, 4)
        instructions[0xBC] = Instruction(Operation.LDY, AddressingMode.ABSOLUTE_X, 4)

        // STA
        instructions[0x85] = Instruction(Operation.STA, AddressingMode.ZERO_PAGE, 3)
        instructions[0x95] = Instruction(Operation.STA, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x8D] = Instruction(Operation.STA, AddressingMode.ABSOLUTE, 4)
        instructions[0x9D] = Instruction(Operation.STA, AddressingMode.ABSOLUTE_X, 5)
        instructions[0x99] = Instruction(Operation.STA, AddressingMode.ABSOLUTE_Y, 5)
        instructions[0x81] = Instruction(Operation.STA, AddressingMode.INDIRECT_X, 6)
        instructions[0x91] = Instruction(Operation.STA, AddressingMode.INDIRECT_Y, 6)

        // STX
        instructions[0x86] = Instruction(Operation.STX, AddressingMode.ZERO_PAGE, 3)
        instructions[0x96] = Instruction(Operation.STX, AddressingMode.ZERO_PAGE_Y, 4)
        instructions[0x8E] = Instruction(Operation.STX, AddressingMode.ABSOLUTE, 4)

        // STY
        instructions[0x84] = Instruction(Operation.STY, AddressingMode.ZERO_PAGE, 3)
        instructions[0x94] = Instruction(Operation.STY, AddressingMode.ZERO_PAGE_X, 4)
        instructions[0x8C] = Instruction(Operation.STY, AddressingMode.ABSOLUTE, 4)

        instructions[0xAA] = Instruction(Operation.TAX, AddressingMode.IMPLIED, 2)
        instructions[0xA8] = Instruction(Operation.TAY, AddressingMode.IMPLIED, 2)
        instructions[0x8A] = Instruction(Operation.TXA, AddressingMode.IMPLIED, 2)
        instructions[0x98] = Instruction(Operation.TYA, AddressingMode.IMPLIED, 2)
        instructions[0xBA] = Instruction(Operation.TSX, AddressingMode.IMPLIED, 2)
        instructions[0x9A] = Instruction(Operation.TXS, AddressingMode.IMPLIED, 2)
        instructions[0xE8] = Instruction(Operation.INX, AddressingMode.IMPLIED, 2)
        instructions[0xC8] = Instruction(Operation.INY, AddressingMode.IMPLIED, 2)
        instructions[0xCA] = Instruction(Operation.DEX, AddressingMode.IMPLIED, 2)
        instructions[0x88] = Instruction(Operation.DEY, AddressingMode.IMPLIED, 2)

        // CPX
        instructions[0xE0] = Instruction(Operation.CPX, AddressingMode.IMMEDIATE, 2)
        instructions[0xE4] = Instruction(Operation.CPX, AddressingMode.ZERO_PAGE, 3)
        instructions[0xEC] = Instruction(Operation.CPX, AddressingMode.ABSOLUTE, 4)

        // CPY
        instructions[0xC0] = Instruction(Operation.CPY, AddressingMode.IMMEDIATE, 2)
        instructions[0xC4] = Instruction(Operation.CPY, AddressingMode.ZERO_PAGE, 3)
        instructions[0xCC] = Instruction(Operation.CPY, AddressingMode.ABSOLUTE, 4)

        // BIT
        instructions[0x24] = Instruction(Operation.BIT, AddressingMode.ZERO_PAGE, 3)
        instructions[0x2C] = Instruction(Operation.BIT, AddressingMode.ABSOLUTE, 4)

        instructions[0x18] = Instruction(Operation.CLC, AddressingMode.IMPLIED, 2)
        instructions[0x38] = Instruction(Operation.SEC, AddressingMode.IMPLIED, 2)
        instructions[0x58] = Instruction(Operation.CLI, AddressingMode.IMPLIED, 2)
        instructions[0x78] = Instruction(Operation.SEI, AddressingMode.IMPLIED, 2)
        instructions[0xB8] = Instruction(Operation.CLV, AddressingMode.IMPLIED, 2)
        instructions[0xD8] = Instruction(Operation.CLD, AddressingMode.IMPLIED, 2)
        instructions[0xF8] = Instruction(Operation.SED, AddressingMode.IMPLIED, 2)

        // INC
        instructions[0xE6] = Instruction(Operation.INC, AddressingMode.ZERO_PAGE, 5)
        instructions[0xF6] = Instruction(Operation.INC, AddressingMode.ZERO_PAGE_X, 6)
        instructions[0xEE] = Instruction(Operation.INC, AddressingMode.ABSOLUTE, 6)
        instructions[0xFE] = Instruction(Operation.INC, AddressingMode.ABSOLUTE_X, 7)

        // DEC
        instructions[0xC6] = Instruction(Operation.DEC, AddressingMode.ZERO_PAGE, 5)
        instructions[0xD6] = Instruction(Operation.DEC, AddressingMode.ZERO_PAGE_X, 6)
        instructions[0xCE] = Instruction(Operation.DEC, AddressingMode.ABSOLUTE, 6)
        instructions[0xDE] = Instruction(Operation.DEC, AddressingMode.ABSOLUTE_X, 7)

        // ASL
        instructions[0x0A] = Instruction(Operation.ASL, AddressingMode.ACCUMULATOR, 2)
        instructions[0x06] = Instruction(Operation.ASL, AddressingMode.ZERO_PAGE, 5)
        instructions[0x16] = Instruction(Operation.ASL, AddressingMode.ZERO_PAGE_X, 6)
        instructions[0x0E] = Instruction(Operation.ASL, AddressingMode.ABSOLUTE, 6)
        instructions[0x1E] = Instruction(Operation.ASL, AddressingMode.ABSOLUTE_X, 7)

        // LSR
        instructions[0x4A] = Instruction(Operation.LSR, AddressingMode.ACCUMULATOR, 2)
        instructions[0x46] = Instruction(Operation.LSR, AddressingMode.ZERO_PAGE, 5)
        instructions[0x56] = Instruction(Operation.LSR, AddressingMode.ZERO_PAGE_X, 6)
        instructions[0x4E] = Instruction(Operation.LSR, AddressingMode.ABSOLUTE, 6)
        instructions[0x5E] = Instruction(Operation.LSR, AddressingMode.ABSOLUTE_X, 7)

        // ROL
        instructions[0x2A] = Instruction(Operation.ROL, AddressingMode.ACCUMULATOR, 2)
        instructions[0x26] = Instruction(Operation.ROL, AddressingMode.ZERO_PAGE, 5)
        instructions[0x36] = Instruction(Operation.ROL, AddressingMode.ZERO_PAGE_X, 6)
        instructions[0x2E] = Instruction(Operation.ROL, AddressingMode.ABSOLUTE, 6)
        instructions[0x3E] = Instruction(Operation.ROL, AddressingMode.ABSOLUTE_X, 7)

        // ROR
        instructions[0x6A] = Instruction(Operation.ROR, AddressingMode.ACCUMULATOR, 2)
        instructions[0x66] = Instruction(Operation.ROR, AddressingMode.ZERO_PAGE, 5)
        instructions[0x76] = Instruction(Operation.ROR, AddressingMode.ZERO_PAGE_X, 6)
        instructions[0x6E] = Instruction(Operation.ROR, AddressingMode.ABSOLUTE, 6)
        instructions[0x7E] = Instruction(Operation.ROR, AddressingMode.ABSOLUTE_X, 7)

        // Branching
        instructions[0x90] = Instruction(Operation.BCC, AddressingMode.RELATIVE, 2)
        instructions[0xB0] = Instruction(Operation.BCS, AddressingMode.RELATIVE, 2)

        instructions[0xF0] = Instruction(Operation.BEQ, AddressingMode.RELATIVE, 2)
        instructions[0xD0] = Instruction(Operation.BNE, AddressingMode.RELATIVE, 2)

        instructions[0x30] = Instruction(Operation.BMI, AddressingMode.RELATIVE, 2)
        instructions[0x10] = Instruction(Operation.BPL, AddressingMode.RELATIVE, 2)

        instructions[0x50] = Instruction(Operation.BVC, AddressingMode.RELATIVE, 2)
        instructions[0x70] = Instruction(Operation.BVS, AddressingMode.RELATIVE, 2)

        // JMP
        instructions[0x4C] = Instruction(Operation.JMP, AddressingMode.ABSOLUTE, 3)
        instructions[0x6C] = Instruction(Operation.JMP, AddressingMode.INDIRECT, 5)

        // JSR
        instructions[0x20] = Instruction(Operation.JSR, AddressingMode.ABSOLUTE, 6)

        // RTS
        instructions[0x60] = Instruction(Operation.RTS, AddressingMode.IMPLIED, 6)

        // Stack
        instructions[0x48] = Instruction(Operation.PHA, AddressingMode.IMPLIED, 3)
        instructions[0x68] = Instruction(Operation.PLA, AddressingMode.IMPLIED, 4)
        instructions[0x08] = Instruction(Operation.PHP, AddressingMode.IMPLIED, 3)
        instructions[0x28] = Instruction(Operation.PLP, AddressingMode.IMPLIED, 4)

        // BRK
        instructions[0x00] = Instruction(Operation.BRK, AddressingMode.IMPLIED, 7)

        // RTI
        instructions[0x40] = Instruction(Operation.RTI, AddressingMode.IMPLIED, 6)

        // NOP
        instructions[0xEA] = Instruction(Operation.NOP, AddressingMode.IMPLIED, 2)
    }

    override fun reset(): Int {
        state.a = 0
        state.x = 0
        state.y = 0
        state.sp = 0xFD
        state.status = 0x24
        state.irqLine = false
        state.nmiPending = false
        state.irqPollI = true
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
        return RESET_CYCLES
    }

    override fun setIrqLine(active: Boolean) {
        state.irqLine = active
    }

    override fun requestNmi() {
        state.nmiPending = true
    }

    override fun step(): Int {
        if (state.nmiPending) {
            state.nmiPending = false
            return nmi()
        }

        if (state.irqLine && !state.i && !state.irqPollI) {
            return irq()
        }

        val opcode = pcRead()
        val instruction = instructions[opcode]

        val iBefore = state.i
        val cycles = execute(instruction)
        state.irqPollI = when (instruction.operation) {
            Operation.CLI, Operation.SEI, Operation.PLP -> iBefore
            else -> state.i
        }

        return cycles
    }

    // If performance is needed, use a plain INT and do all you need to do plainly without relying on
    // data representation. It's ugly AF, but it is more performant.
    private fun execute(instruction: Instruction): Int {
        pageCrossed = false
        var cyclesPenalty = 0
        when (instruction.operation) {
            Operation.ADC -> adc(value = readOperand(instruction.addressingMode, true))
            Operation.AND -> and(value = readOperand(instruction.addressingMode, true))
            Operation.ORA -> ora(value = readOperand(instruction.addressingMode, true))
            Operation.EOR -> eor(value = readOperand(instruction.addressingMode, true))
            Operation.LDA -> lda(value = readOperand(instruction.addressingMode, true))
            Operation.CMP -> cmp(value = readOperand(instruction.addressingMode, true))
            Operation.SBC -> sbc(value = readOperand(instruction.addressingMode, true))
            Operation.LDX -> ldx(value = readOperand(instruction.addressingMode, true))
            Operation.LDY -> ldy(value = readOperand(instruction.addressingMode, true))
            Operation.STA -> sta(address = resolveAddress(instruction.addressingMode, false))
            Operation.STX -> stx(address = resolveAddress(instruction.addressingMode, false))
            Operation.STY -> sty(address = resolveAddress(instruction.addressingMode, false))
            Operation.TAX -> tax()
            Operation.TAY -> tay()
            Operation.TXA -> txa()
            Operation.TYA -> tya()
            Operation.TSX -> tsx()
            Operation.TXS -> txs()
            Operation.INX -> inx()
            Operation.INY -> iny()
            Operation.DEX -> dex()
            Operation.DEY -> dey()
            Operation.CPX -> cpx(value = readOperand(instruction.addressingMode, false))
            Operation.CPY -> cpy(value = readOperand(instruction.addressingMode, false))
            Operation.BIT -> bit(value = readOperand(instruction.addressingMode, false))
            Operation.CLC -> clc()
            Operation.SEC -> sec()
            Operation.CLI -> cli()
            Operation.SEI -> sei()
            Operation.CLV -> clv()
            Operation.CLD -> cld()
            Operation.SED -> sed()
            Operation.INC -> inc(address = resolveAddress(instruction.addressingMode, false))
            Operation.DEC -> dec(address = resolveAddress(instruction.addressingMode, false))
            Operation.ASL -> asl(mode = instruction.addressingMode)
            Operation.LSR -> lsr(mode = instruction.addressingMode)
            Operation.ROL -> rol(mode = instruction.addressingMode)
            Operation.ROR -> ror(mode = instruction.addressingMode)
            Operation.BCC -> cyclesPenalty += branch(!state.c)
            Operation.BCS -> cyclesPenalty += branch(state.c)
            Operation.BEQ -> cyclesPenalty += branch(state.z)
            Operation.BNE -> cyclesPenalty += branch(!state.z)
            Operation.BMI -> cyclesPenalty += branch(state.n)
            Operation.BPL -> cyclesPenalty += branch(!state.n)
            Operation.BVC -> cyclesPenalty += branch(!state.v)
            Operation.BVS -> cyclesPenalty += branch(state.v)
            Operation.JMP -> jmp(mode = instruction.addressingMode)
            Operation.JSR -> jsr()
            Operation.RTS -> rts()
            Operation.PHA -> pha()
            Operation.PLA -> pla()
            Operation.PHP -> php()
            Operation.PLP -> plp()
            Operation.BRK -> brk()
            Operation.RTI -> rti()
            Operation.NOP -> nop()
        }

        return if (pageCrossed) {
            instruction.baseCycles + cyclesPenalty + 1
        } else {
            instruction.baseCycles + cyclesPenalty
        }
    }

    // region Addressing modes
    private fun readOperand(mode: AddressingMode, pageCrossPenalty: Boolean): Int {
        return when (mode) {
            AddressingMode.IMMEDIATE -> pcRead()
            else -> bus.read(resolveAddress(mode, pageCrossPenalty))
        }
    }

    private fun resolveAddress(mode: AddressingMode, pageCrossPenalty: Boolean): Int {
        return when (mode) {
            AddressingMode.ZERO_PAGE -> {
                pcRead()
            }

            AddressingMode.ZERO_PAGE_X -> {
                (pcRead() + state.x).low8Bits()
            }

            AddressingMode.ZERO_PAGE_Y -> {
                (pcRead() + state.y).low8Bits()
            }

            AddressingMode.ABSOLUTE -> {
                val lo = pcRead()
                val hi = pcRead()

                lo or (hi shl 8)
            }

            AddressingMode.ABSOLUTE_X -> {
                val lo = pcRead()
                val hi = pcRead()

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.x).low16Bits()

                if (pageCrossPenalty) {
                    pageCrossed = (baseAddress xor address) and 0xFF00 != 0
                }

                address
            }

            AddressingMode.ABSOLUTE_Y -> {
                val lo = pcRead()
                val hi = pcRead()

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()

                if (pageCrossPenalty) {
                    pageCrossed = (baseAddress xor address) and 0xFF00 != 0
                }

                address
            }

            AddressingMode.INDIRECT_X -> {
                val pointer = (pcRead() + state.x).low8Bits()

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                lo or (hi shl 8)
            }

            AddressingMode.INDIRECT_Y -> {
                val pointer = pcRead()

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()

                if (pageCrossPenalty) {
                    pageCrossed = (baseAddress xor address) and 0xFF00 != 0
                }

                address
            }

            AddressingMode.IMMEDIATE,
            AddressingMode.IMPLIED,
            AddressingMode.ACCUMULATOR,
            AddressingMode.RELATIVE,
            AddressingMode.INDIRECT -> throw IllegalArgumentException(
                "Addressing mode $mode cannot resolve a data address"
            )
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
        val oldValue = bus.read(address)
        bus.write(address, oldValue)

        val result = (oldValue + 1).low8Bits()

        bus.write(address, result)

        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun dec(address: Int) {
        val oldValue = bus.read(address)
        bus.write(address, oldValue)

        val result = (oldValue - 1).low8Bits()

        bus.write(address, result)

        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun asl(mode: AddressingMode) {
        if (mode == AddressingMode.ACCUMULATOR) {
            state.a = aslValue(state.a)
        } else {
            val address = resolveAddress(mode, false)
            val oldValue = bus.read(address)
            bus.write(address, oldValue)
            bus.write(address, aslValue(oldValue))
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
        if (mode == AddressingMode.ACCUMULATOR) {
            state.a = lsrValue(state.a)
        } else {
            val address = resolveAddress(mode, false)
            val oldValue = bus.read(address)
            bus.write(address, oldValue)
            bus.write(address, lsrValue(oldValue))
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
        if (mode == AddressingMode.ACCUMULATOR) {
            state.a = rolValue(state.a)
        } else {
            val address = resolveAddress(mode, false)
            val oldValue = bus.read(address)
            bus.write(address, oldValue)
            bus.write(address, rolValue(oldValue))
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
        if (mode == AddressingMode.ACCUMULATOR) {
            state.a = rorValue(state.a)
        } else {
            val address = resolveAddress(mode, false)
            val oldValue = bus.read(address)
            bus.write(address, oldValue)
            bus.write(address, rorValue(oldValue))
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
            AddressingMode.ABSOLUTE -> {
                val lo = pcRead()
                val hi = pcRead()

                lo or (hi shl 8)
            }

            AddressingMode.INDIRECT -> {
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

    private fun jsr() {
        val lo = pcRead()

        // PC currently points at the high-byte operand.
        // This is the return address the 6502 pushes.
        val returnAddress = state.pc

        val hi = pcRead()
        val target = lo or (hi shl 8)

        push(returnAddress ushr 8)
        push(returnAddress)

        state.pc = target
    }

    private fun rts() {
        val lo = pull()
        val hi = pull()

        state.pc = ((lo or (hi shl 8)) + 1).low16Bits()
    }

    private fun pha() {
        push(state.a)
    }

    private fun pla() {
        state.a = pull()

        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun php() {
        push(state.status or 0x30)
    }

    private fun plp() {
        state.status = (pull() and 0xEF) or 0x20
    }

    private fun brk() {
        // BRK behaves as a 2-byte instruction.
        state.pc = (state.pc + 1).low16Bits()

        push(state.pc ushr 8)
        push(state.pc)

        // When pushed by BRK, B and U are both set.
        push(state.status or 0x30)

        state.i = true

        val lo = bus.read(0xFFFE)
        val hi = bus.read(0xFFFF)

        state.pc = lo or (hi shl 8)
    }

    private fun rti() {
        state.status = (pull() and 0xEF) or 0x20

        val lo = pull()
        val hi = pull()

        state.pc = lo or (hi shl 8)
    }

    private fun nop() = Unit

    private fun irq(): Int {
        return interrupt(0xFFFE)
    }

    private fun nmi(): Int {
        return interrupt(0xFFFA)
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

    private fun push(value: Int) {
        bus.write(0x0100 or state.sp, value.low8Bits())
        state.sp = (state.sp - 1).low8Bits()
    }

    private fun pull(): Int {
        state.sp = (state.sp + 1).low8Bits()
        return bus.read(0x0100 or state.sp)
    }

    private fun interrupt(vector: Int): Int {
        push(state.pc ushr 8)
        push(state.pc)

        // Hardware interrupts push B=0 and U=1.
        push((state.status and 0xEF) or 0x20)

        state.i = true
        state.irqPollI = true

        val lo = bus.read(vector)
        val hi = bus.read((vector + 1).low16Bits())

        state.pc = lo or (hi shl 8)

        return 7
    }

    // endregion

    private companion object {
        const val RESET_CYCLES = 7
    }
}
