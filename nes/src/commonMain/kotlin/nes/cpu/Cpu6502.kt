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

/**
 * Operation identifiers used by [OPCODES]. The names below are the 6502
 * mnemonics. [execute] combines one of them with an addressing mode and then
 * performs the instruction's bus accesses in the same order as the hardware.
 *
 * The documented operations are:
 *
 * - `BRK`: push the return address and status, then enter the IRQ/BRK vector.
 * - `ORA`, `AND`, `EOR`: combine memory with A using OR, AND, or XOR.
 * - `ASL`, `LSR`, `ROL`, `ROR`: shift or rotate A or a memory byte and update C/Z/N.
 * - `ADC`, `SBC`: add or subtract with carry (binary arithmetic; SBC uses one's complement).
 * - `CMP`, `CPX`, `CPY`: subtract without storing the result, setting C/Z/N from the comparison.
 * - `BIT`: test A against memory and copy memory bits 7 and 6 into N and V.
 * - `LDA`, `LDX`, `LDY`: load a value into A, X, or Y and update Z/N.
 * - `STA`, `STX`, `STY`: store A, X, or Y to memory; `SAX` stores `A and X`.
 * - `INC`, `DEC`, `INX`, `DEX`, `INY`, `DEY`: increment or decrement memory or an index register.
 * - `JMP`, `JSR`, `RTS`: jump, call a subroutine, or return from a subroutine.
 * - `RTI`: restore status and PC from the stack after an interrupt.
 * - `BPL`, `BMI`, `BVC`, `BVS`, `BCC`, `BCS`, `BNE`, `BEQ`: conditionally add a signed relative offset to PC.
 * - `CLC`, `SEC`, `CLI`, `SEI`, `CLV`, `CLD`, `SED`: clear or set one processor flag.
 * - `PHP`, `PLP`, `PHA`, `PLA`: push or pull status or A on the hardware stack.
 * - `TAX`, `TAY`, `TXA`, `TYA`, `TSX`, `TXS`: transfer values between registers and update Z/N where applicable.
 * - `NOP`: perform the instruction's reads without changing architectural state.
 * - `KIL`: the unofficial halt instruction; the CPU remains halted until reset.
 * - `SLO`, `RLA`, `SRE`, `RRA`: unofficial read-modify-write combinations of a shift/rotate and OR/AND/XOR/ADC.
 * - `DCP`, `ISB`: unofficial decrement/increment followed by CMP/SBC.
 * - `LAX`, `LAS`: unofficial loads into multiple registers (`LAX` loads A/X; `LAS` also masks with SP).
 * - `ANC`, `ALR`, `ARR`, `XAA`, `AXS`: unofficial immediate A/X operations with silicon-specific flag behavior.
 * - `AHX`, `SHX`, `SHY`, `TAS`: unofficial unstable stores whose value and, on a page crossing, address depend on the high address byte.
 */
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

/** Encodes operation, addressing mode, and cycle-sensitive instruction categories. */
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

private const val OPERATION_MASK = 0xFF
private const val MODE_SHIFT = 8
private const val MODE_MASK = 0x0F
private const val BRANCH_FLAG = 1 shl 12
private const val WRITE_FLAG = 1 shl 13
private const val UNSTABLE_WRITE_FLAG = 1 shl 14
private const val RMW_FLAG = 1 shl 15
private const val READ_CYCLES_SHIFT = 8

