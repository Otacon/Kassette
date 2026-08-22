package nes2.cpu

class NesCpu(private val host: NesCpuHost) {
    companion object {
        const val NMIVector = 0xFFFA
        const val ResetVector = 0xFFFC
        const val IRQVector = 0xFFFE
    }

    private var masterClock: Long = 0
    private var ppuOffset: Int = 0
    private var startClockCount: Int = 6
    private var endClockCount: Int = 6
    private var operand: Int = 0

    private val opTable: Array<NesCpu.() -> Unit> = arrayOf(
        { BRK() }, { ORA() }, { HLT() }, { SLO() }, { NOP() }, { ORA() }, { ASL_Memory() }, { SLO() }, { PHP() }, { ORA() }, { ASL_Acc() }, { AAC() }, { NOP() }, { ORA() }, { ASL_Memory() }, { SLO() },
        { BPL() }, { ORA() }, { HLT() }, { SLO() }, { NOP() }, { ORA() }, { ASL_Memory() }, { SLO() }, { CLC() }, { ORA() }, { NOP() }, { SLO() }, { NOP() }, { ORA() }, { ASL_Memory() }, { SLO() },
        { JSR() }, { AND() }, { HLT() }, { RLA() }, { BIT() }, { AND() }, { ROL_Memory() }, { RLA() }, { PLP() }, { AND() }, { ROL_Acc() }, { AAC() }, { BIT() }, { AND() }, { ROL_Memory() }, { RLA() },
        { BMI() }, { AND() }, { HLT() }, { RLA() }, { NOP() }, { AND() }, { ROL_Memory() }, { RLA() }, { SEC() }, { AND() }, { NOP() }, { RLA() }, { NOP() }, { AND() }, { ROL_Memory() }, { RLA() },
        { RTI() }, { EOR() }, { HLT() }, { SRE() }, { NOP() }, { EOR() }, { LSR_Memory() }, { SRE() }, { PHA() }, { EOR() }, { LSR_Acc() }, { ASR() }, { JMP_Abs() }, { EOR() }, { LSR_Memory() }, { SRE() },
        { BVC() }, { EOR() }, { HLT() }, { SRE() }, { NOP() }, { EOR() }, { LSR_Memory() }, { SRE() }, { CLI() }, { EOR() }, { NOP() }, { SRE() }, { NOP() }, { EOR() }, { LSR_Memory() }, { SRE() },
        { RTS() }, { ADC() }, { HLT() }, { RRA() }, { NOP() }, { ADC() }, { ROR_Memory() }, { RRA() }, { PLA() }, { ADC() }, { ROR_Acc() }, { ARR() }, { JMP_Ind() }, { ADC() }, { ROR_Memory() }, { RRA() },
        { BVS() }, { ADC() }, { HLT() }, { RRA() }, { NOP() }, { ADC() }, { ROR_Memory() }, { RRA() }, { SEI() }, { ADC() }, { NOP() }, { RRA() }, { NOP() }, { ADC() }, { ROR_Memory() }, { RRA() },
        { NOP() }, { STA() }, { NOP() }, { SAX() }, { STY() }, { STA() }, { STX() }, { SAX() }, { DEY() }, { NOP() }, { TXA() }, { ANE() }, { STY() }, { STA() }, { STX() }, { SAX() },
        { BCC() }, { STA() }, { HLT() }, { SHAZ() }, { STY() }, { STA() }, { STX() }, { SAX() }, { TYA() }, { STA() }, { TXS() }, { TAS() }, { SHY() }, { STA() }, { SHX() }, { SHAA() },
        { LDY() }, { LDA() }, { LDX() }, { LAX() }, { LDY() }, { LDA() }, { LDX() }, { LAX() }, { TAY() }, { LDA() }, { TAX() }, { ATX() }, { LDY() }, { LDA() }, { LDX() }, { LAX() },
        { BCS() }, { LDA() }, { HLT() }, { LAX() }, { LDY() }, { LDA() }, { LDX() }, { LAX() }, { CLV() }, { LDA() }, { TSX() }, { LAS() }, { LDY() }, { LDA() }, { LDX() }, { LAX() },
        { CPY() }, { CPA() }, { NOP() }, { DCP() }, { CPY() }, { CPA() }, { DEC() }, { DCP() }, { INY() }, { CPA() }, { DEX() }, { AXS() }, { CPY() }, { CPA() }, { DEC() }, { DCP() },
        { BNE() }, { CPA() }, { HLT() }, { DCP() }, { NOP() }, { CPA() }, { DEC() }, { DCP() }, { CLD() }, { CPA() }, { NOP() }, { DCP() }, { NOP() }, { CPA() }, { DEC() }, { DCP() },
        { CPX() }, { SBC() }, { NOP() }, { ISB() }, { CPX() }, { SBC() }, { INC() }, { ISB() }, { INX() }, { SBC() }, { NOP() }, { SBC() }, { CPX() }, { SBC() }, { INC() }, { ISB() },
        { BEQ() }, { SBC() }, { HLT() }, { ISB() }, { NOP() }, { SBC() }, { INC() }, { ISB() }, { SED() }, { SBC() }, { NOP() }, { ISB() }, { NOP() }, { SBC() }, { INC() }, { ISB() },
    )

