package nes.cpu

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.pageBase

private const val IMP = 0
private const val ACC = 1
private const val IMM = 2
private const val ZP = 3
private const val ZPX = 4
private const val ZPY = 5
private const val ABS = 6
private const val AX = 7
private const val AY = 8
private const val IND = 9
private const val IX = 10
private const val IY = 11
private const val REL = 12

private const val BRK = 0
private const val ORA = 1
private const val KIL = 2
private const val SLO = 3
private const val NOP = 4
private const val ASL = 5
private const val PHP = 6
private const val ANC = 7
private const val BPL = 8
private const val CLC = 9
private const val JSR = 10
private const val AND = 11
private const val RLA = 12
private const val BIT = 13
private const val ROL = 14
private const val PLP = 15
private const val BMI = 16
private const val SEC = 17
private const val RTI = 18
private const val EOR = 19
private const val SRE = 20
private const val LSR = 21
private const val PHA = 22
private const val ALR = 23
private const val JMP = 24
private const val BVC = 25
private const val CLI = 26
private const val RTS = 27
private const val ADC = 28
private const val RRA = 29
private const val ROR = 30
private const val PLA = 31
private const val ARR = 32
private const val BVS = 33
private const val SEI = 34
private const val STA = 35
private const val SAX = 36
private const val STY = 37
private const val STX = 38
private const val DEY = 39
private const val TXA = 40
private const val XAA = 41
private const val BCC = 42
private const val AHX = 43
private const val TYA = 44
private const val TXS = 45
private const val TAS = 46
private const val SHY = 47
private const val SHX = 48
private const val LDY = 49
private const val LDA = 50
private const val LDX = 51
private const val LAX = 52
private const val TAY = 53
private const val TAX = 54
private const val BCS = 55
private const val CLV = 56
private const val TSX = 57
private const val LAS = 58
private const val CPY = 59
private const val CMP = 60
private const val DCP = 61
private const val DEC = 62
private const val INY = 63
private const val DEX = 64
private const val AXS = 65
private const val BNE = 66
private const val CLD = 67
private const val CPX = 68
private const val SBC = 69
private const val ISB = 70
private const val INC = 71
private const val INX = 72
private const val BEQ = 73
private const val SED = 74

private const val OPERATION_MASK = 0xFF
private const val MODE_SHIFT = 8
private const val MODE_MASK = 0x0F
private const val BRANCH_FLAG = 1 shl 12
private const val WRITE_FLAG = 1 shl 13
private const val UNSTABLE_WRITE_FLAG = 1 shl 14
private const val RMW_FLAG = 1 shl 15
private const val READ_CYCLES_SHIFT = 8

private fun opcode(operation: Int, mode: Int): Int {
    val category = when (operation) {
        BPL, BMI, BVC, BVS, BCC, BCS, BNE, BEQ -> BRANCH_FLAG
        AHX, SHX, SHY, TAS -> UNSTABLE_WRITE_FLAG
        STA, STX, STY, SAX -> WRITE_FLAG
        ASL, LSR, ROL, ROR, INC, DEC, SLO, RLA, SRE, RRA, DCP, ISB -> RMW_FLAG
        else -> 0
    }
    return operation or (mode shl MODE_SHIFT) or category
}