/** The 256 entries map each byte fetched from memory to an operation and addressing mode. */
private val OPCODES = intArrayOf(
    opcode(BRK, IMP),
    opcode(ORA, IX),
    opcode(KIL, IMP),
    opcode(SLO, IX),
    opcode(NOP, ZP),
    opcode(ORA, ZP),
    opcode(ASL, ZP),
    opcode(SLO, ZP),
    opcode(PHP, IMP),
    opcode(ORA, IMM),
    opcode(ASL, ACC),
    opcode(ANC, IMM),
    opcode(NOP, ABS),
    opcode(ORA, ABS),
    opcode(ASL, ABS),
    opcode(SLO, ABS),
    opcode(BPL, REL),
    opcode(ORA, IY),
    opcode(KIL, IMP),
    opcode(SLO, IY),
    opcode(NOP, ZPX),
    opcode(ORA, ZPX),
    opcode(ASL, ZPX),
    opcode(SLO, ZPX),
    opcode(CLC, IMP),
    opcode(ORA, AY),
    opcode(NOP, IMP),
    opcode(SLO, AY),
    opcode(NOP, AX),
    opcode(ORA, AX),
    opcode(ASL, AX),
    opcode(SLO, AX),
    opcode(JSR, ABS),
    opcode(AND, IX),
    opcode(KIL, IMP),
    opcode(RLA, IX),
    opcode(BIT, ZP),
    opcode(AND, ZP),
    opcode(ROL, ZP),
    opcode(RLA, ZP),
    opcode(PLP, IMP),
    opcode(AND, IMM),
    opcode(ROL, ACC),
    opcode(ANC, IMM),
    opcode(BIT, ABS),
    opcode(AND, ABS),
    opcode(ROL, ABS),
    opcode(RLA, ABS),
    opcode(BMI, REL),
    opcode(AND, IY),
    opcode(KIL, IMP),
    opcode(RLA, IY),
    opcode(NOP, ZPX),
    opcode(AND, ZPX),
    opcode(ROL, ZPX),
    opcode(RLA, ZPX),
    opcode(SEC, IMP),
    opcode(AND, AY),
    opcode(NOP, IMP),
    opcode(RLA, AY),
    opcode(NOP, AX),
    opcode(AND, AX),
    opcode(ROL, AX),
    opcode(RLA, AX),
    opcode(RTI, IMP),
    opcode(EOR, IX),
    opcode(KIL, IMP),
    opcode(SRE, IX),
    opcode(NOP, ZP),
    opcode(EOR, ZP),
    opcode(LSR, ZP),
    opcode(SRE, ZP),
    opcode(PHA, IMP),
    opcode(EOR, IMM),
    opcode(LSR, ACC),
    opcode(ALR, IMM),
    opcode(JMP, ABS),
    opcode(EOR, ABS),
    opcode(LSR, ABS),
    opcode(SRE, ABS),
    opcode(BVC, REL),
    opcode(EOR, IY),
    opcode(KIL, IMP),
    opcode(SRE, IY),
    opcode(NOP, ZPX),
    opcode(EOR, ZPX),
    opcode(LSR, ZPX),
    opcode(SRE, ZPX),
    opcode(CLI, IMP),
    opcode(EOR, AY),
    opcode(NOP, IMP),
    opcode(SRE, AY),
    opcode(NOP, AX),
    opcode(EOR, AX),
    opcode(LSR, AX),
    opcode(SRE, AX),
    opcode(RTS, IMP),
    opcode(ADC, IX),
    opcode(KIL, IMP),
    opcode(RRA, IX),
    opcode(NOP, ZP),
    opcode(ADC, ZP),
    opcode(ROR, ZP),
    opcode(RRA, ZP),
    opcode(PLA, IMP),
    opcode(ADC, IMM),
    opcode(ROR, ACC),
    opcode(ARR, IMM),
    opcode(JMP, IND),
    opcode(ADC, ABS),
    opcode(ROR, ABS),
    opcode(RRA, ABS),
    opcode(BVS, REL),
    opcode(ADC, IY),
    opcode(KIL, IMP),
    opcode(RRA, IY),
    opcode(NOP, ZPX),
    opcode(ADC, ZPX),
    opcode(ROR, ZPX),
    opcode(RRA, ZPX),
    opcode(SEI, IMP),
    opcode(ADC, AY),
    opcode(NOP, IMP),
    opcode(RRA, AY),
    opcode(NOP, AX),
    opcode(ADC, AX),
    opcode(ROR, AX),
    opcode(RRA, AX),
    opcode(NOP, IMM),
    opcode(STA, IX),
    opcode(NOP, IMM),
    opcode(SAX, IX),
    opcode(STY, ZP),
    opcode(STA, ZP),
    opcode(STX, ZP),
    opcode(SAX, ZP),
    opcode(DEY, IMP),
    opcode(NOP, IMM),
    opcode(TXA, IMP),
    opcode(XAA, IMM),
    opcode(STY, ABS),
    opcode(STA, ABS),
    opcode(STX, ABS),
    opcode(SAX, ABS),
    opcode(BCC, REL),
    opcode(STA, IY),
    opcode(KIL, IMP),
    opcode(AHX, IY),
    opcode(STY, ZPX),
    opcode(STA, ZPX),
    opcode(STX, ZPY),
    opcode(SAX, ZPY),
    opcode(TYA, IMP),
    opcode(STA, AY),
    opcode(TXS, IMP),
    opcode(TAS, AY),
    opcode(SHY, AX),
    opcode(STA, AX),
    opcode(SHX, AY),
    opcode(AHX, AY),
    opcode(LDY, IMM),
    opcode(LDA, IX),
    opcode(LDX, IMM),
    opcode(LAX, IX),
    opcode(LDY, ZP),
    opcode(LDA, ZP),
    opcode(LDX, ZP),
    opcode(LAX, ZP),
    opcode(TAY, IMP),
    opcode(LDA, IMM),
    opcode(TAX, IMP),
    opcode(LAX, IMM),
    opcode(LDY, ABS),
    opcode(LDA, ABS),
    opcode(LDX, ABS),
    opcode(LAX, ABS),
    opcode(BCS, REL),
    opcode(LDA, IY),
    opcode(KIL, IMP),
    opcode(LAX, IY),
    opcode(LDY, ZPX),
    opcode(LDA, ZPX),
    opcode(LDX, ZPY),
    opcode(LAX, ZPY),
    opcode(CLV, IMP),
    opcode(LDA, AY),
    opcode(TSX, IMP),
    opcode(LAS, AY),
    opcode(LDY, AX),
    opcode(LDA, AX),
    opcode(LDX, AY),
    opcode(LAX, AY),
    opcode(CPY, IMM),
    opcode(CMP, IX),
    opcode(NOP, IMM),
    opcode(DCP, IX),
    opcode(CPY, ZP),
    opcode(CMP, ZP),
    opcode(DEC, ZP),
    opcode(DCP, ZP),
    opcode(INY, IMP),
    opcode(CMP, IMM),
    opcode(DEX, IMP),
    opcode(AXS, IMM),
    opcode(CPY, ABS),
    opcode(CMP, ABS),
    opcode(DEC, ABS),
    opcode(DCP, ABS),
    opcode(BNE, REL),
    opcode(CMP, IY),
    opcode(KIL, IMP),
    opcode(DCP, IY),
    opcode(NOP, ZPX),
    opcode(CMP, ZPX),
    opcode(DEC, ZPX),
    opcode(DCP, ZPX),
    opcode(CLD, IMP),
    opcode(CMP, AY),
    opcode(NOP, IMP),
    opcode(DCP, AY),
    opcode(NOP, AX),
    opcode(CMP, AX),
    opcode(DEC, AX),
    opcode(DCP, AX),
    opcode(CPX, IMM),
    opcode(SBC, IX),
    opcode(NOP, IMM),
    opcode(ISB, IX),
    opcode(CPX, ZP),
    opcode(SBC, ZP),
    opcode(INC, ZP),
    opcode(ISB, ZP),
    opcode(INX, IMP),
    opcode(SBC, IMM),
    opcode(NOP, IMP),
    opcode(SBC, IMM),
    opcode(CPX, ABS),
    opcode(SBC, ABS),
    opcode(INC, ABS),
    opcode(ISB, ABS),
    opcode(BEQ, REL),
    opcode(SBC, IY),
    opcode(KIL, IMP),
    opcode(ISB, IY),
    opcode(NOP, ZPX),
    opcode(SBC, ZPX),
    opcode(INC, ZPX),
    opcode(ISB, ZPX),
    opcode(SED, IMP),
    opcode(SBC, AY),
    opcode(NOP, IMP),
    opcode(ISB, AY),
    opcode(NOP, AX),
    opcode(SBC, AX),
    opcode(INC, AX),
    opcode(ISB, AX),
)