    private val addrMode: Array<NesAddrMode> = arrayOf(
        NesAddrMode.Imp, NesAddrMode.IndX, NesAddrMode.None, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Acc, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndYW, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsXW, NesAddrMode.AbsXW,
        NesAddrMode.Other, NesAddrMode.IndX, NesAddrMode.None, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Acc, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndYW, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsXW, NesAddrMode.AbsXW,
        NesAddrMode.Imp, NesAddrMode.IndX, NesAddrMode.None, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Acc, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndYW, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsXW, NesAddrMode.AbsXW,
        NesAddrMode.Imp, NesAddrMode.IndX, NesAddrMode.None, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Acc, NesAddrMode.Imm, NesAddrMode.Ind, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndYW, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsXW, NesAddrMode.AbsXW,
        NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndYW, NesAddrMode.None, NesAddrMode.Other, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroY, NesAddrMode.ZeroY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.Imp, NesAddrMode.Other, NesAddrMode.Other, NesAddrMode.AbsXW, NesAddrMode.Other, NesAddrMode.Other,
        NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndY, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroY, NesAddrMode.ZeroY, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsY, NesAddrMode.AbsY,
        NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndYW, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsXW, NesAddrMode.AbsXW,
        NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Imm, NesAddrMode.IndX, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Zero, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Imp, NesAddrMode.Imm, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs, NesAddrMode.Abs,
        NesAddrMode.Rel, NesAddrMode.IndY, NesAddrMode.None, NesAddrMode.IndYW, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.ZeroX, NesAddrMode.Imp, NesAddrMode.AbsY, NesAddrMode.Imp, NesAddrMode.AbsYW, NesAddrMode.AbsX, NesAddrMode.AbsX, NesAddrMode.AbsXW, NesAddrMode.AbsXW,
    )

    private var instAddrMode: NesAddrMode = NesAddrMode.None
    private var needHalt = false
    private var spriteDmaTransfer = false
    private var dmcDmaRunning = false
    private var abortDmcDma = false
    private var needDummyRead = false
    private var spriteDmaOffset = 0
    private var cpuWrite = false
    private var irqMask = 0
    private var prevRunIrq = false
    private var runIrq = false
    private var prevNmiFlag = false
    private var prevNeedNmi = false
    private var needNmi = false
    private var crashed = false
    private var isDmcDmaRead = false

    var state: NesCpuState = NesCpuState()
        private set

    fun getCycleCount(): Long = state.CycleCount
    fun setNmiFlag() { state.NmiFlag = true }
    fun clearNmiFlag() { state.NmiFlag = false }
    fun setIrqMask(mask: Int) { irqMask = mask.u8() }
    fun setIrqSource(source: IRQSource) { state.IrqFlag = state.IrqFlag or source.mask }
    fun hasIrqSource(source: IRQSource): Boolean = (state.IrqFlag and source.mask) != 0
    fun clearIrqSource(source: IRQSource) { state.IrqFlag = state.IrqFlag and source.mask.inv() }
    fun isCpuWrite(): Boolean = cpuWrite
    fun isDmcDma(): Boolean = isDmcDmaRead
    fun getPC(): Int = state.PC
    fun setState(value: NesCpuState) { state = value.copy() }

    fun reset(softReset: Boolean, region: ConsoleRegion = host.region) {
        state.NmiFlag = false
        state.IrqFlag = 0
        spriteDmaTransfer = false
        spriteDmaOffset = 0
        needHalt = false
        dmcDmaRunning = false
        abortDmcDma = false
        isDmcDmaRead = false
        cpuWrite = false
        crashed = false

        state.PC = (host.memoryManager.read(ResetVector) or (host.memoryManager.read(ResetVector + 1) shl 8)).u16()

        if (softReset) {
            setFlags(PSFlags.Interrupt)
            state.SP = (state.SP - 0x03).u8()
        } else {
            irqMask = 0xFF
            state.A = 0
            state.SP = 0xFD
            state.X = 0
            state.Y = 0
            state.PS = PSFlags.Interrupt
            runIrq = false
        }

        val ppuDivider: Int
        val cpuDivider: Int
        when (region) {
            ConsoleRegion.Ntsc -> { ppuDivider = 4; cpuDivider = 12 }
            ConsoleRegion.Pal -> { ppuDivider = 5; cpuDivider = 16 }
            ConsoleRegion.Dendy -> { ppuDivider = 5; cpuDivider = 15 }
        }

        state.CycleCount = -1L
        masterClock = 0
        var cpuOffset = 0
        if (host.randomizeCpuPpuAlignment) {
            ppuOffset = host.randomInt(ppuDivider)
            cpuOffset += host.randomInt(cpuDivider)
        } else {
            ppuOffset = 1
            cpuOffset = 0
        }

        masterClock += (cpuDivider + cpuOffset).toLong()
        repeat(8) {
            startCpuCycle(true)
            endCpuCycle(true)
        }
    }