private val OPCODES = intArrayOf(
    opcode(BRK, IMP), opcode(ORA, IX), opcode(KIL, IMP), opcode(SLO, IX), opcode(NOP, ZP), opcode(ORA, ZP), opcode(ASL, ZP), opcode(SLO, ZP), opcode(PHP, IMP), opcode(ORA, IMM), opcode(ASL, ACC), opcode(ANC, IMM), opcode(NOP, ABS), opcode(ORA, ABS), opcode(ASL, ABS), opcode(SLO, ABS),
    opcode(BPL, REL), opcode(ORA, IY), opcode(KIL, IMP), opcode(SLO, IY), opcode(NOP, ZPX), opcode(ORA, ZPX), opcode(ASL, ZPX), opcode(SLO, ZPX), opcode(CLC, IMP), opcode(ORA, AY), opcode(NOP, IMP), opcode(SLO, AY), opcode(NOP, AX), opcode(ORA, AX), opcode(ASL, AX), opcode(SLO, AX),
    opcode(JSR, ABS), opcode(AND, IX), opcode(KIL, IMP), opcode(RLA, IX), opcode(BIT, ZP), opcode(AND, ZP), opcode(ROL, ZP), opcode(RLA, ZP), opcode(PLP, IMP), opcode(AND, IMM), opcode(ROL, ACC), opcode(ANC, IMM), opcode(BIT, ABS), opcode(AND, ABS), opcode(ROL, ABS), opcode(RLA, ABS),
    opcode(BMI, REL), opcode(AND, IY), opcode(KIL, IMP), opcode(RLA, IY), opcode(NOP, ZPX), opcode(AND, ZPX), opcode(ROL, ZPX), opcode(RLA, ZPX), opcode(SEC, IMP), opcode(AND, AY), opcode(NOP, IMP), opcode(RLA, AY), opcode(NOP, AX), opcode(AND, AX), opcode(ROL, AX), opcode(RLA, AX),
    opcode(RTI, IMP), opcode(EOR, IX), opcode(KIL, IMP), opcode(SRE, IX), opcode(NOP, ZP), opcode(EOR, ZP), opcode(LSR, ZP), opcode(SRE, ZP), opcode(PHA, IMP), opcode(EOR, IMM), opcode(LSR, ACC), opcode(ALR, IMM), opcode(JMP, ABS), opcode(EOR, ABS), opcode(LSR, ABS), opcode(SRE, ABS),
    opcode(BVC, REL), opcode(EOR, IY), opcode(KIL, IMP), opcode(SRE, IY), opcode(NOP, ZPX), opcode(EOR, ZPX), opcode(LSR, ZPX), opcode(SRE, ZPX), opcode(CLI, IMP), opcode(EOR, AY), opcode(NOP, IMP), opcode(SRE, AY), opcode(NOP, AX), opcode(EOR, AX), opcode(LSR, AX), opcode(SRE, AX),
    opcode(RTS, IMP), opcode(ADC, IX), opcode(KIL, IMP), opcode(RRA, IX), opcode(NOP, ZP), opcode(ADC, ZP), opcode(ROR, ZP), opcode(RRA, ZP), opcode(PLA, IMP), opcode(ADC, IMM), opcode(ROR, ACC), opcode(ARR, IMM), opcode(JMP, IND), opcode(ADC, ABS), opcode(ROR, ABS), opcode(RRA, ABS),
    opcode(BVS, REL), opcode(ADC, IY), opcode(KIL, IMP), opcode(RRA, IY), opcode(NOP, ZPX), opcode(ADC, ZPX), opcode(ROR, ZPX), opcode(RRA, ZPX), opcode(SEI, IMP), opcode(ADC, AY), opcode(NOP, IMP), opcode(RRA, AY), opcode(NOP, AX), opcode(ADC, AX), opcode(ROR, AX), opcode(RRA, AX),
    opcode(NOP, IMM), opcode(STA, IX), opcode(NOP, IMM), opcode(SAX, IX), opcode(STY, ZP), opcode(STA, ZP), opcode(STX, ZP), opcode(SAX, ZP), opcode(DEY, IMP), opcode(NOP, IMM), opcode(TXA, IMP), opcode(XAA, IMM), opcode(STY, ABS), opcode(STA, ABS), opcode(STX, ABS), opcode(SAX, ABS),
    opcode(BCC, REL), opcode(STA, IY), opcode(KIL, IMP), opcode(AHX, IY), opcode(STY, ZPX), opcode(STA, ZPX), opcode(STX, ZPY), opcode(SAX, ZPY), opcode(TYA, IMP), opcode(STA, AY), opcode(TXS, IMP), opcode(TAS, AY), opcode(SHY, AX), opcode(STA, AX), opcode(SHX, AY), opcode(AHX, AY),
    opcode(LDY, IMM), opcode(LDA, IX), opcode(LDX, IMM), opcode(LAX, IX), opcode(LDY, ZP), opcode(LDA, ZP), opcode(LDX, ZP), opcode(LAX, ZP), opcode(TAY, IMP), opcode(LDA, IMM), opcode(TAX, IMP), opcode(LAX, IMM), opcode(LDY, ABS), opcode(LDA, ABS), opcode(LDX, ABS), opcode(LAX, ABS),
    opcode(BCS, REL), opcode(LDA, IY), opcode(KIL, IMP), opcode(LAX, IY), opcode(LDY, ZPX), opcode(LDA, ZPX), opcode(LDX, ZPY), opcode(LAX, ZPY), opcode(CLV, IMP), opcode(LDA, AY), opcode(TSX, IMP), opcode(LAS, AY), opcode(LDY, AX), opcode(LDA, AX), opcode(LDX, AY), opcode(LAX, AY),
    opcode(CPY, IMM), opcode(CMP, IX), opcode(NOP, IMM), opcode(DCP, IX), opcode(CPY, ZP), opcode(CMP, ZP), opcode(DEC, ZP), opcode(DCP, ZP), opcode(INY, IMP), opcode(CMP, IMM), opcode(DEX, IMP), opcode(AXS, IMM), opcode(CPY, ABS), opcode(CMP, ABS), opcode(DEC, ABS), opcode(DCP, ABS),
    opcode(BNE, REL), opcode(CMP, IY), opcode(KIL, IMP), opcode(DCP, IY), opcode(NOP, ZPX), opcode(CMP, ZPX), opcode(DEC, ZPX), opcode(DCP, ZPX), opcode(CLD, IMP), opcode(CMP, AY), opcode(NOP, IMP), opcode(DCP, AY), opcode(NOP, AX), opcode(CMP, AX), opcode(DEC, AX), opcode(DCP, AX),
    opcode(CPX, IMM), opcode(SBC, IX), opcode(NOP, IMM), opcode(ISB, IX), opcode(CPX, ZP), opcode(SBC, ZP), opcode(INC, ZP), opcode(ISB, ZP), opcode(INX, IMP), opcode(SBC, IMM), opcode(NOP, IMP), opcode(SBC, IMM), opcode(CPX, ABS), opcode(SBC, ABS), opcode(INC, ABS), opcode(ISB, ABS),
    opcode(BEQ, REL), opcode(SBC, IY), opcode(KIL, IMP), opcode(ISB, IY), opcode(NOP, ZPX), opcode(SBC, ZPX), opcode(INC, ZPX), opcode(ISB, ZPX), opcode(SED, IMP), opcode(SBC, AY), opcode(NOP, IMP), opcode(ISB, AY), opcode(NOP, AX), opcode(SBC, AX), opcode(INC, AX), opcode(ISB, AX),
)