/**
 * Emulates the NES's NMOS 6502-derived CPU.
 *
 * [step] is the public clock boundary. It first consumes DMA stalls, services
 * pending interrupts, or fetches and executes one opcode. Every memory access
 * goes through [CpuBus], and each access advances [CpuState.totalCycles], so
 * dummy reads/writes and page-crossing penalties are represented as well as
 * the final register and memory results.
 */
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

    var state = CpuState()
        private set

    /** Returns a detached snapshot of registers, flags, interrupt latches, and cycle state. */
    fun captureState(): CpuState = state.copy()

    /** Replaces the live CPU state with a previously captured snapshot. */
    fun restoreState(state: CpuState) {
        this.state = state
    }

    /** Performs a power-on reset, clearing registers before reading the reset vector. */
    fun reset() = reset(softReset = false)

    /**
     * Resets the CPU and reads the little-endian start address from `$FFFC/$FFFD`.
     * A soft reset preserves A/X/Y and RAM-visible state but consumes stack
     * space and sets I, matching the NES reset sequence.
     */
    fun reset(softReset: Boolean) {
        // Resetting the bus also clears pending DMA work and restores its open-bus state.
        bus.reset()
        state.totalCycles = -1
        if (softReset) {
            // A reset behaves like an interrupt: reserve three stack bytes and mask IRQs.
            state.sp = (state.sp - 3).low8Bits()
            set(I, true)
        } else {
            // Power-on starts the general-purpose registers and status register at their
            // documented NES values. The reset vector below supplies the first PC.
            state.a = 0
            state.x = 0
            state.y = 0
            state.sp = 0xFD
            state.status = I or U
        }
        // The vector is stored little-endian at $FFFC/$FFFD.
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
        for (i in 0..<8) {
            // The NES performs eight reset bus cycles before normal execution resumes.
            bus.idle(CpuBus.CycleType.RESET)
            state.totalCycles++
        }
        state.nmiPending = false
        state.irqLine = false
        state.irqPending = false
        state.irqSample = false
        state.halted = false
    }

    /** Latches a non-maskable interrupt; it is serviced at the next instruction boundary. */
    fun requestNmi() {
        state.nmiPending = true
    }

    /** Drives the IRQ line and immediately records whether a maskable interrupt is pending. */
    fun setIrqLine(asserted: Boolean) {
        state.irqLine = asserted
        state.irqPending = asserted && !flag(I)
    }

    /** Samples IRQ one instruction late, reproducing the 6502 interrupt-input timing. */
    fun sampleIrqLine(asserted: Boolean) {
        state.irqLine = asserted
        state.irqPending = state.irqSample
        state.irqSample = asserted && !flag(I)
    }

    /**
     * Advances the CPU through one instruction or a pending DMA stall.
     * Returns the number of CPU cycles consumed, including interrupt and
     * dummy bus cycles but not any external PPU work.
     */
    fun step(): Int {
        val start = state.totalCycles
        // OAM DMA pauses instruction execution and occupies the CPU bus first.
        val stalls = bus.consumeDmaCycles()
        if (stalls > 0) {
            var stall = 0
            while (stall < stalls) {
                bus.idle(CpuBus.CycleType.STALL)
                state.totalCycles++
                stall++
            }
            return stalls
        }

        // Interrupt priority is NMI, then IRQ. A halted CPU still repeats its opcode
        // fetch behavior, which is how the unofficial KIL instruction is emulated.
        when {
            state.halted -> execute(OPCODES[fetchOpcode()])
            state.nmiPending -> {
                state.nmiPending = false
                serviceInterrupt(0xFFFA)
            }

            state.irqPending -> serviceInterrupt(0xFFFE)
            else -> {
                val opcode = fetchOpcode()
                execute(OPCODES[opcode])
            }
        }
        return (state.totalCycles - start).toInt()
    }

    /** Decodes an opcode-table entry and dispatches its instruction semantics and bus sequence. */
    private fun execute(encodedOpcode: Int) {
        // The table entry packs the mnemonic, addressing mode, and bus-sequence flags.
        val instruction = encodedOpcode and OPERATION_MASK
        val mode = (encodedOpcode ushr MODE_SHIFT) and MODE_MASK
        when {
            // These instructions have custom stack/control-flow cycles.
            instruction == BRK -> brk()
            instruction == JSR -> jsr()
            instruction == JMP -> jump(mode)
            instruction == RTS -> rts()
            instruction == RTI -> rti()
            instruction == PHP -> php()
            instruction == PHA -> pha()
            instruction == PLP -> plp()
            instruction == PLA -> pla()

            // Branches fetch their signed offset only after the opcode fetch.
            (encodedOpcode and BRANCH_FLAG) != 0 -> executeBranch(instruction)
            (encodedOpcode and UNSTABLE_WRITE_FLAG) != 0 -> unstableStore(instruction, mode)
            (encodedOpcode and WRITE_FLAG) != 0 -> executeStore(instruction, mode)

            instruction == ASL || instruction == LSR || instruction == ROL || instruction == ROR ->
                executeShift(instruction, mode)

            (encodedOpcode and RMW_FLAG) != 0 -> executeReadModifyWrite(instruction, mode)
            instruction == KIL -> halt()

            else -> executeReadOrImplied(instruction, mode)
        }
    }

    /** Fetches the branch offset and delegates condition evaluation and timing to [branch]. */
    private fun executeBranch(instruction: Int) {
        branch(instruction, fetch())
    }

    /** Resolves a store destination before selecting and writing its register value. */
    private fun executeStore(instruction: Int, mode: Int) {
        val target = address(mode, write = true)
        write(target, storeValue(instruction))
    }

    /** Executes an accumulator shift or a memory read-modify-write shift. */
    private fun executeShift(instruction: Int, mode: Int) {
        if (mode == ACC) {
            // Accumulator shifts never touch memory; the implied cycle is still present.
            impliedRead()
            state.a = transform(instruction, state.a)
        } else {
            // Memory shifts use the full read, dummy-write, final-write sequence.
            modify(address(mode, write = true), instruction)
        }
    }

    /** Executes a non-shift read-modify-write instruction at its effective address. */
    private fun executeReadModifyWrite(instruction: Int, mode: Int) {
        modify(address(mode, write = true), instruction)
    }

    /** Executes the unofficial KIL instruction by repeatedly fetching the same opcode. */
    private fun halt() {
        state.pc = (state.pc - 1).low16Bits()
        state.halted = true
        state.irqPending = false
        state.nmiPending = false
    }

    /** Pushes status with B/U set, as required by PHP. */
    private fun php() {
        pushInstruction(state.status or B or U)
    }

    /** Pushes the accumulator value for PHA. */
    private fun pha() {
        pushInstruction(state.a)
    }

    /** Pulls status for PLP while preserving the emulator's B/U representation. */
    private fun plp() {
        impliedRead()
        dummyRead(0x100 or state.sp)
        state.status = (pull() and (B or U).inv()) or U
    }

    /** Pulls the accumulator for PLA and updates its zero and negative flags. */
    private fun pla() {
        impliedRead()
        dummyRead(0x100 or state.sp)
        state.a = pull()
        zn(state.a)
    }

    /** Executes an implied addressing-mode instruction after its required bus read. */
    private fun executeImplied(instruction: Int) {
        // Even instructions with no operand perform a bus read during their implied cycle.
        impliedRead()
        when (instruction) {
            CLC -> set(C, false)
            SEC -> set(C, true)
            CLI -> set(I, false)
            SEI -> set(I, true)
            CLV -> set(V, false)
            CLD -> set(D, false)
            SED -> set(D, true)
            TAX -> {
                state.x = state.a; zn(state.x)
            }

            TAY -> {
                state.y = state.a; zn(state.y)
            }

            TXA -> {
                state.a = state.x; zn(state.a)
            }

            TYA -> {
                state.a = state.y; zn(state.a)
            }

            TSX -> {
                state.x = state.sp; zn(state.x)
            }

            TXS -> state.sp = state.x
            DEX -> {
                state.x = (state.x - 1).low8Bits(); zn(state.x)
            }

            DEY -> {
                state.y = (state.y - 1).low8Bits(); zn(state.y)
            }

            INX -> {
                state.x = (state.x + 1).low8Bits(); zn(state.x)
            }

            INY -> {
                state.y = (state.y + 1).low8Bits(); zn(state.y)
            }

            NOP -> Unit
            else -> error("Unsupported implied instruction $instruction")
        }
    }

    /** Executes an instruction whose operand has already been fetched from memory. */
    private fun executeRead(instruction: Int, value: Int) {
        when (instruction) {
            ORA -> {
                state.a = state.a or value
                zn(state.a)
            }

            AND -> {
                state.a = state.a and value
                zn(state.a)
            }

            EOR -> {
                state.a = state.a xor value
                zn(state.a)
            }

            ADC -> adc(value)
            SBC -> sbc(value)
            CMP -> compare(state.a, value)
            CPX -> compare(state.x, value)
            CPY -> compare(state.y, value)
            BIT -> bit(value)
            LDA -> {
                state.a = value
                zn(state.a)
            }

            LDX -> {
                state.x = value
                zn(state.x)
            }

            LDY -> {
                state.y = value
                zn(state.y)
            }

            LAX -> {
                state.a = value
                state.x = value
                zn(value)
            }

            LAS -> {
                val result = value and state.sp
                state.a = result
                state.x = result
                state.sp = result
                zn(result)
            }

            ANC -> {
                state.a = state.a and value
                zn(state.a)
                set(C, flag(N))
            }

            ALR -> {
                state.a = lsrValue(state.a and value)
            }

            ARR -> arr(value)
            XAA -> {
                state.a = (state.a or 0xEE) and state.x and value
                zn(state.a)
            }

            AXS -> axs(value)
            NOP -> Unit
            else -> error("Unsupported read instruction $instruction")
        }
    }

    /** Executes flag, transfer, increment/decrement, NOP, and operand-reading instructions. */
    private fun executeReadOrImplied(instruction: Int, mode: Int) {
        if (mode == IMP) {
            executeImplied(instruction)
            return
        }

        // All remaining instructions consume their operand before applying the mnemonic.
        val value = readOperand(mode)
        executeRead(instruction, value)
    }

    /** Resolves an immediate or memory operand, including the read cycle required by its mode. */
    private fun readOperand(mode: Int): Int = when (mode) {
        IMM -> fetch()
        else -> read(address(mode, write = false))
    }

    /** Calculates an effective address and performs addressing-mode dummy accesses. */
    private fun address(mode: Int, write: Boolean): Int = when (mode) {
        ZP -> fetch()
        ZPX, ZPY -> {
            // Zero-page indexing wraps at $00FF instead of carrying into page $01.
            val base = fetch()
            dummyRead(base)
            (base + if (mode == ZPX) state.x else state.y).low8Bits()
        }

        ABS -> absolute()
        AX, AY -> indexedAbsolute(if (mode == AX) state.x else state.y, write)
        IX -> {
            // Indexed-indirect first wraps the operand in zero page, then reads a 16-bit pointer.
            val operand = fetch()
            dummyRead(operand)
            val pointer = (operand + state.x).low8Bits()
            read(pointer) or (read((pointer + 1).low8Bits()) shl 8)
        }

        IY -> {
            // Indirect-indexed reads the pointer first, then adds Y to form the target address.
            val pointer = fetch()
            val base = read(pointer) or (read((pointer + 1).low8Bits()) shl 8)
            val result = (base + state.y).low16Bits()
            // Writes always pay the dummy read; reads pay it only when indexing crosses a page.
            if (write || base.pageBase() != result.pageBase()) {
                dummyRead(base.pageBase() or result.low8Bits())
            }
            result
        }

        else -> error("Address mode $mode has no memory address")
    }

    /** Adds an index to an absolute address and handles page-crossing or write dummy reads. */
    private fun indexedAbsolute(index: Int, alwaysDummy: Boolean): Int {
        val base = absolute()
        val result = (base + index).low16Bits()
        // The failed-page address is placed on the bus before the corrected address.
        if (alwaysDummy || base.pageBase() != result.pageBase()) {
            dummyRead(base.pageBase() or result.low8Bits())
        }
        return result
    }

    /** Performs a read-modify-write instruction, including unofficial combined operations. */
    private fun modify(address: Int, instruction: Int) {
        // NMOS 6502 read-modify-write instructions write the original value once,
        // then write the transformed value. Devices can observe both writes.
        val old = read(address)
        dummyWrite(address, old)
        val result = when (instruction) {
            SLO -> {
                // Shift memory, then OR the shifted result into A.
                val transformed = transform(ASL, old)
                state.a = state.a or transformed
                zn(state.a)
                transformed
            }

            RLA -> {
                // Rotate memory, then AND the rotated result into A.
                val transformed = transform(ROL, old)
                state.a = state.a and transformed
                zn(state.a)
                transformed
            }

            SRE -> {
                // Shift memory, then XOR the shifted result into A.
                val transformed = transform(LSR, old)
                state.a = state.a xor transformed
                zn(state.a)
                transformed
            }

            RRA -> {
                // Rotate memory, then add the rotated result to A with carry.
                val transformed = transform(ROR, old)
                adc(transformed)
                transformed
            }

            DCP -> {
                // Decrement memory, then compare A with the decremented value.
                val transformed = (old - 1).low8Bits()
                compare(state.a, transformed)
                transformed
            }

            ISB -> {
                // Increment memory, then subtract the incremented value from A.
                val transformed = (old + 1).low8Bits()
                sbc(transformed)
                transformed
            }

            else -> transform(instruction, old)
        }
        write(address, result)
    }

    /** Applies a primitive shift, rotate, increment, or decrement and updates its flags. */
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

    /** Selects the value written by STA, STX, STY, or unofficial SAX. */
    private fun storeValue(instruction: Int): Int = when (instruction) {
        STA -> state.a
        STX -> state.x
        STY -> state.y
        SAX -> state.a and state.x
        else -> error("Unsupported store instruction $instruction")
    }

    /** Executes the NMOS-only unstable stores whose high-byte behavior depends on the target address. */
    private fun unstableStore(instruction: Int, mode: Int) {
        // First obtain the unindexed base address, using the instruction's addressing mode.
        val base = if (mode == IY) {
            val pointer = fetch()
            read(pointer) or (read((pointer + 1).low8Bits()) shl 8)
        } else {
            absolute()
        }
        val index = if (mode == AX) state.x else state.y
        val target = (base + index).low16Bits()
        // Unstable stores perform the same preliminary read as other indexed writes.
        dummyRead(base.pageBase() or target.low8Bits())
        val valueRegister = when (instruction) {
            SHY -> state.y
            SHX -> state.x
            AHX -> state.a and state.x
            TAS -> {
                state.a and state.x.also { state.sp = it }
            }

            else -> 0
        }
        val value = valueRegister and (((base shr 8) + 1).low8Bits())
        // On a page crossing, the corrupted high byte also changes the destination address.
        val destination = if (base.pageBase() != target.pageBase()) {
            target.low8Bits() or (((target shr 8) and valueRegister) shl 8)
        } else {
            target
        }
        write(destination, value)
    }

    /** Performs a real CPU write and advances the cycle counter by the bus-reported write cycle. */
    private fun write(address: Int, value: Int) {
        // CpuBus owns memory mapping and reports the write to all mapped devices.
        bus.cpuWrite(address, value)
        state.totalCycles++
    }

    /** Performs the write half of a read-modify-write bus sequence without changing its value. */
    private fun dummyWrite(address: Int, value: Int) {
        bus.cpuWrite(address, value, dummy = true)
        state.totalCycles++
    }

    /** Performs a normal CPU read, returning its byte and accounting for bus timing. */
    private fun read(address: Int, opcodeFetch: Boolean = false): Int {
        // The bus result packs the value in the low byte and consumed cycles above bit 8.
        val result = bus.cpuRead(address, state.totalCycles, opcodeFetch = opcodeFetch)
        state.totalCycles += result ushr READ_CYCLES_SHIFT
        return result and 0xFF
    }

    /** Performs an address-only read needed for a 6502 dummy cycle or opcode fetch sequence. */
    private fun dummyRead(address: Int, opcodeFetch: Boolean = false): Int {
        val result = bus.cpuRead(address, state.totalCycles, dummy = true, opcodeFetch = opcodeFetch)
        state.totalCycles += result ushr READ_CYCLES_SHIFT
        return result and 0xFF
    }

    /** Reads an instruction operand at PC and advances PC with 16-bit wraparound. */
    private fun fetch(): Int {
        // Operand fetches advance PC independently from opcode fetches.
        val value = read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

    /** Reads the next opcode at PC, marking the bus access as an opcode fetch. */
    private fun fetchOpcode(): Int {
        val value = read(state.pc, opcodeFetch = true)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

    /** Performs the otherwise-unused read cycle present in implied instructions. */
    private fun impliedRead() {
        dummyRead(state.pc)
    }

    /** Fetches a little-endian 16-bit absolute address from the instruction stream. */
    private fun absolute(): Int = fetch() or (fetch() shl 8)

    /** Pushes one byte to page `$01xx` and decrements the 8-bit stack pointer. */
    private fun push(value: Int) {
        write(0x100 or state.sp, value)
        state.sp = (state.sp - 1).low8Bits()
    }

    /** Increments the stack pointer and pulls one byte from page `$01xx`. */
    private fun pull(): Int {
        state.sp = (state.sp + 1).low8Bits()
        return read(0x100 or state.sp)
    }

    /** Executes the implied read and stack write shared by PHP and PHA. */
    private fun pushInstruction(value: Int) {
        impliedRead()
        push(value)
    }

    /** Services a hardware IRQ or NMI by pushing PC/status and loading the selected vector. */
    private fun serviceInterrupt(vector: Int) {
        // Interrupt entry begins with two internal/dummy reads before stack activity.
        dummyRead(state.pc, opcodeFetch = true)
        dummyRead(state.pc)
        push(state.pc shr 8)
        push(state.pc)
        val selectedVector = if (state.nmiPending) {
            // An NMI arriving during entry wins over the originally requested IRQ vector.
            state.nmiPending = false
            0xFFFA
        } else {
            vector
        }
        push((state.status or U) and B.inv())
        // Hardware interrupts clear B in the pushed copy and mask further IRQs.
        set(I, true)
        state.pc = read(selectedVector) or (read(selectedVector + 1) shl 8)
        state.irqPending = false
    }

    /** Executes BRK, including its padding-byte read and software-interrupt stack frame. */
    private fun brk() {
        fetch() // BRK's padding byte is a real read.
        // BRK pushes the PC after its padding byte, followed by a status copy with B set.
        push(state.pc shr 8)
        push(state.pc)
        val vector = if (state.nmiPending) {
            state.nmiPending = false
            0xFFFA
        } else {
            0xFFFE
        }
        push(state.status or B or U)
        set(I, true)
        state.pc = read(vector) or (read(vector + 1) shl 8)
    }

    /** Executes JSR by pushing the return address and loading the absolute target. */
    private fun jsr() {
        // JSR fetches the low target byte first, then pushes the address of that low byte.
        val low = fetch()
        dummyRead(0x100 or state.sp)
        push(state.pc shr 8)
        push(state.pc)
        state.pc = low or (fetch() shl 8)
    }

    /** Executes JMP absolute or indirect, including the NMOS indirect-page-wrap bug. */
    private fun jump(mode: Int) {
        if (mode == ABS) {
            // Absolute JMP simply replaces PC with the fetched target.
            state.pc = absolute()
        } else {
            // Indirect JMP reads a pointer; NMOS hardware wraps the high-byte read in page.
            val pointer = absolute()
            val highAddress = pointer.pageBase() or ((pointer + 1).low8Bits())
            state.pc = read(pointer) or (read(highAddress) shl 8)
        }
    }

    /** Executes RTS by pulling a return address, reading it, and advancing past the call. */
    private fun rts() {
        impliedRead()
        dummyRead(0x100 or state.sp)
        val low = pull()
        val high = pull()
        val returnAddress = low or (high shl 8)
        dummyRead(returnAddress)
        state.pc = (returnAddress + 1).low16Bits()
    }

    /** Executes RTI by restoring status and the interrupted program counter from the stack. */
    private fun rti() {
        impliedRead()
        dummyRead(0x100 or state.sp)
        state.status = (pull() and (B or U).inv()) or U
        state.pc = pull() or (pull() shl 8)
    }

    /** Evaluates a conditional branch and performs its taken/page-crossing dummy reads. */
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
        // A taken branch first performs a dummy read at the old PC.
        val oldPc = state.pc
        dummyRead(oldPc)
        val signed = if (offset < 0x80) offset else offset - 0x100
        val target = (oldPc + signed).low16Bits()
        // Crossing a page requires one additional read from the partially corrected address.
        if (oldPc.pageBase() != target.pageBase()) {
            dummyRead(oldPc.pageBase() or target.low8Bits())
        }
        state.pc = target
    }

    /** Reports whether a processor-status flag bit is currently set. */
    private fun flag(flag: Int): Boolean = (state.status and flag) != 0

    /** Sets one status bit while enforcing the 6502's always-set U and clear B representation. */
    private fun set(flag: Int, enabled: Boolean) {
        // B is not a persistent latch and U is always observed as set in this representation.
        state.status = if (enabled) state.status or flag else state.status and flag.inv()
        state.status = (state.status or U) and B.inv()
    }

    /** Updates zero and negative flags from an 8-bit result. */
    private fun zn(value: Int) {
        val result = value.low8Bits()
        state.status = (state.status and (Z or N).inv()) or (result and N) or if (result == 0) Z else 0
    }

    /** Adds an operand and carry to A, setting binary carry, overflow, zero, and negative flags. */
    private fun adc(value: Int) {
        // Carry is an input to the sum and an output when the 9-bit result overflows.
        val sum = state.a + value + if (flag(C)) 1 else 0
        val result = sum.low8Bits()
        set(C, sum > 0xFF)
        // Overflow occurs when same-sign operands produce a result with the opposite sign.
        set(V, ((state.a xor result) and (value xor result) and 0x80) != 0)
        state.a = result
        zn(state.a)
    }

    /** Implements SBC through one's-complement addition, preserving 6502 carry semantics. */
    private fun sbc(value: Int) = adc(value xor 0xFF)

    /** Compares a register with an operand by subtraction without storing the difference. */
    private fun compare(register: Int, value: Int) {
        val result = (register - value).low8Bits()
        set(C, register >= value)
        zn(result)
    }

    /** Executes BIT by testing A and copying operand sign/overflow bits into status. */
    private fun bit(value: Int) {
        set(Z, (state.a and value) == 0)
        set(V, (value and V) != 0)
        set(N, (value and N) != 0)
    }

    /** Shifts an 8-bit value left, moving bit 7 into carry and updating zero/negative. */
    private fun aslValue(value: Int): Int {
        set(C, (value and 0x80) != 0)
        val result = (value shl 1).low8Bits()
        zn(result)
        return result
    }

    /** Shifts an 8-bit value right, moving bit 0 into carry and updating zero/negative. */
    private fun lsrValue(value: Int): Int {
        set(C, (value and 1) != 0)
        val result = (value ushr 1).low8Bits()
        zn(result)
        return result
    }

    /** Rotates an 8-bit value left through carry and updates zero/negative. */
    private fun rolValue(value: Int): Int {
        val carry = if (flag(C)) 1 else 0
        set(C, (value and 0x80) != 0)
        val result = ((value shl 1) or carry).low8Bits()
        zn(result)
        return result
    }

    /** Rotates an 8-bit value right through carry and updates zero/negative. */
    private fun rorValue(value: Int): Int {
        val carry = if (flag(C)) 0x80 else 0
        set(C, (value and 1) != 0)
        val result = ((value ushr 1) or carry).low8Bits()
        zn(result)
        return result
    }

    /** Executes unofficial ARR: ANDs A with the operand, rotates right, and derives C/V from bits 6/5. */
    private fun arr(value: Int) {
        state.a = (state.a and value) ushr 1 or if (flag(C)) 0x80 else 0
        zn(state.a)
        set(C, (state.a and 0x40) != 0)
        set(V, ((state.a shr 6) xor (state.a shr 5)) and 1 != 0)
    }

    /** Executes unofficial AXS by subtracting an immediate operand from `A and X` into X. */
    private fun axs(value: Int) {
        val source = state.a and state.x
        state.x = (source - value).low8Bits()
        set(C, source >= value)
        zn(state.x)
    }


}