    fun exec() {
        val opCode = getOPCode()
        instAddrMode = addrMode[opCode]
        operand = fetchOperand()
        opTable[opCode](this)
        if (prevRunIrq || prevNeedNmi) {
            IRQ()
        }
    }

    fun runDMATransfer(offsetValue: Int) {
        spriteDmaTransfer = true
        spriteDmaOffset = offsetValue.u8()
        needHalt = true
    }

    fun startDmcTransfer() {
        dmcDmaRunning = true
        needDummyRead = true
        needHalt = true
    }

    fun stopDmcTransfer() {
        if (dmcDmaRunning) {
            if (needHalt) {
                dmcDmaRunning = false
                needDummyRead = false
                needHalt = false
            } else {
                abortDmcDma = true
            }
        }
    }

    fun setMasterClockDivider(region: ConsoleRegion) {
        when (region) {
            ConsoleRegion.Ntsc -> { startClockCount = 6; endClockCount = 6 }
            ConsoleRegion.Pal -> { startClockCount = 8; endClockCount = 8 }
            ConsoleRegion.Dendy -> { startClockCount = 7; endClockCount = 8 }
        }
    }

    private fun startCpuCycle(forRead: Boolean) {
        masterClock += if (forRead) (startClockCount - 1).toLong() else (startClockCount + 1).toLong()
        host.runPpuUntil(masterClock - ppuOffset)
        state.CycleCount++
        host.processCpuClock()
    }

    private fun endCpuCycle(forRead: Boolean) {
        masterClock += if (forRead) (endClockCount + 1).toLong() else (endClockCount - 1).toLong()
        host.runPpuUntil(masterClock - ppuOffset)
        prevNeedNmi = needNmi
        if (!prevNmiFlag && state.NmiFlag) {
            needNmi = true
        }
        prevNmiFlag = state.NmiFlag
        prevRunIrq = runIrq
        runIrq = ((state.IrqFlag and irqMask) > 0 && !checkFlag(PSFlags.Interrupt))
    }

    private fun memoryWrite(addr: Int, value: Int, operationType: MemoryOperationType = MemoryOperationType.Write) {
        cpuWrite = true
        startCpuCycle(false)
        host.memoryManager.write(addr.u16(), value.u8(), operationType)
        endCpuCycle(false)
        cpuWrite = false
    }

    private fun memoryRead(addr: Int, operationType: MemoryOperationType = MemoryOperationType.Read): Int {
        processPendingDma(addr.u16(), operationType)
        startCpuCycle(true)
        val value = host.memoryManager.read(addr.u16(), operationType).u8()
        endCpuCycle(true)
        return value
    }

    private fun memoryReadWord(addr: Int, operationType: MemoryOperationType = MemoryOperationType.Read): Int {
        val lo = memoryRead(addr, operationType)
        val hi = memoryRead(addr + 1, operationType)
        return (lo or (hi shl 8)).u16()
    }

    private fun getOPCode(): Int {
        val opCode = memoryRead(state.PC, MemoryOperationType.ExecOpCode)
        state.PC = (state.PC + 1).u16()
        return opCode
    }

    private fun dummyPcRead() { memoryRead(state.PC, MemoryOperationType.DummyRead) }
    private fun dummyStackRead() { memoryRead(0x100 + SP(), MemoryOperationType.DummyRead) }
    private fun readByte(): Int { val value = memoryRead(state.PC, MemoryOperationType.ExecOperand); state.PC = (state.PC + 1).u16(); return value }
    private fun readWord(): Int { val low = readByte(); val high = readByte(); return (high shl 8) or low }