class Cpu6502(
    private val bus: CpuBus
) {
    companion object {
        const val C = 0x01
        const val Z = 0x02
        const val I = 0x04
        const val D = 0x08
        const val B = 0x10
        const val U = 0x20
        const val V = 0x40
        const val N = 0x80

        const val OP_BRK = 0x00
        const val OP_ORA_INDX = 0x01
        const val OP_ORA_ZP = 0x05
        const val OP_ASL_ZP = 0x06
        const val OP_PHP = 0x08
        const val OP_ORA_IMM = 0x09
        const val OP_ASL_ACC = 0x0A
        const val OP_ORA_ABS = 0x0D
        const val OP_ASL_ABS = 0x0E
        const val OP_BPL = 0x10
        const val OP_ORA_INDY = 0x11
        const val OP_ORA_ZPX = 0x15
        const val OP_ASL_ZPX = 0x16
        const val OP_CLC = 0x18
        const val OP_ORA_ABSY = 0x19
        const val OP_ORA_ABSX = 0x1D
        const val OP_ASL_ABSX = 0x1E
        const val OP_JSR_ABS = 0x20
        const val OP_AND_INDX = 0x21
        const val OP_BIT_ZP = 0x24
        const val OP_AND_ZP = 0x25
        const val OP_ROL_ZP = 0x26
        const val OP_PLP = 0x28
        const val OP_AND_IMM = 0x29
        const val OP_ROL_ACC = 0x2A
        const val OP_BIT_ABS = 0x2C
        const val OP_AND_ABS = 0x2D
        const val OP_ROL_ABS = 0x2E
        const val OP_BMI = 0x30
        const val OP_AND_INDY = 0x31
        const val OP_AND_ZPX = 0x35
        const val OP_ROL_ZPX = 0x36
        const val OP_SEC = 0x38
        const val OP_AND_ABSY = 0x39
        const val OP_AND_ABSX = 0x3D
        const val OP_ROL_ABSX = 0x3E
        const val OP_RTI = 0x40
        const val OP_EOR_INDX = 0x41
        const val OP_EOR_ZP = 0x45
        const val OP_LSR_ZP = 0x46
        const val OP_PHA = 0x48
        const val OP_EOR_IMM = 0x49
        const val OP_LSR_ACC = 0x4A
        const val OP_JMP_ABS = 0x4C
        const val OP_EOR_ABS = 0x4D
        const val OP_LSR_ABS = 0x4E
        const val OP_BVC = 0x50
        const val OP_EOR_INDY = 0x51
        const val OP_EOR_ZPX = 0x55
        const val OP_LSR_ZPX = 0x56
        const val OP_CLI = 0x58
        const val OP_EOR_ABSY = 0x59
        const val OP_EOR_ABSX = 0x5D
        const val OP_LSR_ABSX = 0x5E
        const val OP_RTS = 0x60
        const val OP_ADC_INDX = 0x61
        const val OP_ADC_ZP = 0x65
        const val OP_ROR_ZP = 0x66
        const val OP_PLA = 0x68
        const val OP_ADC_IMM = 0x69
        const val OP_ROR_ACC = 0x6A
        const val OP_JMP_IND = 0x6C
        const val OP_ADC_ABS = 0x6D
        const val OP_ROR_ABS = 0x6E
        const val OP_BVS = 0x70
        const val OP_ADC_INDY = 0x71
        const val OP_ADC_ZPX = 0x75
        const val OP_ROR_ZPX = 0x76
        const val OP_SEI = 0x78
        const val OP_ADC_ABSY = 0x79
        const val OP_ADC_ABSX = 0x7D
        const val OP_ROR_ABSX = 0x7E
        const val OP_STA_INDX = 0x81
        const val OP_STY_ZP = 0x84
        const val OP_STA_ZP = 0x85
        const val OP_STX_ZP = 0x86
        const val OP_DEY = 0x88
        const val OP_TXA = 0x8A
        const val OP_STY_ABS = 0x8C
        const val OP_STA_ABS = 0x8D
        const val OP_STX_ABS = 0x8E
        const val OP_BCC = 0x90
        const val OP_STA_INDY = 0x91
        const val OP_STY_ZPX = 0x94
        const val OP_STA_ZPX = 0x95
        const val OP_STX_ZPY = 0x96
        const val OP_TYA = 0x98
        const val OP_STA_ABSY = 0x99
        const val OP_TXS = 0x9A
        const val OP_STA_ABSX = 0x9D
        const val OP_LDY_IMM = 0xA0
        const val OP_LDA_INDX = 0xA1
        const val OP_LDX_IMM = 0xA2
        const val OP_LDY_ZP = 0xA4
        const val OP_LDA_ZP = 0xA5
        const val OP_LDX_ZP = 0xA6
        const val OP_TAY = 0xA8
        const val OP_LDA_IMM = 0xA9
        const val OP_TAX = 0xAA
        const val OP_LDY_ABS = 0xAC
        const val OP_LDA_ABS = 0xAD
        const val OP_LDX_ABS = 0xAE
        const val OP_BCS = 0xB0
        const val OP_LDA_INDY = 0xB1
        const val OP_LDY_ZPX = 0xB4
        const val OP_LDA_ZPX = 0xB5
        const val OP_LDX_ZPY = 0xB6
        const val OP_CLV = 0xB8
        const val OP_LDA_ABSY = 0xB9
        const val OP_TSX = 0xBA
        const val OP_LDY_ABSX = 0xBC
        const val OP_LDA_ABSX = 0xBD
        const val OP_LDX_ABSY = 0xBE
        const val OP_CPY_IMM = 0xC0
        const val OP_CMP_INDX = 0xC1
        const val OP_CPY_ZP = 0xC4
        const val OP_CMP_ZP = 0xC5
        const val OP_DEC_ZP = 0xC6
        const val OP_INY = 0xC8
        const val OP_CMP_IMM = 0xC9
        const val OP_DEX = 0xCA
        const val OP_CPY_ABS = 0xCC
        const val OP_CMP_ABS = 0xCD
        const val OP_DEC_ABS = 0xCE
        const val OP_BNE = 0xD0
        const val OP_CMP_INDY = 0xD1
        const val OP_CMP_ZPX = 0xD5
        const val OP_DEC_ZPX = 0xD6
        const val OP_CLD = 0xD8
        const val OP_CMP_ABSY = 0xD9
        const val OP_CMP_ABSX = 0xDD
        const val OP_DEC_ABSX = 0xDE
        const val OP_CPX_IMM = 0xE0
        const val OP_SBC_INDX = 0xE1
        const val OP_CPX_ZP = 0xE4
        const val OP_SBC_ZP = 0xE5
        const val OP_INC_ZP = 0xE6
        const val OP_INX = 0xE8
        const val OP_SBC_IMM = 0xE9
        const val OP_NOP = 0xEA
        const val OP_SBC_IMM_UNOFFICIAL = 0xEB
        const val OP_CPX_ABS = 0xEC
        const val OP_SBC_ABS = 0xED
        const val OP_INC_ABS = 0xEE
        const val OP_BEQ = 0xF0
        const val OP_SBC_INDY = 0xF1
        const val OP_SBC_ZPX = 0xF5
        const val OP_INC_ZPX = 0xF6
        const val OP_SED = 0xF8
        const val OP_SBC_ABSY = 0xF9
        const val OP_SBC_ABSX = 0xFD
        const val OP_INC_ABSX = 0xFE
    }

    var pc = 0
        private set
    var a = 0
        private set
    var x = 0
        private set
    var y = 0
        private set
    var sp = 0xFD
        private set
    var status = I or U
        private set
    var totalCycles = 0L
        private set

    private var nmiPending = false
    private var irqLine = false
    private var irqPending = false
    private var irqSample = false
    private var halted = false

    fun reset() = reset(softReset = false)

    fun reset(softReset: Boolean) {
        bus.reset()
        totalCycles = -1
        if (softReset) {
            sp = (sp - 3).low8Bits()
            set(I, true)
        } else {
            a = 0
            x = 0
            y = 0
            sp = 0xFD
            status = I or U
        }
        pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
        repeat(8) {
            bus.idle(CpuBus.CycleType.RESET)
            totalCycles++
        }
        nmiPending = false
        irqLine = false
        irqPending = false
        irqSample = false
        halted = false
    }

    fun requestNmi() {
        nmiPending = true
    }

    fun setIrqLine(asserted: Boolean) {
        irqLine = asserted
        irqPending = asserted && !flag(I)
    }

    fun sampleIrqLine(asserted: Boolean) {
        irqLine = asserted
        irqPending = irqSample
        irqSample = asserted && !flag(I)
    }

    fun step(): Int {
        val start = totalCycles
        val stalls = bus.consumeDmaCycles()
        if (stalls > 0) {
            var stall = 0
            while (stall < stalls) {
                bus.idle(CpuBus.CycleType.STALL)
                totalCycles++
                stall++
            }
            return stalls
        }

        when {
            halted -> execute(OPCODES[fetchOpcode()])
            nmiPending -> {
                nmiPending = false
                serviceInterrupt(0xFFFA)
            }
            irqPending -> serviceInterrupt(0xFFFE)
            else -> {
                val opcode = fetchOpcode()
                execute(OPCODES[opcode])
            }
        }
        return (totalCycles - start).toInt()
    }

    private fun execute(encodedOpcode: Int) {
        val instruction = encodedOpcode and OPERATION_MASK
        val mode = (encodedOpcode ushr MODE_SHIFT) and MODE_MASK
        when {
            instruction == BRK -> brk()
            instruction == JSR -> jsr()
            instruction == JMP -> jump(mode)
            instruction == RTS -> rts()
            instruction == RTI -> rti()
            instruction == PHP -> pushInstruction(status or B or U)
            instruction == PHA -> pushInstruction(a)
            instruction == PLP -> {
                impliedRead()
                dummyRead(0x100 or sp)
                status = (pull() and (B or U).inv()) or U
            }
            instruction == PLA -> {
                impliedRead()
                dummyRead(0x100 or sp)
                a = pull()
                zn(a)
            }
            (encodedOpcode and BRANCH_FLAG) != 0 -> branch(instruction, fetch())
            (encodedOpcode and UNSTABLE_WRITE_FLAG) != 0 -> unstableStore(instruction, mode)
            (encodedOpcode and WRITE_FLAG) != 0 -> {
                val target = address(mode, write = true)
                write(target, storeValue(instruction))
            }
            instruction == ASL || instruction == LSR || instruction == ROL || instruction == ROR -> {
                if (mode == ACC) {
                    impliedRead()
                    a = transform(instruction, a)
                } else {
                    modify(address(mode, write = true), instruction)
                }
            }
            (encodedOpcode and RMW_FLAG) != 0 -> modify(address(mode, write = true), instruction)
            instruction == KIL -> {
                pc = (pc - 1).low16Bits()
                halted = true
                irqPending = false
                nmiPending = false
            }
            else -> executeReadOrImplied(instruction, mode)
        }
    }

    private fun executeReadOrImplied(instruction: Int, mode: Int) {
        if (mode == IMP) {
            impliedRead()
            when (instruction) {
                CLC -> set(C, false)
                SEC -> set(C, true)
                CLI -> set(I, false)
                SEI -> set(I, true)
                CLV -> set(V, false)
                CLD -> set(D, false)
                SED -> set(D, true)
                TAX -> { x = a; zn(x) }
                TAY -> { y = a; zn(y) }
                TXA -> { a = x; zn(a) }
                TYA -> { a = y; zn(a) }
                TSX -> { x = sp; zn(x) }
                TXS -> sp = x
                DEX -> { x = (x - 1).low8Bits(); zn(x) }
                DEY -> { y = (y - 1).low8Bits(); zn(y) }
                INX -> { x = (x + 1).low8Bits(); zn(x) }
                INY -> { y = (y + 1).low8Bits(); zn(y) }
                NOP -> Unit
                else -> error("Unsupported implied instruction $instruction")
            }
            return
        }

        val value = readOperand(mode)
        when (instruction) {
            ORA -> { a = a or value; zn(a) }
            AND -> { a = a and value; zn(a) }
            EOR -> { a = a xor value; zn(a) }
            ADC -> adc(value)
            SBC -> sbc(value)
            CMP -> compare(a, value)
            CPX -> compare(x, value)
            CPY -> compare(y, value)
            BIT -> bit(value)
            LDA -> { a = value; zn(a) }
            LDX -> { x = value; zn(x) }
            LDY -> { y = value; zn(y) }
            LAX -> { a = value; x = value; zn(value) }
            LAS -> { val result = value and sp; a = result; x = result; sp = result; zn(result) }
            ANC -> { a = a and value; zn(a); set(C, flag(N)) }
            ALR -> { a = lsrValue(a and value) }
            ARR -> arr(value)
            XAA -> { a = (a or 0xEE) and x and value; zn(a) }
            AXS -> axs(value)
            NOP -> Unit
            else -> error("Unsupported read instruction $instruction")
        }
    }

    private fun readOperand(mode: Int): Int = when (mode) {
        IMM -> fetch()
        else -> read(address(mode, write = false))
    }

    private fun address(mode: Int, write: Boolean): Int = when (mode) {
        ZP -> fetch()
        ZPX, ZPY -> {
            val base = fetch()
            dummyRead(base)
            (base + if (mode == ZPX) x else y).low8Bits()
        }
        ABS -> absolute()
        AX, AY -> indexedAbsolute(if (mode == AX) x else y, write)
        IX -> {
            val operand = fetch()
            dummyRead(operand)
            val pointer = (operand + x).low8Bits()
            read(pointer) or (read((pointer + 1).low8Bits()) shl 8)
        }
        IY -> {
            val pointer = fetch()
            val base = read(pointer) or (read((pointer + 1).low8Bits()) shl 8)
            val result = (base + y).low16Bits()
            if (write || base.pageBase() != result.pageBase()) {
                dummyRead(base.pageBase() or result.low8Bits())
            }
            result
        }
        else -> error("Address mode $mode has no memory address")
    }

    private fun indexedAbsolute(index: Int, alwaysDummy: Boolean): Int {
        val base = absolute()
        val result = (base + index).low16Bits()
        if (alwaysDummy || base.pageBase() != result.pageBase()) {
            dummyRead(base.pageBase() or result.low8Bits())
        }
        return result
    }

    private fun modify(address: Int, instruction: Int) {
        val old = read(address)
        dummyWrite(address, old)
        val result = when (instruction) {
            SLO -> {
                val transformed = transform(ASL, old)
                a = a or transformed
                zn(a)
                transformed
            }
            RLA -> {
                val transformed = transform(ROL, old)
                a = a and transformed
                zn(a)
                transformed
            }
            SRE -> {
                val transformed = transform(LSR, old)
                a = a xor transformed
                zn(a)
                transformed
            }
            RRA -> {
                val transformed = transform(ROR, old)
                adc(transformed)
                transformed
            }
            DCP -> {
                val transformed = (old - 1).low8Bits()
                compare(a, transformed)
                transformed
            }
            ISB -> {
                val transformed = (old + 1).low8Bits()
                sbc(transformed)
                transformed
            }
            else -> transform(instruction, old)
        }
        write(address, result)
    }

    private fun transform(instruction: Int, value: Int): Int = when (instruction) {
        ASL -> aslValue(value)
        LSR -> lsrValue(value)
        ROL -> rolValue(value)
        ROR -> rorValue(value)
        INC -> {
            val result = (value + 1).low8Bits()
            zn(result)
            result
        }
        DEC -> {
            val result = (value - 1).low8Bits()
            zn(result)
            result
        }
        else -> error("Unsupported RMW instruction $instruction")
    }

    private fun storeValue(instruction: Int): Int = when (instruction) {
        STA -> a
        STX -> x
        STY -> y
        SAX -> a and x
        else -> error("Unsupported store instruction $instruction")
    }

    private fun unstableStore(instruction: Int, mode: Int) {
        val base = if (mode == IY) {
            val pointer = fetch()
            read(pointer) or (read((pointer + 1).low8Bits()) shl 8)
        } else {
            absolute()
        }
        val index = if (mode == AX) x else y
        val target = (base + index).low16Bits()
        dummyRead(base.pageBase() or target.low8Bits())
        val valueRegister = when (instruction) {
            SHY -> y
            SHX -> x
            AHX -> a and x
            TAS -> (a and x).also { sp = it }
            else -> 0
        }
        val value = valueRegister and (((base shr 8) + 1).low8Bits())
        val destination = if (base.pageBase() != target.pageBase()) {
            target.low8Bits() or (((target shr 8) and valueRegister) shl 8)
        } else {
            target
        }
        write(destination, value)
    }

    private fun write(address: Int, value: Int) {
        bus.cpuWrite(address, value)
        totalCycles++
    }

    private fun dummyWrite(address: Int, value: Int) {
        bus.cpuWrite(address, value, dummy = true)
        totalCycles++
    }

    private fun read(address: Int, opcodeFetch: Boolean = false): Int {
        val result = bus.cpuRead(address, totalCycles, opcodeFetch = opcodeFetch)
        totalCycles += result ushr READ_CYCLES_SHIFT
        return result and 0xFF
    }

    private fun dummyRead(address: Int, opcodeFetch: Boolean = false): Int {
        val result = bus.cpuRead(address, totalCycles, dummy = true, opcodeFetch = opcodeFetch)
        totalCycles += result ushr READ_CYCLES_SHIFT
        return result and 0xFF
    }

    private fun fetch(): Int {
        val value = read(pc)
        pc = (pc + 1).low16Bits()
        return value
    }

    private fun fetchOpcode(): Int {
        val value = read(pc, opcodeFetch = true)
        pc = (pc + 1).low16Bits()
        return value
    }

    private fun impliedRead() {
        dummyRead(pc)
    }

    private fun absolute(): Int = fetch() or (fetch() shl 8)

    private fun push(value: Int) {
        write(0x100 or sp, value)
        sp = (sp - 1).low8Bits()
    }

    private fun pull(): Int {
        sp = (sp + 1).low8Bits()
        return read(0x100 or sp)
    }

    private fun pushInstruction(value: Int) {
        impliedRead()
        push(value)
    }

    private fun serviceInterrupt(vector: Int) {
        dummyRead(pc, opcodeFetch = true)
        dummyRead(pc)
        push(pc shr 8)
        push(pc)
        val selectedVector = if (nmiPending) {
            nmiPending = false
            0xFFFA
        } else {
            vector
        }
        push((status or U) and B.inv())
        set(I, true)
        pc = read(selectedVector) or (read(selectedVector + 1) shl 8)
        irqPending = false
    }

    private fun brk() {
        fetch() // BRK's padding byte is a real read.
        push(pc shr 8)
        push(pc)
        val vector = if (nmiPending) {
            nmiPending = false
            0xFFFA
        } else {
            0xFFFE
        }
        push(status or B or U)
        set(I, true)
        pc = read(vector) or (read(vector + 1) shl 8)
    }

    private fun jsr() {
        val low = fetch()
        dummyRead(0x100 or sp)
        push(pc shr 8)
        push(pc)
        pc = low or (fetch() shl 8)
    }

    private fun jump(mode: Int) {
        if (mode == ABS) {
            pc = absolute()
        } else {
            val pointer = absolute()
            val highAddress = pointer.pageBase() or ((pointer + 1).low8Bits())
            pc = read(pointer) or (read(highAddress) shl 8)
        }
    }

    private fun rts() {
        impliedRead()
        dummyRead(0x100 or sp)
        val low = pull()
        val high = pull()
        val returnAddress = low or (high shl 8)
        dummyRead(returnAddress)
        pc = (returnAddress + 1).low16Bits()
    }

    private fun rti() {
        impliedRead()
        dummyRead(0x100 or sp)
        status = (pull() and (B or U).inv()) or U
        pc = pull() or (pull() shl 8)
    }

    private fun branch(instruction: Int, offset: Int) {
        val take = when (instruction) {
            BPL -> !flag(N)
            BMI -> flag(N)
            BVC -> !flag(V)
            BVS -> flag(V)
            BCC -> !flag(C)
            BCS -> flag(C)
            BNE -> !flag(Z)
            BEQ -> flag(Z)
            else -> false
        }
        if (!take) return
        val oldPc = pc
        dummyRead(oldPc)
        val signed = if (offset < 0x80) offset else offset - 0x100
        val target = (oldPc + signed).low16Bits()
        if (oldPc.pageBase() != target.pageBase()) {
            dummyRead(oldPc.pageBase() or target.low8Bits())
        }
        pc = target
    }

    private fun flag(flag: Int): Boolean = (status and flag) != 0

    private fun set(flag: Int, enabled: Boolean) {
        status = if (enabled) status or flag else status and flag.inv()
        status = (status or U) and B.inv()
    }

    private fun zn(value: Int) {
        val result = value.low8Bits()
        status = (status and (Z or N).inv()) or (result and N) or if (result == 0) Z else 0
    }

    private fun adc(value: Int) {
        val sum = a + value + if (flag(C)) 1 else 0
        val result = sum.low8Bits()
        set(C, sum > 0xFF)
        set(V, ((a xor result) and (value xor result) and 0x80) != 0)
        a = result
        zn(a)
    }

    private fun sbc(value: Int) = adc(value xor 0xFF)

    private fun compare(register: Int, value: Int) {
        val result = (register - value).low8Bits()
        set(C, register >= value)
        zn(result)
    }

    private fun bit(value: Int) {
        set(Z, (a and value) == 0)
        set(V, (value and V) != 0)
        set(N, (value and N) != 0)
    }

    private fun aslValue(value: Int): Int {
        set(C, (value and 0x80) != 0)
        val result = (value shl 1).low8Bits()
        zn(result)
        return result
    }

    private fun lsrValue(value: Int): Int {
        set(C, (value and 1) != 0)
        val result = (value ushr 1).low8Bits()
        zn(result)
        return result
    }

    private fun rolValue(value: Int): Int {
        val carry = if (flag(C)) 1 else 0
        set(C, (value and 0x80) != 0)
        val result = ((value shl 1) or carry).low8Bits()
        zn(result)
        return result
    }

    private fun rorValue(value: Int): Int {
        val carry = if (flag(C)) 0x80 else 0
        set(C, (value and 1) != 0)
        val result = ((value ushr 1) or carry).low8Bits()
        zn(result)
        return result
    }

    private fun arr(value: Int) {
        a = (a and value) ushr 1 or if (flag(C)) 0x80 else 0
        zn(a)
        set(C, (a and 0x40) != 0)
        set(V, ((a shr 6) xor (a shr 5)) and 1 != 0)
    }

    private fun axs(value: Int) {
        val source = a and x
        x = (source - value).low8Bits()
        set(C, source >= value)
        zn(x)
    }


}
