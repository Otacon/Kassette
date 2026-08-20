package nes2.cpu

import nes.util.isNegative8Bit
import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.pageBase
import nes2.CpuBus

interface Cpu {
    fun reset(): Int
    fun softReset(): Int
    fun setIrqLine(active: Boolean)
    fun requestNmi()
    fun step(): Int
}

class Cpu6502(
    private val bus: CpuBus,
    private var state: CpuState = CpuState(),
) : Cpu {

    private var pagePenalty = 0

    override fun reset(): Int {
        state.a = 0
        state.x = 0
        state.y = 0
        state.sp = 0xFD
        state.status = 0x24
        resetCommon()
        return RESET_CYCLES
    }

    override fun softReset(): Int {
        state.sp = (state.sp - 3).low8Bits()
        state.i = true
        resetCommon()
        return RESET_CYCLES
    }

    private fun resetCommon() {
        state.irqLine = false
        state.nmiPending = false
        state.irqPollI = true
        state.halted = false
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
    }

    override fun setIrqLine(active: Boolean) {
        state.irqLine = active
    }

    override fun requestNmi() {
        state.nmiPending = true
    }

    override fun step(): Int {
        if (state.halted) {
            return 1
        }

        if (state.nmiPending) {
            state.nmiPending = false
            return nmi()
        }

        if (state.irqLine && !state.i && !state.irqPollI) {
            return irq()
        }

        val opcode = pcRead()
        val iBefore = state.i
        val cycles = execute(opcode)
        state.irqPollI = when (opcode) {
            // CLI, SEI, and PLP affect IRQ polling one instruction later.
            0x58, 0x78, 0x28 -> iBefore
            else -> state.i
        }

        return cycles
    }

    // @formatter:off
    private fun execute(opcode: Int): Int {
        pagePenalty = 0
        return when (opcode) {
            // ADC
            0x69 -> { adc(immediate()); 2 }
            0x65 -> { adc(readZeroPage()); 3 }
            0x75 -> { adc(readZeroPageX()); 4 }
            0x61 -> { adc(readIndirectX()); 6 }
            0x6D -> { adc(readAbsolute()); 4 }
            0x71 -> { adc(readIndirectY()); 5 + pagePenalty }
            0x7D -> { adc(readAbsoluteX()); 4 + pagePenalty }
            0x79 -> { adc(readAbsoluteY()); 4 + pagePenalty }
            // AND
            0x29 -> { and(immediate()); 2 }
            0x25 -> { and(readZeroPage()); 3 }
            0x35 -> { and(readZeroPageX()); 4 }
            0x21 -> { and(readIndirectX()); 6 }
            0x2D -> { and(readAbsolute()); 4 }
            0x31 -> { and(readIndirectY()); 5 + pagePenalty }
            0x3D -> { and(readAbsoluteX()); 4 + pagePenalty }
            0x39 -> { and(readAbsoluteY()); 4 + pagePenalty }
            // ORA
            0x09 -> { ora(immediate()); 2 }
            0x05 -> { ora(readZeroPage()); 3 }
            0x15 -> { ora(readZeroPageX()); 4 }
            0x01 -> { ora(readIndirectX()); 6 }
            0x0D -> { ora(readAbsolute()); 4 }
            0x11 -> { ora(readIndirectY()); 5 + pagePenalty }
            0x1D -> { ora(readAbsoluteX()); 4 + pagePenalty }
            0x19 -> { ora(readAbsoluteY()); 4 + pagePenalty }
            // EOR
            0x49 -> { eor(immediate()); 2 }
            0x45 -> { eor(readZeroPage()); 3 }
            0x55 -> { eor(readZeroPageX()); 4 }
            0x41 -> { eor(readIndirectX()); 6 }
            0x4D -> { eor(readAbsolute()); 4 }
            0x51 -> { eor(readIndirectY()); 5 + pagePenalty }
            0x5D -> { eor(readAbsoluteX()); 4 + pagePenalty }
            0x59 -> { eor(readAbsoluteY()); 4 + pagePenalty }
            // LDA
            0xA9 -> { lda(immediate()); 2 }
            0xA5 -> { lda(readZeroPage()); 3 }
            0xB5 -> { lda(readZeroPageX()); 4 }
            0xA1 -> { lda(readIndirectX()); 6 }
            0xAD -> { lda(readAbsolute()); 4 }
            0xB1 -> { lda(readIndirectY()); 5 + pagePenalty }
            0xBD -> { lda(readAbsoluteX()); 4 + pagePenalty }
            0xB9 -> { lda(readAbsoluteY()); 4 + pagePenalty }
            // CMP
            0xC9 -> { cmp(immediate()); 2 }
            0xC5 -> { cmp(readZeroPage()); 3 }
            0xD5 -> { cmp(readZeroPageX()); 4 }
            0xC1 -> { cmp(readIndirectX()); 6 }
            0xCD -> { cmp(readAbsolute()); 4 }
            0xD1 -> { cmp(readIndirectY()); 5 + pagePenalty }
            0xDD -> { cmp(readAbsoluteX()); 4 + pagePenalty }
            0xD9 -> { cmp(readAbsoluteY()); 4 + pagePenalty }
            // SBC
            0xE9 -> { sbc(immediate()); 2 }
            0xE5 -> { sbc(readZeroPage()); 3 }
            0xF5 -> { sbc(readZeroPageX()); 4 }
            0xE1 -> { sbc(readIndirectX()); 6 }
            0xED -> { sbc(readAbsolute()); 4 }
            0xF1 -> { sbc(readIndirectY()); 5 + pagePenalty }
            0xFD -> { sbc(readAbsoluteX()); 4 + pagePenalty }
            0xF9 -> { sbc(readAbsoluteY()); 4 + pagePenalty }
            // LDX
            0xA2 -> { ldx(immediate()); 2 }
            0xA6 -> { ldx(readZeroPage()); 3 }
            0xB6 -> { ldx(readZeroPageY()); 4 }
            0xAE -> { ldx(readAbsolute()); 4 }
            0xBE -> { ldx(readAbsoluteY()); 4 + pagePenalty }
            // LDY
            0xA0 -> { ldy(immediate()); 2 }
            0xA4 -> { ldy(readZeroPage()); 3 }
            0xB4 -> { ldy(readZeroPageX()); 4 }
            0xAC -> { ldy(readAbsolute()); 4 }
            0xBC -> { ldy(readAbsoluteX()); 4 + pagePenalty }
            // STA
            0x85 -> { sta(addressZeroPage()); 3 }
            0x95 -> { sta(addressZeroPageX()); 4 }
            0x8D -> { sta(addressAbsolute()); 4 }
            0x9D -> { sta(addressAbsoluteX()); 5 }
            0x99 -> { sta(addressAbsoluteY()); 5 }
            0x81 -> { sta(addressIndirectX()); 6 }
            0x91 -> { sta(addressIndirectY()); 6 }
            // STX
            0x86 -> { stx(addressZeroPage()); 3 }
            0x96 -> { stx(addressZeroPageY()); 4 }
            0x8E -> { stx(addressAbsolute()); 4 }
            // STY
            0x84 -> { sty(addressZeroPage()); 3 }
            0x94 -> { sty(addressZeroPageX()); 4 }
            0x8C -> { sty(addressAbsolute()); 4 }
            // LAX
            0xAB -> { lax(immediate()); 2 }
            0xA7 -> { lax(readZeroPage()); 3 }
            0xB7 -> { lax(readZeroPageY()); 4 }
            0xAF -> { lax(readAbsolute()); 4 }
            0xBF -> { lax(readAbsoluteY()); 4 + pagePenalty }
            0xA3 -> { lax(readIndirectX()); 6 }
            0xB3 -> { lax(readIndirectY()); 5 + pagePenalty }
            // LAS
            0xBB -> { las(readAbsoluteY()); 4 + pagePenalty }
            // SAX
            0x87 -> { sax(addressZeroPage()); 3 }
            0x97 -> { sax(addressZeroPageY()); 4 }
            0x8F -> { sax(addressAbsolute()); 4 }
            0x83 -> { sax(addressIndirectX()); 6 }
            // AHX
            0x93 -> { ahxIndirectY(); 6 }
            0x9F -> { ahxAbsoluteY(); 5 }
            // SHY
            0x9C -> { shyAbsoluteX(); 5 }
            // SHX
            0x9E -> { shxAbsoluteY(); 5 }
            // TAS
            0x9B -> { tasAbsoluteY(); 5 }
            // ANC
            0x0B, 0x2B -> { anc(immediate()); 2 }
            // ALR
            0x4B -> { alr(immediate()); 2 }
            // ARR
            0x6B -> { arr(immediate()); 2 }
            // XAA
            0x8B -> { xaa(immediate()); 2 }
            // AXS/SBX
            0xCB -> { axs(immediate()); 2 }
            // SLO
            0x03 -> { slo(addressIndirectX()); 8 }
            0x07 -> { slo(addressZeroPage()); 5 }
            0x0F -> { slo(addressAbsolute()); 6 }
            0x13 -> { slo(addressIndirectY()); 8 }
            0x17 -> { slo(addressZeroPageX()); 6 }
            0x1B -> { slo(addressAbsoluteY()); 7 }
            0x1F -> { slo(addressAbsoluteX()); 7 }
            // RLA
            0x23 -> { rla(addressIndirectX()); 8 }
            0x27 -> { rla(addressZeroPage()); 5 }
            0x2F -> { rla(addressAbsolute()); 6 }
            0x33 -> { rla(addressIndirectY()); 8 }
            0x37 -> { rla(addressZeroPageX()); 6 }
            0x3B -> { rla(addressAbsoluteY()); 7 }
            0x3F -> { rla(addressAbsoluteX()); 7 }
            // SRE
            0x43 -> { sre(addressIndirectX()); 8 }
            0x47 -> { sre(addressZeroPage()); 5 }
            0x4F -> { sre(addressAbsolute()); 6 }
            0x53 -> { sre(addressIndirectY()); 8 }
            0x57 -> { sre(addressZeroPageX()); 6 }
            0x5B -> { sre(addressAbsoluteY()); 7 }
            0x5F -> { sre(addressAbsoluteX()); 7 }
            // RRA
            0x63 -> { rra(addressIndirectX()); 8 }
            0x67 -> { rra(addressZeroPage()); 5 }
            0x6F -> { rra(addressAbsolute()); 6 }
            0x73 -> { rra(addressIndirectY()); 8 }
            0x77 -> { rra(addressZeroPageX()); 6 }
            0x7B -> { rra(addressAbsoluteY()); 7 }
            0x7F -> { rra(addressAbsoluteX()); 7 }
            // DCP
            0xC3 -> { dcp(addressIndirectX()); 8 }
            0xC7 -> { dcp(addressZeroPage()); 5 }
            0xCF -> { dcp(addressAbsolute()); 6 }
            0xD3 -> { dcp(addressIndirectY()); 8 }
            0xD7 -> { dcp(addressZeroPageX()); 6 }
            0xDB -> { dcp(addressAbsoluteY()); 7 }
            0xDF -> { dcp(addressAbsoluteX()); 7 }
            // ISC/ISB
            0xE3 -> { isc(addressIndirectX()); 8 }
            0xE7 -> { isc(addressZeroPage()); 5 }
            0xEF -> { isc(addressAbsolute()); 6 }
            0xF3 -> { isc(addressIndirectY()); 8 }
            0xF7 -> { isc(addressZeroPageX()); 6 }
            0xFB -> { isc(addressAbsoluteY()); 7 }
            0xFF -> { isc(addressAbsoluteX()); 7 }
            // Transfers, increments, decrements
            0xAA -> { tax(); 2 }
            0xA8 -> { tay(); 2 }
            0x8A -> { txa(); 2 }
            0x98 -> { tya(); 2 }
            0xBA -> { tsx(); 2 }
            0x9A -> { txs(); 2 }
            0xE8 -> { inx(); 2 }
            0xC8 -> { iny(); 2 }
            0xCA -> { dex(); 2 }
            0x88 -> { dey(); 2 }
            // CPX, CPY, BIT
            0xE0 -> { cpx(immediate()); 2 }
            0xE4 -> { cpx(readZeroPage()); 3 }
            0xEC -> { cpx(readAbsolute()); 4 }
            0xC0 -> { cpy(immediate()); 2 }
            0xC4 -> { cpy(readZeroPage()); 3 }
            0xCC -> { cpy(readAbsolute()); 4 }
            0x24 -> { bit(readZeroPage()); 3 }
            0x2C -> { bit(readAbsolute()); 4 }
            // Flags
            0x18 -> { clc(); 2 }
            0x38 -> { sec(); 2 }
            0x58 -> { cli(); 2 }
            0x78 -> { sei(); 2 }
            0xB8 -> { clv(); 2 }
            0xD8 -> { cld(); 2 }
            0xF8 -> { sed(); 2 }
            // INC, DEC
            0xE6 -> { inc(addressZeroPage()); 5 }
            0xF6 -> { inc(addressZeroPageX()); 6 }
            0xEE -> { inc(addressAbsolute()); 6 }
            0xFE -> { inc(addressAbsoluteX()); 7 }
            0xC6 -> { dec(addressZeroPage()); 5 }
            0xD6 -> { dec(addressZeroPageX()); 6 }
            0xCE -> { dec(addressAbsolute()); 6 }
            0xDE -> { dec(addressAbsoluteX()); 7 }
            // Shifts and rotates
            0x0A -> { state.a = aslValue(state.a); 2 }
            0x06 -> { rmw(addressZeroPage(), ::aslValue); 5 }
            0x16 -> { rmw(addressZeroPageX(), ::aslValue); 6 }
            0x0E -> { rmw(addressAbsolute(), ::aslValue); 6 }
            0x1E -> { rmw(addressAbsoluteX(), ::aslValue); 7 }
            0x4A -> { state.a = lsrValue(state.a); 2 }
            0x46 -> { rmw(addressZeroPage(), ::lsrValue); 5 }
            0x56 -> { rmw(addressZeroPageX(), ::lsrValue); 6 }
            0x4E -> { rmw(addressAbsolute(), ::lsrValue); 6 }
            0x5E -> { rmw(addressAbsoluteX(), ::lsrValue); 7 }
            0x2A -> { state.a = rolValue(state.a); 2 }
            0x26 -> { rmw(addressZeroPage(), ::rolValue); 5 }
            0x36 -> { rmw(addressZeroPageX(), ::rolValue); 6 }
            0x2E -> { rmw(addressAbsolute(), ::rolValue); 6 }
            0x3E -> { rmw(addressAbsoluteX(), ::rolValue); 7 }
            0x6A -> { state.a = rorValue(state.a); 2 }
            0x66 -> { rmw(addressZeroPage(), ::rorValue); 5 }
            0x76 -> { rmw(addressZeroPageX(), ::rorValue); 6 }
            0x6E -> { rmw(addressAbsolute(), ::rorValue); 6 }
            0x7E -> { rmw(addressAbsoluteX(), ::rorValue); 7 }
            // Branches
            0x90 -> 2 + branch(!state.c)
            0xB0 -> 2 + branch(state.c)
            0xF0 -> 2 + branch(state.z)
            0xD0 -> 2 + branch(!state.z)
            0x30 -> 2 + branch(state.n)
            0x10 -> 2 + branch(!state.n)
            0x50 -> 2 + branch(!state.v)
            0x70 -> 2 + branch(state.v)
            // Jumps and stack
            0x4C -> { jmpAbsolute(); 3 }
            0x6C -> { jmpIndirect(); 5 }
            0x20 -> { jsr(); 6 }
            0x60 -> { rts(); 6 }
            0x48 -> { pha(); 3 }
            0x68 -> { pla(); 4 }
            0x08 -> { php(); 3 }
            0x28 -> { plp(); 4 }
            0x00 -> { brk(); 7 }
            0x40 -> { rti(); 6 }
            // KIL/JAM
            0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72, 0x92, 0xB2, 0xD2, 0xF2 -> { kil(); 1 }
            // NOP
            0xEA, 0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA -> { nop(); 2 }
            // NOP #imm
            0x80, 0x82, 0x89, 0xC2, 0xE2 -> { immediate(); 2 }
            // NOP zp
            0x04, 0x44, 0x64 -> { readZeroPage(); 3 }
            // NOP zp,X
            0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4 -> { readZeroPageX(); 4 }
            // NOP abs
            0x0C -> { readAbsolute(); 4 }
            // NOP abs,X
            0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC -> { readAbsoluteX(); 4 + pagePenalty }
            // Currently-unimplemented unofficial opcodes.
            else -> { nop(); 2 }
        }
    }
    // @formatter:on
    // region Addressing modes
    private fun immediate(): Int = pcRead()

    private fun readZeroPage(): Int = bus.read(addressZeroPage())

    private fun readZeroPageX(): Int = bus.read(addressZeroPageX())

    private fun readZeroPageY(): Int = bus.read(addressZeroPageY())

    private fun readAbsolute(): Int = bus.read(addressAbsolute())

    private fun readAbsoluteX(): Int = bus.read(addressAbsoluteXWithPagePenalty())

    private fun readAbsoluteY(): Int = bus.read(addressAbsoluteYWithPagePenalty())

    private fun readIndirectX(): Int = bus.read(addressIndirectX())

    private fun readIndirectY(): Int = bus.read(addressIndirectYWithPagePenalty())

    private fun addressZeroPage(): Int = pcRead()

    private fun addressZeroPageX(): Int {
        val baseAddress = pcRead()
        bus.read(baseAddress)
        return (baseAddress + state.x).low8Bits()
    }

    private fun addressZeroPageY(): Int {
        val baseAddress = pcRead()
        bus.read(baseAddress)
        return (baseAddress + state.y).low8Bits()
    }

    private fun addressAbsolute(): Int {
        val lo = pcRead()
        val hi = pcRead()
        return lo or (hi shl 8)
    }

    private fun addressAbsoluteX(): Int {
        val baseAddress = addressAbsolute()
        val address = (baseAddress + state.x).low16Bits()
        readSpeculativeIndexedAddress(baseAddress, address)
        return address
    }

    private fun addressAbsoluteY(): Int {
        val baseAddress = addressAbsolute()
        val address = (baseAddress + state.y).low16Bits()
        readSpeculativeIndexedAddress(baseAddress, address)
        return address
    }

    private fun addressAbsoluteXWithPagePenalty(): Int {
        val baseAddress = addressAbsolute()
        val address = (baseAddress + state.x).low16Bits()
        readSpeculativeIndexedAddressIfPageCrossed(baseAddress, address)
        return address
    }

    private fun addressAbsoluteYWithPagePenalty(): Int {
        val baseAddress = addressAbsolute()
        val address = (baseAddress + state.y).low16Bits()
        readSpeculativeIndexedAddressIfPageCrossed(baseAddress, address)
        return address
    }

    private fun addressIndirectX(): Int {
        val basePointer = pcRead()
        bus.read(basePointer)

        val pointer = (basePointer + state.x).low8Bits()
        val lo = bus.read(pointer)
        val hi = bus.read((pointer + 1).low8Bits())

        return lo or (hi shl 8)
    }

    private fun addressIndirectY(): Int {
        val pointer = pcRead()
        val lo = bus.read(pointer)
        val hi = bus.read((pointer + 1).low8Bits())

        val baseAddress = lo or (hi shl 8)
        val address = (baseAddress + state.y).low16Bits()
        readSpeculativeIndexedAddress(baseAddress, address)

        return address
    }

    private fun addressIndirectYWithPagePenalty(): Int {
        val pointer = pcRead()
        val lo = bus.read(pointer)
        val hi = bus.read((pointer + 1).low8Bits())

        val baseAddress = lo or (hi shl 8)
        val address = (baseAddress + state.y).low16Bits()
        readSpeculativeIndexedAddressIfPageCrossed(baseAddress, address)

        return address
    }

    private fun readSpeculativeIndexedAddress(baseAddress: Int, address: Int) {
        val speculativeAddress = (baseAddress and 0xFF00) or (address and 0x00FF)
        bus.read(speculativeAddress)
    }

    private fun readSpeculativeIndexedAddressIfPageCrossed(baseAddress: Int, address: Int) {
        val crossedPage = (baseAddress xor address) and 0xFF00 != 0
        if (crossedPage) {
            pagePenalty = 1
            val speculativeAddress = (baseAddress and 0xFF00) or (address and 0x00FF)
            bus.read(speculativeAddress)
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

    private fun lax(value: Int) {
        val result = value.low8Bits()
        state.a = result
        state.x = result
        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun sax(address: Int) {
        bus.write(address, state.a and state.x)
    }

    private fun ahxIndirectY() {
        val pointer = pcRead()
        val lo = bus.read(pointer)
        val hi = bus.read((pointer + 1).low8Bits())
        val baseAddress = lo or (hi shl 8)
        val targetAddress = (baseAddress + state.y).low16Bits()
        unstableStore(baseAddress, targetAddress, state.a and state.x)
    }

    private fun ahxAbsoluteY() {
        val baseAddress = addressAbsolute()
        val targetAddress = (baseAddress + state.y).low16Bits()
        unstableStore(baseAddress, targetAddress, state.a and state.x)
    }

    private fun shyAbsoluteX() {
        val baseAddress = addressAbsolute()
        val targetAddress = (baseAddress + state.x).low16Bits()
        unstableStore(baseAddress, targetAddress, state.y)
    }

    private fun shxAbsoluteY() {
        val baseAddress = addressAbsolute()
        val targetAddress = (baseAddress + state.y).low16Bits()
        unstableStore(baseAddress, targetAddress, state.x)
    }

    private fun tasAbsoluteY() {
        val valueRegister = state.a and state.x
        state.sp = valueRegister

        val baseAddress = addressAbsolute()
        val targetAddress = (baseAddress + state.y).low16Bits()
        unstableStore(baseAddress, targetAddress, valueRegister)
    }

    private fun unstableStore(baseAddress: Int, targetAddress: Int, valueRegister: Int) {
        readSpeculativeIndexedAddress(baseAddress, targetAddress)

        val value = valueRegister and (((baseAddress ushr 8) + 1).low8Bits())
        val destination = if (baseAddress.pageBase() != targetAddress.pageBase()) {
            targetAddress.low8Bits() or (((targetAddress ushr 8) and valueRegister) shl 8)
        } else {
            targetAddress
        }

        bus.write(destination, value)
    }

    private fun las(value: Int) {
        val result = value and state.sp
        state.a = result
        state.x = result
        state.sp = result
        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun anc(value: Int) {
        and(value)
        state.c = state.n
    }

    private fun alr(value: Int) {
        state.a = lsrValue(state.a and value)
    }

    private fun arr(value: Int) {
        state.a = rorValue(state.a and value)
        state.c = (state.a and 0x40) != 0
        state.v = (((state.a shr 6) xor (state.a shr 5)) and 1) != 0
    }

    private fun xaa(value: Int) {
        state.a = ((state.a or 0xEE) and state.x and value).low8Bits()
        state.z = state.a == 0
        state.n = state.a.isNegative8Bit()
    }

    private fun axs(value: Int) {
        val source = state.a and state.x
        val result = (source - value).low8Bits()
        state.x = result
        state.c = source >= value
        state.z = result == 0
        state.n = result.isNegative8Bit()
    }

    private fun slo(address: Int) {
        ora(rmwValue(address, ::aslValue))
    }

    private fun rla(address: Int) {
        and(rmwValue(address, ::rolValue))
    }

    private fun sre(address: Int) {
        eor(rmwValue(address, ::lsrValue))
    }

    private fun rra(address: Int) {
        adc(rmwValue(address, ::rorValue))
    }

    private fun dcp(address: Int) {
        cmp(rmwValue(address) { (it - 1).low8Bits() })
    }

    private fun isc(address: Int) {
        sbc(rmwValue(address) { (it + 1).low8Bits() })
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

    private fun aslValue(value: Int): Int {
        state.c = value.isNegative8Bit()

        val result = (value shl 1).low8Bits()

        state.z = result == 0
        state.n = result.isNegative8Bit()

        return result
    }

    private fun lsrValue(value: Int): Int {
        state.c = (value and 0x01) != 0

        val result = value ushr 1

        state.z = result == 0
        state.n = result.isNegative8Bit()

        return result
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
        bus.read(oldPc)

        state.pc = newPc

        return if (oldPc.pageBase() != newPc.pageBase()) {
            bus.read(oldPc.pageBase() or (newPc and 0x00FF))
            2
        } else {
            1
        }
    }

    private fun rmw(address: Int, transform: (Int) -> Int) {
        rmwValue(address, transform)
    }

    private fun rmwValue(address: Int, transform: (Int) -> Int): Int {
        val oldValue = bus.read(address)
        bus.write(address, oldValue)
        val result = transform(oldValue)
        bus.write(address, result)
        return result
    }

    private fun jmpAbsolute() {
        state.pc = addressAbsolute()
    }

    private fun jmpIndirect() {
        val pointer = addressAbsolute()
        val targetLo = bus.read(pointer)

        val targetHiAddress =
            if ((pointer and 0x00FF) == 0x00FF) {
                // 6502 hardware bug: $12FF reads high byte from $1200, not $1300.
                pointer and 0xFF00
            } else {
                (pointer + 1).low16Bits()
            }

        val targetHi = bus.read(targetHiAddress)

        state.pc = targetLo or (targetHi shl 8)
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

    private fun kil() {
        state.pc = (state.pc - 1).low16Bits()
        state.halted = true
        state.irqLine = false
        state.nmiPending = false
    }

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