    private fun clearFlags(flags: Int) { state.PS = state.PS and flags.inv() and 0xFF }
    private fun setFlags(flags: Int) { state.PS = (state.PS or flags).u8() }
    private fun checkFlag(flag: Int): Boolean = (state.PS and flag) == flag
    private fun setZeroNegativeFlags(value: Int) { if (value.u8() == 0) setFlags(PSFlags.Zero) else if ((value and 0x80) != 0) setFlags(PSFlags.Negative) }
    private fun checkPageCrossed(valA: Int, valB: Int): Boolean = (((valA + valB) and 0xFF00) != (valA and 0xFF00))
    private fun checkPageCrossedSigned(valA: Int, valB: Int): Boolean = (((valA + valB) and 0xFF00) != (valA and 0xFF00))
    private fun setRegister(current: Int, value: Int, setter: (Int) -> Unit) { clearFlags(PSFlags.Zero or PSFlags.Negative); setZeroNegativeFlags(value); setter(value.u8()) }
    private fun push(value: Int) { memoryWrite(SP() + 0x100, value); setSP(SP() - 1) }
    private fun pushWord(value: Int) { push(value shr 8); push(value) }
    private fun pop(): Int { setSP(SP() + 1); return memoryRead(0x100 + SP()) }
    private fun popWord(): Int { val lo = pop(); val hi = pop(); return lo or (hi shl 8) }

    private fun A(): Int = state.A
    private fun setA(value: Int) = setRegister(state.A, value) { state.A = it }
    private fun X(): Int = state.X
    private fun setX(value: Int) = setRegister(state.X, value) { state.X = it }
    private fun Y(): Int = state.Y
    private fun setY(value: Int) = setRegister(state.Y, value) { state.Y = it }
    private fun SP(): Int = state.SP
    private fun setSP(value: Int) { state.SP = value.u8() }
    private fun PS(): Int = state.PS
    private fun setPS(value: Int) { state.PS = value and 0xCF }
    private fun PC(): Int = state.PC
    private fun setPC(value: Int) { state.PC = value.u16() }
    private fun getOperand(): Int = operand
    private fun getOperandValue(): Int = if (instAddrMode >= NesAddrMode.Zero) memoryRead(getOperand()) else getOperand().u8()

    private fun getIndAddr(): Int = readWord()
    private fun getImmediate(): Int = readByte()
    private fun getZeroAddr(): Int = readByte()
    private fun getZeroXAddr(): Int { val value = readByte(); memoryRead(value, MemoryOperationType.DummyRead); return (value + X()).u8() }
    private fun getZeroYAddr(): Int { val value = readByte(); memoryRead(value, MemoryOperationType.DummyRead); return (value + Y()).u8() }
    private fun getAbsAddr(): Int = readWord()
    private fun getAbsXAddr(dummyRead: Boolean = true): Int { val baseAddr = readWord(); val pageCrossed = checkPageCrossed(baseAddr, X()); if (pageCrossed || dummyRead) memoryRead(baseAddr + X() - if (pageCrossed) 0x100 else 0, MemoryOperationType.DummyRead); return (baseAddr + X()).u16() }
    private fun getAbsYAddr(dummyRead: Boolean = true): Int { val baseAddr = readWord(); val pageCrossed = checkPageCrossed(baseAddr, Y()); if (pageCrossed || dummyRead) memoryRead(baseAddr + Y() - if (pageCrossed) 0x100 else 0, MemoryOperationType.DummyRead); return (baseAddr + Y()).u16() }
    private fun getInd(): Int { val addr = getOperand(); return if ((addr and 0xFF) == 0xFF) memoryRead(addr) or (memoryRead(addr - 0xFF) shl 8) else memoryReadWord(addr) }
    private fun getIndXAddr(): Int { var zero = readByte(); memoryRead(zero, MemoryOperationType.DummyRead); zero = (zero + X()).u8(); return if (zero == 0xFF) memoryRead(0xFF) or (memoryRead(0x00) shl 8) else memoryReadWord(zero) }
    private fun getIndYAddr(dummyRead: Boolean = true): Int { val zero = readByte(); val addr = if (zero == 0xFF) memoryRead(0xFF) or (memoryRead(0x00) shl 8) else memoryReadWord(zero); val pageCrossed = checkPageCrossed(addr, Y()); if (pageCrossed || dummyRead) memoryRead(addr + Y() - if (pageCrossed) 0x100 else 0, MemoryOperationType.DummyRead); return (addr + Y()).u16() }

    private fun fetchOperand(): Int = when (instAddrMode) {
        NesAddrMode.Acc, NesAddrMode.Imp -> { dummyPcRead(); 0 }
        NesAddrMode.Imm, NesAddrMode.Rel -> getImmediate()
        NesAddrMode.Zero -> getZeroAddr()
        NesAddrMode.ZeroX -> getZeroXAddr()
        NesAddrMode.ZeroY -> getZeroYAddr()
        NesAddrMode.Ind -> getIndAddr()
        NesAddrMode.IndX -> getIndXAddr()
        NesAddrMode.IndY -> getIndYAddr(false)
        NesAddrMode.IndYW -> getIndYAddr(true)
        NesAddrMode.Abs -> getAbsAddr()
        NesAddrMode.AbsX -> getAbsXAddr(false)
        NesAddrMode.AbsXW -> getAbsXAddr(true)
        NesAddrMode.AbsY -> getAbsYAddr(false)
        NesAddrMode.AbsYW -> getAbsYAddr(true)
        NesAddrMode.Other, NesAddrMode.None -> 0
    }

    private fun AND() { setA(A() and getOperandValue()) }
    private fun EOR() { setA(A() xor getOperandValue()) }
    private fun ORA() { setA(A() or getOperandValue()) }
    private fun ADD(value: Int) { val result = A() + value.u8() + if (checkFlag(PSFlags.Carry)) PSFlags.Carry else 0; clearFlags(PSFlags.Carry or PSFlags.Negative or PSFlags.Overflow or PSFlags.Zero); setZeroNegativeFlags(result); if (((A() xor value).inv() and (A() xor result) and 0x80) != 0) setFlags(PSFlags.Overflow); if (result > 0xFF) setFlags(PSFlags.Carry); setA(result) }
    private fun ADC() { ADD(getOperandValue()) }
    private fun SBC() { ADD(getOperandValue() xor 0xFF) }
    private fun CMP(reg: Int, value: Int) { clearFlags(PSFlags.Carry or PSFlags.Negative or PSFlags.Zero); val result = (reg - value).u8(); if (reg >= value) setFlags(PSFlags.Carry); if (reg == value) setFlags(PSFlags.Zero); if ((result and 0x80) == 0x80) setFlags(PSFlags.Negative) }
    private fun CPA() { CMP(A(), getOperandValue()) }
    private fun CPX() { CMP(X(), getOperandValue()) }
    private fun CPY() { CMP(Y(), getOperandValue()) }
    private fun INC() { val addr = getOperand(); clearFlags(PSFlags.Negative or PSFlags.Zero); var value = memoryRead(addr); memoryWrite(addr, value, MemoryOperationType.DummyWrite); value = (value + 1).u8(); setZeroNegativeFlags(value); memoryWrite(addr, value) }
    private fun DEC() { val addr = getOperand(); clearFlags(PSFlags.Negative or PSFlags.Zero); var value = memoryRead(addr); memoryWrite(addr, value, MemoryOperationType.DummyWrite); value = (value - 1).u8(); setZeroNegativeFlags(value); memoryWrite(addr, value) }
    private fun ASL(value: Int): Int { clearFlags(PSFlags.Carry or PSFlags.Negative or PSFlags.Zero); if ((value and 0x80) != 0) setFlags(PSFlags.Carry); val result = (value shl 1).u8(); setZeroNegativeFlags(result); return result }
    private fun LSR(value: Int): Int { clearFlags(PSFlags.Carry or PSFlags.Negative or PSFlags.Zero); if ((value and 0x01) != 0) setFlags(PSFlags.Carry); val result = (value shr 1).u8(); setZeroNegativeFlags(result); return result }
    private fun ROL(value: Int): Int { val carryFlag = checkFlag(PSFlags.Carry); clearFlags(PSFlags.Carry or PSFlags.Negative or PSFlags.Zero); if ((value and 0x80) != 0) setFlags(PSFlags.Carry); val result = ((value shl 1) or if (carryFlag) 0x01 else 0x00).u8(); setZeroNegativeFlags(result); return result }
    private fun ROR(value: Int): Int { val carryFlag = checkFlag(PSFlags.Carry); clearFlags(PSFlags.Carry or PSFlags.Negative or PSFlags.Zero); if ((value and 0x01) != 0) setFlags(PSFlags.Carry); val result = ((value shr 1) or if (carryFlag) 0x80 else 0x00).u8(); setZeroNegativeFlags(result); return result }
    private fun ASLAddr() { val addr = getOperand(); val value = memoryRead(addr); memoryWrite(addr, value, MemoryOperationType.DummyWrite); memoryWrite(addr, ASL(value)) }
    private fun LSRAddr() { val addr = getOperand(); val value = memoryRead(addr); memoryWrite(addr, value, MemoryOperationType.DummyWrite); memoryWrite(addr, LSR(value)) }
    private fun ROLAddr() { val addr = getOperand(); val value = memoryRead(addr); memoryWrite(addr, value, MemoryOperationType.DummyWrite); memoryWrite(addr, ROL(value)) }
    private fun RORAddr() { val addr = getOperand(); val value = memoryRead(addr); memoryWrite(addr, value, MemoryOperationType.DummyWrite); memoryWrite(addr, ROR(value)) }
    private fun JMP(addr: Int) { setPC(addr) }
    private fun branchRelative(branch: Boolean) { val offset = getOperand().toByte().toInt(); if (branch) { runIrq = prevRunIrq; dummyPcRead(); if (checkPageCrossedSigned(PC(), offset)) { runIrq = runIrq or prevRunIrq; memoryRead((PC() and 0xFF00) or ((PC() + offset) and 0xFF), MemoryOperationType.DummyRead) }; setPC(PC() + offset) } }
    private fun BIT() { val value = getOperandValue(); clearFlags(PSFlags.Zero or PSFlags.Overflow or PSFlags.Negative); if ((A() and value) == 0) setFlags(PSFlags.Zero); if ((value and 0x40) != 0) setFlags(PSFlags.Overflow); if ((value and 0x80) != 0) setFlags(PSFlags.Negative) }

    private fun LDA() { setA(getOperandValue()) }
    private fun LDX() { setX(getOperandValue()) }
    private fun LDY() { setY(getOperandValue()) }
    private fun STA() { memoryWrite(getOperand(), A()) }
    private fun STX() { memoryWrite(getOperand(), X()) }
    private fun STY() { memoryWrite(getOperand(), Y()) }
    private fun TAX() { setX(A()) }
    private fun TAY() { setY(A()) }
    private fun TSX() { setX(SP()) }
    private fun TXA() { setA(X()) }
    private fun TXS() { setSP(X()) }
    private fun TYA() { setA(Y()) }
    private fun PHA() { push(A()) }
    private fun PHP() { push(PS() or PSFlags.Break or PSFlags.Reserved) }
    private fun PLA() { dummyStackRead(); setA(pop()) }
    private fun PLP() { dummyStackRead(); setPS(pop()) }
    private fun INX() { setX(X() + 1) }
    private fun INY() { setY(Y() + 1) }
    private fun DEX() { setX(X() - 1) }
    private fun DEY() { setY(Y() - 1) }
    private fun ASL_Acc() { setA(ASL(A())) }
    private fun ASL_Memory() { ASLAddr() }
    private fun LSR_Acc() { setA(LSR(A())) }
    private fun LSR_Memory() { LSRAddr() }
    private fun ROL_Acc() { setA(ROL(A())) }
    private fun ROL_Memory() { ROLAddr() }
    private fun ROR_Acc() { setA(ROR(A())) }
    private fun ROR_Memory() { RORAddr() }
    private fun JMP_Abs() { JMP(getOperand()) }
    private fun JMP_Ind() { JMP(getInd()) }
    private fun JSR() { val lo = readByte(); dummyStackRead(); pushWord(PC()); val addr = (readByte() shl 8) or lo; JMP(addr) }
    private fun RTS() { dummyStackRead(); val addr = popWord(); setPC(addr); dummyPcRead(); setPC(addr + 1) }
    private fun BCC() { branchRelative(!checkFlag(PSFlags.Carry)) }
    private fun BCS() { branchRelative(checkFlag(PSFlags.Carry)) }
    private fun BEQ() { branchRelative(checkFlag(PSFlags.Zero)) }
    private fun BMI() { branchRelative(checkFlag(PSFlags.Negative)) }
    private fun BNE() { branchRelative(!checkFlag(PSFlags.Zero)) }
    private fun BPL() { branchRelative(!checkFlag(PSFlags.Negative)) }
    private fun BVC() { branchRelative(!checkFlag(PSFlags.Overflow)) }
    private fun BVS() { branchRelative(checkFlag(PSFlags.Overflow)) }
    private fun CLC() { clearFlags(PSFlags.Carry) }
    private fun CLD() { clearFlags(PSFlags.Decimal) }
    private fun CLI() { clearFlags(PSFlags.Interrupt) }
    private fun CLV() { clearFlags(PSFlags.Overflow) }
    private fun SEC() { setFlags(PSFlags.Carry) }
    private fun SED() { setFlags(PSFlags.Decimal) }
    private fun SEI() { setFlags(PSFlags.Interrupt) }
    private fun RTI() { dummyStackRead(); setPS(pop()); setPC(popWord()) }
    private fun NOP() { getOperandValue() }

    private fun IRQ() { val originalPc = PC(); if (host.region == ConsoleRegion.Pal) processPendingDma(state.PC, MemoryOperationType.ExecOpCode); dummyPcRead(); dummyPcRead(); pushWord(PC()); if (needNmi) { needNmi = false; push(PS() or PSFlags.Reserved); setFlags(PSFlags.Interrupt); setPC(memoryReadWord(NMIVector)) } else { push(PS() or PSFlags.Reserved); setFlags(PSFlags.Interrupt); setPC(memoryReadWord(IRQVector)) }; @Suppress("UNUSED_VARIABLE") val ignored = originalPc }
    private fun BRK() { pushWord(PC() + 1); val flags = PS() or PSFlags.Break or PSFlags.Reserved; if (needNmi) { needNmi = false; push(flags); setFlags(PSFlags.Interrupt); setPC(memoryReadWord(NMIVector)) } else { push(flags); setFlags(PSFlags.Interrupt); setPC(memoryReadWord(IRQVector)) }; prevNeedNmi = false }

    private fun SLO() { val value = getOperandValue(); memoryWrite(getOperand(), value, MemoryOperationType.DummyWrite); val shiftedValue = ASL(value); setA(A() or shiftedValue); memoryWrite(getOperand(), shiftedValue) }
    private fun SRE() { val value = getOperandValue(); memoryWrite(getOperand(), value, MemoryOperationType.DummyWrite); val shiftedValue = LSR(value); setA(A() xor shiftedValue); memoryWrite(getOperand(), shiftedValue) }
    private fun RLA() { val value = getOperandValue(); memoryWrite(getOperand(), value, MemoryOperationType.DummyWrite); val shiftedValue = ROL(value); setA(A() and shiftedValue); memoryWrite(getOperand(), shiftedValue) }
    private fun RRA() { val value = getOperandValue(); memoryWrite(getOperand(), value, MemoryOperationType.DummyWrite); val shiftedValue = ROR(value); ADD(shiftedValue); memoryWrite(getOperand(), shiftedValue) }
    private fun SAX() { memoryWrite(getOperand(), A() and X()) }
    private fun LAX() { val value = getOperandValue(); setX(value); setA(value) }
    private fun DCP() { var value = getOperandValue(); memoryWrite(getOperand(), value, MemoryOperationType.DummyWrite); value = (value - 1).u8(); CMP(A(), value); memoryWrite(getOperand(), value) }
    private fun ISB() { var value = getOperandValue(); memoryWrite(getOperand(), value, MemoryOperationType.DummyWrite); value = (value + 1).u8(); ADD(value xor 0xFF); memoryWrite(getOperand(), value) }
    private fun AAC() { setA(A() and getOperandValue()); clearFlags(PSFlags.Carry); if (checkFlag(PSFlags.Negative)) setFlags(PSFlags.Carry) }
    private fun ASR() { clearFlags(PSFlags.Carry); setA(A() and getOperandValue()); if ((A() and 0x01) != 0) setFlags(PSFlags.Carry); setA(A() shr 1) }
    private fun ARR() { setA(((A() and getOperandValue()) shr 1) or if (checkFlag(PSFlags.Carry)) 0x80 else 0x00); clearFlags(PSFlags.Carry or PSFlags.Overflow); if ((A() and 0x40) != 0) setFlags(PSFlags.Carry); if (((if (checkFlag(PSFlags.Carry)) 0x01 else 0x00) xor ((A() shr 5) and 0x01)) != 0) setFlags(PSFlags.Overflow) }
    private fun ATX() { val value = getOperandValue(); setA(value); setX(A()); setA(A()) }
    private fun AXS() { val opValue = getOperandValue(); val value = ((A() and X()) - opValue).u8(); clearFlags(PSFlags.Carry); if ((A() and X()) >= opValue) setFlags(PSFlags.Carry); setX(value) }
    private fun syaSxaAxa(baseAddr: Int, indexReg: Int, valueReg: Int) { val pageCrossed = checkPageCrossed(baseAddr, indexReg); val cyc = state.CycleCount; memoryRead(baseAddr + indexReg - if (pageCrossed) 0x100 else 0, MemoryOperationType.DummyRead); val hadDma = state.CycleCount - cyc > 1; val op = baseAddr + indexReg; var addrHigh = op shr 8; val addrLow = op and 0xFF; if (pageCrossed) addrHigh = addrHigh and valueReg; val value = if (hadDma) valueReg else valueReg and ((baseAddr shr 8) + 1); memoryWrite((addrHigh shl 8) or addrLow, value) }
    private fun SHY() { syaSxaAxa(readWord(), X(), Y()) }
    private fun SHX() { syaSxaAxa(readWord(), Y(), X()) }
    private fun SHAA() { syaSxaAxa(readWord(), Y(), X() and A()) }
    private fun SHAZ() { val zero = readByte(); val baseAddr = if (zero == 0xFF) memoryRead(0xFF) or (memoryRead(0x00) shl 8) else memoryReadWord(zero); syaSxaAxa(baseAddr, Y(), X() and A()) }
    private fun TAS() { SHAA(); setSP(X() and A()) }
    private fun HLT() { state.PC = (state.PC - 1).u16(); prevRunIrq = false; prevNeedNmi = false; if (!crashed) { crashed = true; host.onCpuCrash() } }
    private fun ANE() { val imm = getOperandValue(); setA((A() or 0xEE) and X() and imm) }
    private fun LAS() { val value = getOperandValue(); setA(value and SP()); setX(A()); setSP(A()) }

    private fun processPendingDma(readAddress: Int, opType: MemoryOperationType) {
        if (!needHalt) return
        if (host.region == ConsoleRegion.Pal && opType != MemoryOperationType.ExecOpCode) return

        var prevReadAddress = readAddress
        val enableInternalRegReads = (readAddress and 0xFFE0) == 0x4000
        needHalt = false

        startCpuCycle(true)
        host.memoryManager.read(readAddress, MemoryOperationType.DmaRead)
        endCpuCycle(true)

        if (abortDmcDma) {
            dmcDmaRunning = false
            abortDmcDma = false
            if (!spriteDmaTransfer) {
                needDummyRead = false
                return
            }
        }

        var spriteDmaCounter = 0
        var spriteReadAddr = 0
        var readValue = 0

        fun processCycle() {
            if (abortDmcDma) {
                dmcDmaRunning = false
                abortDmcDma = false
                needDummyRead = false
                needHalt = false
            } else if (needHalt) {
                needHalt = false
            } else if (needDummyRead) {
                needDummyRead = false
            }
            startCpuCycle(true)
        }

        while (dmcDmaRunning || spriteDmaTransfer) {
            val getCycle = (state.CycleCount and 0x01L) == 0L
            if (getCycle) {
                if (dmcDmaRunning && !needHalt && !needDummyRead) {
                    processCycle()
                    isDmcDmaRead = true
                    val result = processDmaRead(host.apu.getDmcReadAddress(), prevReadAddress, enableInternalRegReads)
                    readValue = result.first
                    prevReadAddress = result.second
                    isDmcDmaRead = false
                    endCpuCycle(true)
                    dmcDmaRunning = false
                    abortDmcDma = false
                    host.apu.setDmcReadBuffer(readValue)
                    if (needHalt) processPendingDma(readAddress, opType)
                } else if (spriteDmaTransfer) {
                    processCycle()
                    val result = processDmaRead(spriteDmaOffset * 0x100 + spriteReadAddr, prevReadAddress, enableInternalRegReads)
                    readValue = result.first
                    prevReadAddress = result.second
                    endCpuCycle(true)
                    spriteReadAddr = (spriteReadAddr + 1).u8()
                    spriteDmaCounter++
                } else {
                    processCycle()
                    host.memoryManager.read(readAddress, MemoryOperationType.DmaRead)
                    endCpuCycle(true)
                }
            } else {
                if (spriteDmaTransfer && (spriteDmaCounter and 0x01) != 0) {
                    processCycle()
                    host.memoryManager.write(0x2004, readValue, MemoryOperationType.DmaWrite)
                    endCpuCycle(true)
                    spriteDmaCounter++
                    if (spriteDmaCounter == 0x200) spriteDmaTransfer = false
                } else {
                    processCycle()
                    host.memoryManager.read(readAddress, MemoryOperationType.DmaRead)
                    endCpuCycle(true)
                }
            }
        }
    }

    private fun processDmaRead(addr: Int, prevReadAddress: Int, enableInternalRegReads: Boolean): Pair<Int, Int> {
        var valRead: Int
        if (!enableInternalRegReads) {
            valRead = if (addr in 0x4015..0x401A) host.memoryManager.getOpenBus() else host.memoryManager.read(addr, MemoryOperationType.DmaRead)
            return valRead.u8() to addr.u16()
        } else {
            val internalAddr = 0x4000 or (addr and 0x1F)
            val isSameAddress = internalAddr == addr
            when (internalAddr) {
                0x4015 -> {
                    valRead = host.memoryManager.read(internalAddr, MemoryOperationType.DmaRead, NesCpuBusType.Internal)
                    if (!isSameAddress) host.memoryManager.read(addr, MemoryOperationType.DmaRead, NesCpuBusType.External)
                }
                0x4016, 0x4017 -> {
                    valRead = host.memoryManager.read(internalAddr, MemoryOperationType.DmaRead)
                    if (!isSameAddress) {
                        val obMask = host.getOpenBusMask(internalAddr - 0x4016)
                        val externalValue = host.memoryManager.read(addr, MemoryOperationType.DmaRead)
                        host.memoryManager.setOpenBus((externalValue and obMask) or (valRead and obMask.inv()), NesCpuBusType.External)
                        valRead = (externalValue and obMask) or ((valRead and obMask.inv()) and (externalValue and obMask.inv()))
                    }
                }
                else -> valRead = host.memoryManager.read(addr, MemoryOperationType.DmaRead)
            }
            @Suppress("UNUSED_VARIABLE") val ignored = prevReadAddress
            return valRead.u8() to internalAddr.u16()
        }
    }
}
