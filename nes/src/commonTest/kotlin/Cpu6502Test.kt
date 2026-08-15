import nes.cpu.Cpu6502
import nes.cpu.CpuBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Cpu6502Test {
    private fun program(vararg bytes: Number): ByteArray {
        return bytes.map { it.toByte() }.toByteArray()
    }

    @Test
    fun `reset reads program counter from reset vector`() {
        val resetVectorProgram = program(Cpu6502.OP_NOP)
        val (cpu, _, _) = cpuWithProgram(resetVectorProgram, 0x8123)

        assertEquals(0x8123, cpu.state.pc, "Program counter matches reset vector")
    }

    @Test
    fun `load and store opcodes move accumulator into zero page memory`() {
        val loadStoreProgram = program(
                Cpu6502.OP_LDA_IMM,
                0x44,
                Cpu6502.OP_STA_ZP,
                0x10
        )
        val (cpu, bus, _) = cpuWithProgram(loadStoreProgram)

        cpu.step()
        cpu.step()

        assertEquals(0x44, bus.read(0x10), "Accumulator value is stored in zero page")
    }

    @Test
    fun `adc immediate sets accumulator and overflow flag`() {
        val adcOverflowProgram = program(
                Cpu6502.OP_LDA_IMM,
                0x50,
                Cpu6502.OP_ADC_IMM,
                0x50
        )
        val (cpu, _, _) = cpuWithProgram(adcOverflowProgram)

        cpu.step()
        cpu.step()

        assertEquals(0xA0, cpu.state.a, "Accumulator contains ADC result")
        assertTrue((cpu.state.status and Cpu6502.V) != 0, "Overflow flag is set")
    }

    @Test
    fun `cpx immediate sets carry and zero flags when values match`() {
        val cpxEqualProgram = program(
                Cpu6502.OP_LDX_IMM,
                3,
                Cpu6502.OP_CPX_IMM,
                3
        )
        val (cpu, _, _) = cpuWithProgram(cpxEqualProgram)

        cpu.step()
        cpu.step()

        assertTrue((cpu.state.status and Cpu6502.C) != 0, "Carry flag is set for equal comparison")
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "Zero flag is set for equal comparison")
    }

    @Test
    fun `beq skips instructions when zero flag is set`() {
        val branchWhenZeroProgram = program(
                Cpu6502.OP_LDA_IMM,
                0,
                Cpu6502.OP_BEQ,
                2,
                Cpu6502.OP_LDA_IMM,
                1,
                Cpu6502.OP_LDA_IMM,
                2
        )
        val (cpu, _, _) = cpuWithProgram(branchWhenZeroProgram)

        repeat(4) { cpu.step() }

        assertEquals(2, cpu.state.a, "Branch skips the first replacement accumulator value")
    }

    @Test
    fun `pha and pla restore accumulator through the stack`() {
        val accumulatorStackProgram = program(
                Cpu6502.OP_LDA_IMM,
                0x7F,
                Cpu6502.OP_PHA,
                Cpu6502.OP_LDA_IMM,
                0,
                Cpu6502.OP_PLA
        )
        val (cpu, _, _) = cpuWithProgram(accumulatorStackProgram)

        repeat(4) { cpu.step() }

        assertEquals(0x7F, cpu.state.a, "Accumulator is restored from the stack")
    }

    @Test
    fun `jsr and rts execute subroutine then return`() {
        val subroutineProgram = program(
                Cpu6502.OP_JSR_ABS,
                0x06,
                0x80,
                Cpu6502.OP_LDA_IMM,
                2,
                Cpu6502.OP_NOP,
                Cpu6502.OP_LDA_IMM,
                1,
                Cpu6502.OP_RTS
        )
        val (cpu, _, _) = cpuWithProgram(subroutineProgram)

        repeat(4) { cpu.step() }

        assertEquals(2, cpu.state.a, "Execution resumes after JSR once RTS returns")
    }

    @Test
    fun `brk jumps to irq vector and rti returns`() {
        val breakProgram = program(Cpu6502.OP_BRK, Cpu6502.OP_NOP)
        val (cpu, bus, _) = cpuWithProgram(breakProgram)
        bus.write(0x9100, Cpu6502.OP_RTI)

        cpu.step()
        assertEquals(0x9100, cpu.state.pc, "BRK loads the IRQ vector")

        cpu.step()
        assertEquals(0x8002, cpu.state.pc, "RTI restores the interrupted program counter")
    }

    @Test
    fun `nmi request jumps to nmi vector before next opcode`() {
        val nmiProgram = program(Cpu6502.OP_NOP)
        val (cpu, bus, _) = cpuWithProgram(nmiProgram)
        bus.write(0x9000, Cpu6502.OP_NOP)
        val cyclesBefore = cpu.state.totalCycles

        cpu.requestNmi()
        val cycles = cpu.step()

        assertEquals(0x9000, cpu.state.pc, "NMI vector is loaded")
        assertEquals(7, cycles)
        assertEquals(cyclesBefore + cycles, cpu.state.totalCycles)
    }

    @Test
    fun `deasserted IRQ line does not leave a stale interrupt`() {
        val (cpu, _, _) = cpuWithProgram(program(Cpu6502.OP_CLI, Cpu6502.OP_NOP))
        cpu.step()

        cpu.setIrqLine(true)
        cpu.setIrqLine(false)
        val cycles = cpu.step()

        assertEquals(0x8002, cpu.state.pc)
        assertEquals(2, cycles)
    }

    @Test
    fun `absolute x load adds a cycle when page boundary is crossed`() {
        val pageCrossingProgram = program(
                Cpu6502.OP_LDX_IMM,
                1,
                Cpu6502.OP_LDA_ABSX,
                0xFF,
                0x80
        )
        val (cpu, _, _) = cpuWithProgram(pageCrossingProgram)

        cpu.step()
        val cycles = cpu.step()

        assertEquals(5, cycles, "Page crossing adds one cycle")
    }

    @Test
    fun `zero page x addressing wraps within zero page`() {
        val zeroPageWrapProgram = program(Cpu6502.OP_LDX_IMM, 1, Cpu6502.OP_LDA_ZPX, 0xFF)
        val (cpu, bus, _) = cpuWithProgram(zeroPageWrapProgram)
        bus.write(0, 0x33)

        cpu.step()
        cpu.step()

        assertEquals(0x33, cpu.state.a, "Zero page indexed address wraps to address zero")
    }

    @Test
    fun `indirect jmp emulates 6502 page wraparound bug`() {
        val indirectJumpProgram = program(Cpu6502.OP_JMP_IND, 0xFF, 0x02)
        val (cpu, bus, _) = cpuWithProgram(indirectJumpProgram)
        bus.write(0x02FF, 0x34)
        bus.write(0x0200, 0x12)

        cpu.step()

        assertEquals(0x1234, cpu.state.pc, "Indirect JMP high byte wraps within the same page")
    }

    @Test
    fun `load accumulator updates zero and negative status flags`() {
        val zeroFlagProgram = program(
                Cpu6502.OP_LDA_IMM,
                0
        )
        val (cpu, _, _) = cpuWithProgram(zeroFlagProgram)

        cpu.step()

        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "Zero flag is set for zero value")
        assertFalse((cpu.state.status and Cpu6502.N) != 0, "Negative flag is clear for zero value")
    }

    @Test
    fun `lda zero page x loads accumulator from indexed zero page address`() {
        val ldaZeroPageXProgram = program(Cpu6502.OP_LDX_IMM, 0x04, Cpu6502.OP_LDA_ZPX, 0x10)
        val (cpu, bus, _) = cpuWithProgram(ldaZeroPageXProgram)
        bus.write(0x0014, 0x33)

        cpu.step()
        cpu.step()

        assertEquals(0x33, cpu.state.a, "LDA zero page,X loads from operand plus X")
    }

    @Test
    fun `lda absolute x loads accumulator from indexed absolute address`() {
        val ldaAbsoluteXProgram = program(Cpu6502.OP_LDX_IMM, 0x04, Cpu6502.OP_LDA_ABSX, 0x00, 0x02)
        val (cpu, bus, _) = cpuWithProgram(ldaAbsoluteXProgram)
        bus.write(0x0204, 0x55)

        cpu.step()
        cpu.step()

        assertEquals(0x55, cpu.state.a, "LDA absolute,X loads from operand plus X")
    }

    @Test
    fun `lda absolute y loads accumulator from indexed absolute address`() {
        val ldaAbsoluteYProgram = program(Cpu6502.OP_LDY_IMM, 0x05, Cpu6502.OP_LDA_ABSY, 0x00, 0x02)
        val (cpu, bus, _) = cpuWithProgram(ldaAbsoluteYProgram)
        bus.write(0x0205, 0x66)

        cpu.step()
        cpu.step()

        assertEquals(0x66, cpu.state.a, "LDA absolute,Y loads from operand plus Y")
    }

    @Test
    fun `lda indexed indirect loads accumulator through zero page pointer plus x`() {
        val ldaIndexedIndirectProgram = program(Cpu6502.OP_LDX_IMM, 0x04, Cpu6502.OP_LDA_INDX, 0x30)
        val (cpu, bus, _) = cpuWithProgram(ldaIndexedIndirectProgram)
        bus.write(0x0034, 0x50)
        bus.write(0x0035, 0x02)
        bus.write(0x0250, 0x77)

        cpu.step()
        cpu.step()

        assertEquals(0x77, cpu.state.a, "LDA indexed-indirect follows the zero page pointer selected by X")
    }

    @Test
    fun `lda indirect indexed loads accumulator through zero page pointer plus y`() {
        val ldaIndirectIndexedProgram = program(Cpu6502.OP_LDY_IMM, 0x05, Cpu6502.OP_LDA_INDY, 0x40)
        val (cpu, bus, _) = cpuWithProgram(ldaIndirectIndexedProgram)
        bus.write(0x0040, 0x60)
        bus.write(0x0041, 0x02)
        bus.write(0x0265, 0x88)

        cpu.step()
        cpu.step()

        assertEquals(0x88, cpu.state.a, "LDA indirect-indexed follows the zero page pointer and applies Y")
    }

    @Test
    fun `ldx zero page y loads x from indexed zero page address`() {
        val ldxZeroPageYProgram = program(Cpu6502.OP_LDY_IMM, 0x03, Cpu6502.OP_LDX_ZPY, 0x10)
        val (cpu, bus, _) = cpuWithProgram(ldxZeroPageYProgram)
        bus.write(0x0013, 0x44)

        cpu.step()
        cpu.step()

        assertEquals(0x44, cpu.state.x, "LDX zero page,Y loads from operand plus Y")
    }

    @Test
    fun `ldy zero page x loads y from indexed zero page address`() {
        val ldyZeroPageXProgram = program(Cpu6502.OP_LDX_IMM, 0x04, Cpu6502.OP_LDY_ZPX, 0x10)
        val (cpu, bus, _) = cpuWithProgram(ldyZeroPageXProgram)
        bus.write(0x0014, 0x88)

        cpu.step()
        cpu.step()

        assertEquals(0x88, cpu.state.y, "LDY zero page,X loads from operand plus X")
    }

    @Test
    fun `sta indirect indexed stores accumulator through zero page pointer plus y`() {
        val staIndirectIndexedProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x44,
            Cpu6502.OP_LDY_IMM,
            0x05,
            Cpu6502.OP_STA_INDY,
            0x40
        )
        val (cpu, bus, _) = cpuWithProgram(staIndirectIndexedProgram)
        bus.write(0x0040, 0x80)
        bus.write(0x0041, 0x02)

        repeat(3) { cpu.step() }

        assertEquals(0x44, bus.read(0x0285), "STA indirect-indexed stores at pointer plus Y")
    }

    @Test
    fun `stx and sty indexed zero page opcodes store index registers`() {
        val storeIndexRegistersProgram = program(
            Cpu6502.OP_LDX_IMM,
            0x04,
            Cpu6502.OP_LDY_IMM,
            0x05,
            Cpu6502.OP_STX_ZPY,
            0x50,
            Cpu6502.OP_STY_ZPX,
            0x60
        )
        val (cpu, bus, _) = cpuWithProgram(storeIndexRegistersProgram)

        repeat(4) { cpu.step() }

        assertEquals(0x04, bus.read(0x0055), "STX zero page,Y stores X at operand plus Y")
        assertEquals(0x05, bus.read(0x0064), "STY zero page,X stores Y at operand plus X")
    }

    @Test
    fun `transfer opcodes copy values between registers and stack pointer`() {
        val transferRegistersProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x12,
            Cpu6502.OP_TAX,
            Cpu6502.OP_TAY,
            Cpu6502.OP_LDA_IMM,
            0x00,
            Cpu6502.OP_TXA,
            Cpu6502.OP_TYA,
            Cpu6502.OP_LDX_IMM,
            0xF0.toByte(),
            Cpu6502.OP_TXS,
            Cpu6502.OP_TSX
        )
        val (cpu, _, _) = cpuWithProgram(transferRegistersProgram)

        cpu.step()
        cpu.step()
        assertEquals(0x12, cpu.state.x, "TAX copies the accumulator to X")

        cpu.step()
        assertEquals(0x12, cpu.state.y, "TAY copies the accumulator to Y")

        cpu.step()
        cpu.step()
        assertEquals(0x12, cpu.state.a, "TXA copies X to the accumulator")

        cpu.step()
        assertEquals(0x12, cpu.state.a, "TYA copies Y to the accumulator")

        cpu.step()
        cpu.step()
        assertEquals(0xF0, cpu.state.sp, "TXS copies X to the stack pointer")

        cpu.step()
        assertEquals(0xF0, cpu.state.x, "TSX copies the stack pointer to X")
    }

    @Test
    fun `stack opcodes push and pull accumulator and status`() {
        val stackProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x7F,
            Cpu6502.OP_PHA,
            Cpu6502.OP_LDA_IMM,
            0x00,
            Cpu6502.OP_PLA,
            Cpu6502.OP_SEC,
            Cpu6502.OP_PHP,
            Cpu6502.OP_CLC,
            Cpu6502.OP_PLP
        )
        val (cpu, _, _) = cpuWithProgram(stackProgram)

        repeat(6) { cpu.step() }
        assertEquals(0x7F, cpu.state.a, "PHA and PLA restore the accumulator")

        repeat(4) { cpu.step() }
        assertTrue((cpu.state.status and Cpu6502.C) != 0, "PHP and PLP restore the carry flag")
    }

    @Test
    fun `adc zero page adds memory and carry to accumulator`() {
        val adcZeroPageProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x10, Cpu6502.OP_ADC_ZP, 0x20)
        val (cpu, bus, _) = cpuWithProgram(adcZeroPageProgram)
        bus.write(0x0020, 0x0F)

        repeat(3) { cpu.step() }

        assertEquals(0x20, cpu.state.a, "ADC zero page adds memory and incoming carry")
        assertFalse((cpu.state.status and Cpu6502.C) != 0, "ADC clears carry when result fits in a byte")
    }

    @Test
    fun `adc absolute indexed addressing reads from indexed memory`() {
        val adcAbsoluteIndexedProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x10,
            Cpu6502.OP_LDX_IMM,
            0x04,
            Cpu6502.OP_ADC_ABSX,
            0x00,
            0x02
        )
        val (cpu, bus, _) = cpuWithProgram(adcAbsoluteIndexedProgram)
        bus.write(0x0204, 0x05)

        repeat(3) { cpu.step() }

        assertEquals(0x15, cpu.state.a, "ADC absolute,X reads from base address plus X")
    }

    @Test
    fun `sbc immediate subtracts from accumulator using carry`() {
        val sbcImmediateProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_SBC_IMM, 0x10)
        val (cpu, _, _) = cpuWithProgram(sbcImmediateProgram)

        repeat(3) { cpu.step() }

        assertEquals(0x20, cpu.state.a, "SBC immediate subtracts operand from accumulator")
        assertTrue((cpu.state.status and Cpu6502.C) != 0, "SBC keeps carry set when no borrow occurs")
    }

    @Test
    fun `unofficial sbc immediate behaves like sbc immediate`() {
        val unofficialSbcImmediateProgram = program(
            Cpu6502.OP_SEC,
            Cpu6502.OP_LDA_IMM,
            0x30,
            Cpu6502.OP_SBC_IMM_UNOFFICIAL,
            0x10
        )
        val (cpu, _, _) = cpuWithProgram(unofficialSbcImmediateProgram)

        repeat(3) { cpu.step() }

        assertEquals(0x20, cpu.state.a, "Unofficial SBC immediate subtracts operand from accumulator")
    }

    @Test
    fun `and immediate masks accumulator and updates zero flag`() {
        val andImmediateProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_AND_IMM, 0x0F)
        val (cpu, _, _) = cpuWithProgram(andImmediateProgram)

        cpu.step()
        cpu.step()

        assertEquals(0x00, cpu.state.a, "AND immediate masks the accumulator")
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "AND immediate updates the zero flag")
    }

    @Test
    fun `ora zero page sets accumulator bits from memory`() {
        val oraZeroPageProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_ORA_ZP, 0x10)
        val (cpu, bus, _) = cpuWithProgram(oraZeroPageProgram)
        bus.write(0x0010, 0x80)

        cpu.step()
        cpu.step()

        assertEquals(0xA0, cpu.state.a, "ORA zero page sets accumulator bits from memory")
    }

    @Test
    fun `eor zero page toggles accumulator bits from memory`() {
        val eorZeroPageProgram = program(Cpu6502.OP_LDA_IMM, 0xA0, Cpu6502.OP_EOR_ZP, 0x10)
        val (cpu, bus, _) = cpuWithProgram(eorZeroPageProgram)
        bus.write(0x0010, 0xFF)

        cpu.step()
        cpu.step()

        assertEquals(0x5F, cpu.state.a, "EOR zero page toggles accumulator bits from memory")
    }

    @Test
    fun `compare opcodes update carry zero and negative flags`() {
        val compareProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x20,
            Cpu6502.OP_CMP_IMM,
            0x20,
            Cpu6502.OP_CMP_ZP,
            0x10,
            Cpu6502.OP_LDX_IMM,
            0x30,
            Cpu6502.OP_CPX_IMM,
            0x20,
            Cpu6502.OP_CPX_ZP,
            0x11,
            Cpu6502.OP_LDY_IMM,
            0x10,
            Cpu6502.OP_CPY_IMM,
            0x20,
            Cpu6502.OP_CPY_ZP,
            0x12
        )
        val (cpu, bus, _) = cpuWithProgram(compareProgram)
        bus.write(0x0010, 0x30)
        bus.write(0x0011, 0x30)
        bus.write(0x0012, 0x10)

        cpu.step()
        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "CMP immediate sets zero when values match")

        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.N) != 0, "CMP zero page sets negative when register is smaller")

        cpu.step()
        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.C) != 0, "CPX immediate sets carry when register is greater")

        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "CPX zero page sets zero when values match")

        cpu.step()
        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.N) != 0, "CPY immediate sets negative when register is smaller")

        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "CPY zero page sets zero when values match")
    }

    @Test
    fun `inc zero page increments memory and updates negative flag`() {
        val incZeroPageProgram = program(Cpu6502.OP_INC_ZP, 0x10)
        val (cpu, bus, _) = cpuWithProgram(incZeroPageProgram)
        bus.write(0x0010, 0x7F)

        cpu.step()

        assertEquals(0x80, bus.read(0x0010), "INC zero page increments memory")
        assertTrue((cpu.state.status and Cpu6502.N) != 0, "INC zero page updates the negative flag")
    }

    @Test
    fun `dec zero page decrements memory`() {
        val decZeroPageProgram = program(Cpu6502.OP_DEC_ZP, 0x10)
        val (cpu, bus, _) = cpuWithProgram(decZeroPageProgram)
        bus.write(0x0010, 0x80)

        cpu.step()

        assertEquals(0x7F, bus.read(0x0010), "DEC zero page decrements memory")
    }

    @Test
    fun `inx wraps x register and updates zero flag`() {
        val inxWrapProgram = program(Cpu6502.OP_LDX_IMM, 0xFF, Cpu6502.OP_INX)
        val (cpu, _, _) = cpuWithProgram(inxWrapProgram)

        cpu.step()
        cpu.step()

        assertEquals(0x00, cpu.state.x, "INX increments X with 8-bit wraparound")
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "INX updates the zero flag")
    }

    @Test
    fun `dey wraps y register and updates negative flag`() {
        val deyWrapProgram = program(Cpu6502.OP_LDY_IMM, 0x00, Cpu6502.OP_DEY)
        val (cpu, _, _) = cpuWithProgram(deyWrapProgram)

        cpu.step()
        cpu.step()

        assertEquals(0xFF, cpu.state.y, "DEY decrements Y with 8-bit wraparound")
        assertTrue((cpu.state.status and Cpu6502.N) != 0, "DEY updates the negative flag")
    }

    @Test
    fun `asl accumulator shifts left and moves bit seven into carry`() {
        val aslAccumulatorProgram = program(Cpu6502.OP_LDA_IMM, 0x81, Cpu6502.OP_ASL_ACC)
        val (cpu, _, _) = cpuWithProgram(aslAccumulatorProgram)

        cpu.step()
        cpu.step()

        assertEquals(0x02, cpu.state.a, "ASL accumulator shifts the accumulator left")
        assertTrue((cpu.state.status and Cpu6502.C) != 0, "ASL accumulator moves bit seven into carry")
    }

    @Test
    fun `lsr zero page shifts memory right and moves bit zero into carry`() {
        val lsrZeroPageProgram = program(Cpu6502.OP_LSR_ZP, 0x10)
        val (cpu, bus, _) = cpuWithProgram(lsrZeroPageProgram)
        bus.write(0x0010, 0x01)

        cpu.step()

        assertEquals(0x00, bus.read(0x0010), "LSR zero page writes the shifted value to memory")
        assertTrue((cpu.state.status and Cpu6502.C) != 0, "LSR zero page moves bit zero into carry")
    }

    @Test
    fun `rol zero page rotates memory left through carry`() {
        val rolZeroPageProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_ROL_ZP, 0x10)
        val (cpu, bus, _) = cpuWithProgram(rolZeroPageProgram)
        bus.write(0x0010, 0x40)

        cpu.step()
        cpu.step()

        assertEquals(0x81, bus.read(0x0010), "ROL zero page writes the rotated value to memory")
    }

    @Test
    fun `ror accumulator rotates right through carry`() {
        val rorAccumulatorProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x02, Cpu6502.OP_ROR_ACC)
        val (cpu, _, _) = cpuWithProgram(rorAccumulatorProgram)

        repeat(3) { cpu.step() }

        assertEquals(0x81, cpu.state.a, "ROR accumulator rotates the accumulator right through carry")
    }

    @Test
    fun `bit opcode updates zero overflow and negative flags from memory`() {
        val bitProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x0F,
            Cpu6502.OP_BIT_ZP,
            0x10,
            Cpu6502.OP_BIT_ABS,
            0x00,
            0x02
        )
        val (cpu, bus, _) = cpuWithProgram(bitProgram)
        bus.write(0x0010, 0xC0)
        bus.write(0x0200, 0x01)

        cpu.step()
        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.Z) != 0, "BIT zero page sets zero when accumulator and memory do not overlap")
        assertTrue((cpu.state.status and Cpu6502.V) != 0, "BIT zero page copies bit six into overflow")
        assertTrue((cpu.state.status and Cpu6502.N) != 0, "BIT zero page copies bit seven into negative")

        cpu.step()
        assertFalse((cpu.state.status and Cpu6502.Z) != 0, "BIT absolute clears zero when accumulator and memory overlap")
    }

    @Test
    fun `jump subroutine return break and interrupt opcodes control program counter`() {
        val controlFlowProgram = program(
            Cpu6502.OP_JSR_ABS,
            0x07,
            0x80.toByte(),
            Cpu6502.OP_JMP_ABS,
            0x0A,
            0x80.toByte(),
            Cpu6502.OP_NOP,
            Cpu6502.OP_LDA_IMM,
            0x01,
            Cpu6502.OP_RTS,
            Cpu6502.OP_JMP_IND,
            0x20,
            0x00,
            Cpu6502.OP_BRK
        )
        val (cpu, bus, _) = cpuWithProgram(controlFlowProgram)
        bus.write(0x0020, 0x0D)
        bus.write(0x0021, 0x80)
        bus.write(0x9100, Cpu6502.OP_RTI)

        cpu.step()
        assertEquals(0x8007, cpu.state.pc, "JSR absolute jumps to the subroutine address")

        cpu.step()
        assertEquals(0x01, cpu.state.a, "Subroutine body executes after JSR")

        cpu.step()
        assertEquals(0x8003, cpu.state.pc, "RTS returns to the instruction after JSR")

        cpu.step()
        assertEquals(0x800A, cpu.state.pc, "JMP absolute changes the program counter")

        cpu.step()
        assertEquals(0x800D, cpu.state.pc, "JMP indirect loads the target from memory")

        cpu.step()
        assertEquals(0x9100, cpu.state.pc, "BRK loads the IRQ vector")

        cpu.step()
        assertEquals(0x800F, cpu.state.pc, "RTI restores the program counter from the stack")
    }

    @Test
    fun `branch opcodes change program counter based on status flags`() {
        val branchProgram = program(
            Cpu6502.OP_LDA_IMM,
            0x00,
            Cpu6502.OP_BEQ,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x01,
            Cpu6502.OP_LDA_IMM,
            0x80.toByte(),
            Cpu6502.OP_BMI,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x02,
            Cpu6502.OP_CLV,
            Cpu6502.OP_BVC,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x03,
            Cpu6502.OP_SEC,
            Cpu6502.OP_BCS,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x04,
            Cpu6502.OP_CLC,
            Cpu6502.OP_BCC,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x05,
            Cpu6502.OP_BNE,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x06,
            Cpu6502.OP_LDA_IMM,
            0x40,
            Cpu6502.OP_ADC_IMM,
            0x40,
            Cpu6502.OP_BVS,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x07,
            Cpu6502.OP_BPL,
            0x02,
            Cpu6502.OP_LDA_IMM,
            0x08
        )
        val (cpu, _, _) = cpuWithProgram(branchProgram)

        repeat(17) { cpu.step() }

        assertEquals(0x08, cpu.state.a, "All branch opcodes route execution according to status flags")
    }

    @Test
    fun `flag opcodes set and clear status bits`() {
        val flagProgram = program(
            Cpu6502.OP_SEC,
            Cpu6502.OP_CLC,
            Cpu6502.OP_SEI,
            Cpu6502.OP_CLI,
            Cpu6502.OP_SED,
            Cpu6502.OP_CLD,
            Cpu6502.OP_LDA_IMM,
            0x40,
            Cpu6502.OP_ADC_IMM,
            0x40,
            Cpu6502.OP_CLV
        )
        val (cpu, _, _) = cpuWithProgram(flagProgram)

        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.C) != 0, "SEC sets carry")

        cpu.step()
        assertFalse((cpu.state.status and Cpu6502.C) != 0, "CLC clears carry")

        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.I) != 0, "SEI sets interrupt disable")

        cpu.step()
        assertFalse((cpu.state.status and Cpu6502.I) != 0, "CLI clears interrupt disable")

        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.D) != 0, "SED sets decimal mode")

        cpu.step()
        assertFalse((cpu.state.status and Cpu6502.D) != 0, "CLD clears decimal mode")

        cpu.step()
        cpu.step()
        assertTrue((cpu.state.status and Cpu6502.V) != 0, "ADC sets overflow before CLV")

        cpu.step()
        assertFalse((cpu.state.status and Cpu6502.V) != 0, "CLV clears overflow")
    }

    @Test
    fun `nop opcode only advances the program counter`() {
        val nopProgram = program(Cpu6502.OP_NOP)
        val (cpu, _, _) = cpuWithProgram(nopProgram)

        val cycles = cpu.step()

        assertEquals(0x8001, cpu.state.pc, "NOP advances the program counter by one byte")
        assertEquals(2, cycles, "NOP consumes two cycles")
    }

    @Test
    fun `remaining load opcodes read from their addressed memory`() {
        val ldaZeroPageProgram = program(Cpu6502.OP_LDA_ZP, 0x10)
        val (ldaZeroPageCpu, ldaZeroPageBus, _) = cpuWithProgram(ldaZeroPageProgram)
        ldaZeroPageBus.write(0x0010, 0x21)
        ldaZeroPageCpu.step()
        assertEquals(0x21, ldaZeroPageCpu.state.a, "LDA zero page reads memory into accumulator")

        val ldaAbsoluteProgram = program(Cpu6502.OP_LDA_ABS, 0x00, 0x02)
        val (ldaAbsoluteCpu, ldaAbsoluteBus, _) = cpuWithProgram(ldaAbsoluteProgram)
        ldaAbsoluteBus.write(0x0200, 0x22)
        ldaAbsoluteCpu.step()
        assertEquals(0x22, ldaAbsoluteCpu.state.a, "LDA absolute reads memory into accumulator")

        val ldxZeroPageProgram = program(Cpu6502.OP_LDX_ZP, 0x10)
        val (ldxZeroPageCpu, ldxZeroPageBus, _) = cpuWithProgram(ldxZeroPageProgram)
        ldxZeroPageBus.write(0x0010, 0x23)
        ldxZeroPageCpu.step()
        assertEquals(0x23, ldxZeroPageCpu.state.x, "LDX zero page reads memory into X")

        val ldxAbsoluteProgram = program(Cpu6502.OP_LDX_ABS, 0x00, 0x02)
        val (ldxAbsoluteCpu, ldxAbsoluteBus, _) = cpuWithProgram(ldxAbsoluteProgram)
        ldxAbsoluteBus.write(0x0200, 0x24)
        ldxAbsoluteCpu.step()
        assertEquals(0x24, ldxAbsoluteCpu.state.x, "LDX absolute reads memory into X")

        val ldxAbsoluteYProgram = program(Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_LDX_ABSY, 0x00, 0x02)
        val (ldxAbsoluteYCpu, ldxAbsoluteYBus, _) = cpuWithProgram(ldxAbsoluteYProgram)
        ldxAbsoluteYBus.write(0x0202, 0x25)
        ldxAbsoluteYCpu.step()
        ldxAbsoluteYCpu.step()
        assertEquals(0x25, ldxAbsoluteYCpu.state.x, "LDX absolute,Y reads memory into X")

        val ldyZeroPageProgram = program(Cpu6502.OP_LDY_ZP, 0x10)
        val (ldyZeroPageCpu, ldyZeroPageBus, _) = cpuWithProgram(ldyZeroPageProgram)
        ldyZeroPageBus.write(0x0010, 0x26)
        ldyZeroPageCpu.step()
        assertEquals(0x26, ldyZeroPageCpu.state.y, "LDY zero page reads memory into Y")

        val ldyAbsoluteProgram = program(Cpu6502.OP_LDY_ABS, 0x00, 0x02)
        val (ldyAbsoluteCpu, ldyAbsoluteBus, _) = cpuWithProgram(ldyAbsoluteProgram)
        ldyAbsoluteBus.write(0x0200, 0x27)
        ldyAbsoluteCpu.step()
        assertEquals(0x27, ldyAbsoluteCpu.state.y, "LDY absolute reads memory into Y")

        val ldyAbsoluteXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_LDY_ABSX, 0x00, 0x02)
        val (ldyAbsoluteXCpu, ldyAbsoluteXBus, _) = cpuWithProgram(ldyAbsoluteXProgram)
        ldyAbsoluteXBus.write(0x0202, 0x28)
        ldyAbsoluteXCpu.step()
        ldyAbsoluteXCpu.step()
        assertEquals(0x28, ldyAbsoluteXCpu.state.y, "LDY absolute,X reads memory into Y")
    }

    @Test
    fun `remaining store opcodes write registers to their addressed memory`() {
        val staZeroPageXProgram = program(Cpu6502.OP_LDA_IMM, 0x31, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_STA_ZPX, 0x10)
        val (staZeroPageXCpu, staZeroPageXBus, _) = cpuWithProgram(staZeroPageXProgram)
        repeat(3) { staZeroPageXCpu.step() }
        assertEquals(0x31, staZeroPageXBus.read(0x0012), "STA zero page,X writes accumulator to indexed memory")

        val staAbsoluteProgram = program(Cpu6502.OP_LDA_IMM, 0x32, Cpu6502.OP_STA_ABS, 0x00, 0x02)
        val (staAbsoluteCpu, staAbsoluteBus, _) = cpuWithProgram(staAbsoluteProgram)
        staAbsoluteCpu.step()
        staAbsoluteCpu.step()
        assertEquals(0x32, staAbsoluteBus.read(0x0200), "STA absolute writes accumulator to memory")

        val staAbsoluteXProgram = program(Cpu6502.OP_LDA_IMM, 0x33, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_STA_ABSX, 0x00, 0x02)
        val (staAbsoluteXCpu, staAbsoluteXBus, _) = cpuWithProgram(staAbsoluteXProgram)
        repeat(3) { staAbsoluteXCpu.step() }
        assertEquals(0x33, staAbsoluteXBus.read(0x0202), "STA absolute,X writes accumulator to indexed memory")

        val staAbsoluteYProgram = program(Cpu6502.OP_LDA_IMM, 0x34, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_STA_ABSY, 0x00, 0x02)
        val (staAbsoluteYCpu, staAbsoluteYBus, _) = cpuWithProgram(staAbsoluteYProgram)
        repeat(3) { staAbsoluteYCpu.step() }
        assertEquals(0x34, staAbsoluteYBus.read(0x0202), "STA absolute,Y writes accumulator to indexed memory")

        val staIndexedIndirectProgram = program(Cpu6502.OP_LDA_IMM, 0x35, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_STA_INDX, 0x20)
        val (staIndexedIndirectCpu, staIndexedIndirectBus, _) = cpuWithProgram(staIndexedIndirectProgram)
        staIndexedIndirectBus.write(0x0022, 0x00)
        staIndexedIndirectBus.write(0x0023, 0x02)
        repeat(3) { staIndexedIndirectCpu.step() }
        assertEquals(0x35, staIndexedIndirectBus.read(0x0200), "STA indexed-indirect writes accumulator through pointer")

        val stxZeroPageProgram = program(Cpu6502.OP_LDX_IMM, 0x36, Cpu6502.OP_STX_ZP, 0x10)
        val (stxZeroPageCpu, stxZeroPageBus, _) = cpuWithProgram(stxZeroPageProgram)
        stxZeroPageCpu.step()
        stxZeroPageCpu.step()
        assertEquals(0x36, stxZeroPageBus.read(0x0010), "STX zero page writes X to memory")

        val stxAbsoluteProgram = program(Cpu6502.OP_LDX_IMM, 0x37, Cpu6502.OP_STX_ABS, 0x00, 0x02)
        val (stxAbsoluteCpu, stxAbsoluteBus, _) = cpuWithProgram(stxAbsoluteProgram)
        stxAbsoluteCpu.step()
        stxAbsoluteCpu.step()
        assertEquals(0x37, stxAbsoluteBus.read(0x0200), "STX absolute writes X to memory")

        val styZeroPageProgram = program(Cpu6502.OP_LDY_IMM, 0x38, Cpu6502.OP_STY_ZP, 0x10)
        val (styZeroPageCpu, styZeroPageBus, _) = cpuWithProgram(styZeroPageProgram)
        styZeroPageCpu.step()
        styZeroPageCpu.step()
        assertEquals(0x38, styZeroPageBus.read(0x0010), "STY zero page writes Y to memory")

        val styAbsoluteProgram = program(Cpu6502.OP_LDY_IMM, 0x39, Cpu6502.OP_STY_ABS, 0x00, 0x02)
        val (styAbsoluteCpu, styAbsoluteBus, _) = cpuWithProgram(styAbsoluteProgram)
        styAbsoluteCpu.step()
        styAbsoluteCpu.step()
        assertEquals(0x39, styAbsoluteBus.read(0x0200), "STY absolute writes Y to memory")
    }

    @Test
    fun `remaining adc opcodes add addressed memory to accumulator`() {
        val adcZeroPageXProgram = program(Cpu6502.OP_LDA_IMM, 0x10, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ADC_ZPX, 0x20)
        val (adcZeroPageXCpu, adcZeroPageXBus, _) = cpuWithProgram(adcZeroPageXProgram)
        adcZeroPageXBus.write(0x0022, 0x03)
        repeat(3) { adcZeroPageXCpu.step() }
        assertEquals(0x13, adcZeroPageXCpu.state.a, "ADC zero page,X adds indexed memory")

        val adcAbsoluteProgram = program(Cpu6502.OP_LDA_IMM, 0x10, Cpu6502.OP_ADC_ABS, 0x00, 0x02)
        val (adcAbsoluteCpu, adcAbsoluteBus, _) = cpuWithProgram(adcAbsoluteProgram)
        adcAbsoluteBus.write(0x0200, 0x04)
        adcAbsoluteCpu.step()
        adcAbsoluteCpu.step()
        assertEquals(0x14, adcAbsoluteCpu.state.a, "ADC absolute adds memory")

        val adcAbsoluteYProgram = program(Cpu6502.OP_LDA_IMM, 0x10, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_ADC_ABSY, 0x00, 0x02)
        val (adcAbsoluteYCpu, adcAbsoluteYBus, _) = cpuWithProgram(adcAbsoluteYProgram)
        adcAbsoluteYBus.write(0x0202, 0x05)
        repeat(3) { adcAbsoluteYCpu.step() }
        assertEquals(0x15, adcAbsoluteYCpu.state.a, "ADC absolute,Y adds indexed memory")

        val adcIndexedIndirectProgram = program(Cpu6502.OP_LDA_IMM, 0x10, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ADC_INDX, 0x20)
        val (adcIndexedIndirectCpu, adcIndexedIndirectBus, _) = cpuWithProgram(adcIndexedIndirectProgram)
        adcIndexedIndirectBus.write(0x0022, 0x00)
        adcIndexedIndirectBus.write(0x0023, 0x02)
        adcIndexedIndirectBus.write(0x0200, 0x06)
        repeat(3) { adcIndexedIndirectCpu.step() }
        assertEquals(0x16, adcIndexedIndirectCpu.state.a, "ADC indexed-indirect adds memory through pointer")

        val adcIndirectIndexedProgram = program(Cpu6502.OP_LDA_IMM, 0x10, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_ADC_INDY, 0x20)
        val (adcIndirectIndexedCpu, adcIndirectIndexedBus, _) = cpuWithProgram(adcIndirectIndexedProgram)
        adcIndirectIndexedBus.write(0x0020, 0x00)
        adcIndirectIndexedBus.write(0x0021, 0x02)
        adcIndirectIndexedBus.write(0x0202, 0x07)
        repeat(3) { adcIndirectIndexedCpu.step() }
        assertEquals(0x17, adcIndirectIndexedCpu.state.a, "ADC indirect-indexed adds memory through pointer plus Y")
    }

    @Test
    fun `remaining sbc opcodes subtract addressed memory from accumulator`() {
        val sbcZeroPageProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_SBC_ZP, 0x10)
        val (sbcZeroPageCpu, sbcZeroPageBus, _) = cpuWithProgram(sbcZeroPageProgram)
        sbcZeroPageBus.write(0x0010, 0x01)
        repeat(3) { sbcZeroPageCpu.step() }
        assertEquals(0x1F, sbcZeroPageCpu.state.a, "SBC zero page subtracts memory")

        val sbcZeroPageXProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_SBC_ZPX, 0x10)
        val (sbcZeroPageXCpu, sbcZeroPageXBus, _) = cpuWithProgram(sbcZeroPageXProgram)
        sbcZeroPageXBus.write(0x0012, 0x02)
        repeat(4) { sbcZeroPageXCpu.step() }
        assertEquals(0x1E, sbcZeroPageXCpu.state.a, "SBC zero page,X subtracts indexed memory")

        val sbcAbsoluteProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_SBC_ABS, 0x00, 0x02)
        val (sbcAbsoluteCpu, sbcAbsoluteBus, _) = cpuWithProgram(sbcAbsoluteProgram)
        sbcAbsoluteBus.write(0x0200, 0x03)
        repeat(3) { sbcAbsoluteCpu.step() }
        assertEquals(0x1D, sbcAbsoluteCpu.state.a, "SBC absolute subtracts memory")

        val sbcAbsoluteXProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_SBC_ABSX, 0x00, 0x02)
        val (sbcAbsoluteXCpu, sbcAbsoluteXBus, _) = cpuWithProgram(sbcAbsoluteXProgram)
        sbcAbsoluteXBus.write(0x0202, 0x04)
        repeat(4) { sbcAbsoluteXCpu.step() }
        assertEquals(0x1C, sbcAbsoluteXCpu.state.a, "SBC absolute,X subtracts indexed memory")

        val sbcAbsoluteYProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_SBC_ABSY, 0x00, 0x02)
        val (sbcAbsoluteYCpu, sbcAbsoluteYBus, _) = cpuWithProgram(sbcAbsoluteYProgram)
        sbcAbsoluteYBus.write(0x0202, 0x05)
        repeat(4) { sbcAbsoluteYCpu.step() }
        assertEquals(0x1B, sbcAbsoluteYCpu.state.a, "SBC absolute,Y subtracts indexed memory")

        val sbcIndexedIndirectProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_SBC_INDX, 0x20)
        val (sbcIndexedIndirectCpu, sbcIndexedIndirectBus, _) = cpuWithProgram(sbcIndexedIndirectProgram)
        sbcIndexedIndirectBus.write(0x0022, 0x00)
        sbcIndexedIndirectBus.write(0x0023, 0x02)
        sbcIndexedIndirectBus.write(0x0200, 0x06)
        repeat(4) { sbcIndexedIndirectCpu.step() }
        assertEquals(0x1A, sbcIndexedIndirectCpu.state.a, "SBC indexed-indirect subtracts memory through pointer")

        val sbcIndirectIndexedProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_SBC_INDY, 0x20)
        val (sbcIndirectIndexedCpu, sbcIndirectIndexedBus, _) = cpuWithProgram(sbcIndirectIndexedProgram)
        sbcIndirectIndexedBus.write(0x0020, 0x00)
        sbcIndirectIndexedBus.write(0x0021, 0x02)
        sbcIndirectIndexedBus.write(0x0202, 0x07)
        repeat(4) { sbcIndirectIndexedCpu.step() }
        assertEquals(0x19, sbcIndirectIndexedCpu.state.a, "SBC indirect-indexed subtracts memory through pointer plus Y")
    }

    @Test
    fun `remaining and opcodes mask accumulator with addressed memory`() {
        val andZeroPageProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_AND_ZP, 0x10)
        val (andZeroPageCpu, andZeroPageBus, _) = cpuWithProgram(andZeroPageProgram)
        andZeroPageBus.write(0x0010, 0x0F)
        andZeroPageCpu.step()
        andZeroPageCpu.step()
        assertEquals(0x03, andZeroPageCpu.state.a, "AND zero page masks accumulator with memory")

        val andZeroPageXProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_AND_ZPX, 0x10)
        val (andZeroPageXCpu, andZeroPageXBus, _) = cpuWithProgram(andZeroPageXProgram)
        andZeroPageXBus.write(0x0012, 0x0F)
        repeat(3) { andZeroPageXCpu.step() }
        assertEquals(0x03, andZeroPageXCpu.state.a, "AND zero page,X masks accumulator with indexed memory")

        val andAbsoluteProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_AND_ABS, 0x00, 0x02)
        val (andAbsoluteCpu, andAbsoluteBus, _) = cpuWithProgram(andAbsoluteProgram)
        andAbsoluteBus.write(0x0200, 0x0F)
        andAbsoluteCpu.step()
        andAbsoluteCpu.step()
        assertEquals(0x03, andAbsoluteCpu.state.a, "AND absolute masks accumulator with memory")

        val andAbsoluteXProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_AND_ABSX, 0x00, 0x02)
        val (andAbsoluteXCpu, andAbsoluteXBus, _) = cpuWithProgram(andAbsoluteXProgram)
        andAbsoluteXBus.write(0x0202, 0x0F)
        repeat(3) { andAbsoluteXCpu.step() }
        assertEquals(0x03, andAbsoluteXCpu.state.a, "AND absolute,X masks accumulator with indexed memory")

        val andAbsoluteYProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_AND_ABSY, 0x00, 0x02)
        val (andAbsoluteYCpu, andAbsoluteYBus, _) = cpuWithProgram(andAbsoluteYProgram)
        andAbsoluteYBus.write(0x0202, 0x0F)
        repeat(3) { andAbsoluteYCpu.step() }
        assertEquals(0x03, andAbsoluteYCpu.state.a, "AND absolute,Y masks accumulator with indexed memory")

        val andIndexedIndirectProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_AND_INDX, 0x20)
        val (andIndexedIndirectCpu, andIndexedIndirectBus, _) = cpuWithProgram(andIndexedIndirectProgram)
        andIndexedIndirectBus.write(0x0022, 0x00)
        andIndexedIndirectBus.write(0x0023, 0x02)
        andIndexedIndirectBus.write(0x0200, 0x0F)
        repeat(3) { andIndexedIndirectCpu.step() }
        assertEquals(0x03, andIndexedIndirectCpu.state.a, "AND indexed-indirect masks accumulator through pointer")

        val andIndirectIndexedProgram = program(Cpu6502.OP_LDA_IMM, 0xF3, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_AND_INDY, 0x20)
        val (andIndirectIndexedCpu, andIndirectIndexedBus, _) = cpuWithProgram(andIndirectIndexedProgram)
        andIndirectIndexedBus.write(0x0020, 0x00)
        andIndirectIndexedBus.write(0x0021, 0x02)
        andIndirectIndexedBus.write(0x0202, 0x0F)
        repeat(3) { andIndirectIndexedCpu.step() }
        assertEquals(0x03, andIndirectIndexedCpu.state.a, "AND indirect-indexed masks accumulator through pointer plus Y")
    }

    @Test
    fun `remaining ora opcodes set accumulator bits from addressed memory`() {
        val oraImmediateProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_ORA_IMM, 0x0F)
        val (oraImmediateCpu, _, _) = cpuWithProgram(oraImmediateProgram)
        oraImmediateCpu.step()
        oraImmediateCpu.step()
        assertEquals(0x3F, oraImmediateCpu.state.a, "ORA immediate sets accumulator bits from operand")

        val oraZeroPageXProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ORA_ZPX, 0x10)
        val (oraZeroPageXCpu, oraZeroPageXBus, _) = cpuWithProgram(oraZeroPageXProgram)
        oraZeroPageXBus.write(0x0012, 0x0F)
        repeat(3) { oraZeroPageXCpu.step() }
        assertEquals(0x3F, oraZeroPageXCpu.state.a, "ORA zero page,X sets accumulator bits from indexed memory")

        val oraAbsoluteProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_ORA_ABS, 0x00, 0x02)
        val (oraAbsoluteCpu, oraAbsoluteBus, _) = cpuWithProgram(oraAbsoluteProgram)
        oraAbsoluteBus.write(0x0200, 0x0F)
        oraAbsoluteCpu.step()
        oraAbsoluteCpu.step()
        assertEquals(0x3F, oraAbsoluteCpu.state.a, "ORA absolute sets accumulator bits from memory")

        val oraAbsoluteXProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ORA_ABSX, 0x00, 0x02)
        val (oraAbsoluteXCpu, oraAbsoluteXBus, _) = cpuWithProgram(oraAbsoluteXProgram)
        oraAbsoluteXBus.write(0x0202, 0x0F)
        repeat(3) { oraAbsoluteXCpu.step() }
        assertEquals(0x3F, oraAbsoluteXCpu.state.a, "ORA absolute,X sets accumulator bits from indexed memory")

        val oraAbsoluteYProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_ORA_ABSY, 0x00, 0x02)
        val (oraAbsoluteYCpu, oraAbsoluteYBus, _) = cpuWithProgram(oraAbsoluteYProgram)
        oraAbsoluteYBus.write(0x0202, 0x0F)
        repeat(3) { oraAbsoluteYCpu.step() }
        assertEquals(0x3F, oraAbsoluteYCpu.state.a, "ORA absolute,Y sets accumulator bits from indexed memory")

        val oraIndexedIndirectProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ORA_INDX, 0x20)
        val (oraIndexedIndirectCpu, oraIndexedIndirectBus, _) = cpuWithProgram(oraIndexedIndirectProgram)
        oraIndexedIndirectBus.write(0x0022, 0x00)
        oraIndexedIndirectBus.write(0x0023, 0x02)
        oraIndexedIndirectBus.write(0x0200, 0x0F)
        repeat(3) { oraIndexedIndirectCpu.step() }
        assertEquals(0x3F, oraIndexedIndirectCpu.state.a, "ORA indexed-indirect sets accumulator bits through pointer")

        val oraIndirectIndexedProgram = program(Cpu6502.OP_LDA_IMM, 0x30, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_ORA_INDY, 0x20)
        val (oraIndirectIndexedCpu, oraIndirectIndexedBus, _) = cpuWithProgram(oraIndirectIndexedProgram)
        oraIndirectIndexedBus.write(0x0020, 0x00)
        oraIndirectIndexedBus.write(0x0021, 0x02)
        oraIndirectIndexedBus.write(0x0202, 0x0F)
        repeat(3) { oraIndirectIndexedCpu.step() }
        assertEquals(0x3F, oraIndirectIndexedCpu.state.a, "ORA indirect-indexed sets accumulator bits through pointer plus Y")
    }

    @Test
    fun `remaining eor opcodes toggle accumulator bits from addressed memory`() {
        val eorImmediateProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_EOR_IMM, 0x0F)
        val (eorImmediateCpu, _, _) = cpuWithProgram(eorImmediateProgram)
        eorImmediateCpu.step()
        eorImmediateCpu.step()
        assertEquals(0xFF, eorImmediateCpu.state.a, "EOR immediate toggles accumulator bits from operand")

        val eorZeroPageXProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_EOR_ZPX, 0x10)
        val (eorZeroPageXCpu, eorZeroPageXBus, _) = cpuWithProgram(eorZeroPageXProgram)
        eorZeroPageXBus.write(0x0012, 0x0F)
        repeat(3) { eorZeroPageXCpu.step() }
        assertEquals(0xFF, eorZeroPageXCpu.state.a, "EOR zero page,X toggles accumulator bits from indexed memory")

        val eorAbsoluteProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_EOR_ABS, 0x00, 0x02)
        val (eorAbsoluteCpu, eorAbsoluteBus, _) = cpuWithProgram(eorAbsoluteProgram)
        eorAbsoluteBus.write(0x0200, 0x0F)
        eorAbsoluteCpu.step()
        eorAbsoluteCpu.step()
        assertEquals(0xFF, eorAbsoluteCpu.state.a, "EOR absolute toggles accumulator bits from memory")

        val eorAbsoluteXProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_EOR_ABSX, 0x00, 0x02)
        val (eorAbsoluteXCpu, eorAbsoluteXBus, _) = cpuWithProgram(eorAbsoluteXProgram)
        eorAbsoluteXBus.write(0x0202, 0x0F)
        repeat(3) { eorAbsoluteXCpu.step() }
        assertEquals(0xFF, eorAbsoluteXCpu.state.a, "EOR absolute,X toggles accumulator bits from indexed memory")

        val eorAbsoluteYProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_EOR_ABSY, 0x00, 0x02)
        val (eorAbsoluteYCpu, eorAbsoluteYBus, _) = cpuWithProgram(eorAbsoluteYProgram)
        eorAbsoluteYBus.write(0x0202, 0x0F)
        repeat(3) { eorAbsoluteYCpu.step() }
        assertEquals(0xFF, eorAbsoluteYCpu.state.a, "EOR absolute,Y toggles accumulator bits from indexed memory")

        val eorIndexedIndirectProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_EOR_INDX, 0x20)
        val (eorIndexedIndirectCpu, eorIndexedIndirectBus, _) = cpuWithProgram(eorIndexedIndirectProgram)
        eorIndexedIndirectBus.write(0x0022, 0x00)
        eorIndexedIndirectBus.write(0x0023, 0x02)
        eorIndexedIndirectBus.write(0x0200, 0x0F)
        repeat(3) { eorIndexedIndirectCpu.step() }
        assertEquals(0xFF, eorIndexedIndirectCpu.state.a, "EOR indexed-indirect toggles accumulator bits through pointer")

        val eorIndirectIndexedProgram = program(Cpu6502.OP_LDA_IMM, 0xF0, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_EOR_INDY, 0x20)
        val (eorIndirectIndexedCpu, eorIndirectIndexedBus, _) = cpuWithProgram(eorIndirectIndexedProgram)
        eorIndirectIndexedBus.write(0x0020, 0x00)
        eorIndirectIndexedBus.write(0x0021, 0x02)
        eorIndirectIndexedBus.write(0x0202, 0x0F)
        repeat(3) { eorIndirectIndexedCpu.step() }
        assertEquals(0xFF, eorIndirectIndexedCpu.state.a, "EOR indirect-indexed toggles accumulator bits through pointer plus Y")
    }

    @Test
    fun `remaining compare opcodes update flags from addressed memory`() {
        val cmpZeroPageXProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_CMP_ZPX, 0x10)
        val (cmpZeroPageXCpu, cmpZeroPageXBus, _) = cpuWithProgram(cmpZeroPageXProgram)
        cmpZeroPageXBus.write(0x0012, 0x20)
        repeat(3) { cmpZeroPageXCpu.step() }
        assertTrue((cmpZeroPageXCpu.state.status and Cpu6502.Z) != 0, "CMP zero page,X sets zero when values match")

        val cmpAbsoluteProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_CMP_ABS, 0x00, 0x02)
        val (cmpAbsoluteCpu, cmpAbsoluteBus, _) = cpuWithProgram(cmpAbsoluteProgram)
        cmpAbsoluteBus.write(0x0200, 0x10)
        cmpAbsoluteCpu.step()
        cmpAbsoluteCpu.step()
        assertTrue((cmpAbsoluteCpu.state.status and Cpu6502.C) != 0, "CMP absolute sets carry when accumulator is greater")

        val cmpAbsoluteXProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_CMP_ABSX, 0x00, 0x02)
        val (cmpAbsoluteXCpu, cmpAbsoluteXBus, _) = cpuWithProgram(cmpAbsoluteXProgram)
        cmpAbsoluteXBus.write(0x0202, 0x30)
        repeat(3) { cmpAbsoluteXCpu.step() }
        assertTrue((cmpAbsoluteXCpu.state.status and Cpu6502.N) != 0, "CMP absolute,X sets negative when accumulator is smaller")

        val cmpAbsoluteYProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_CMP_ABSY, 0x00, 0x02)
        val (cmpAbsoluteYCpu, cmpAbsoluteYBus, _) = cpuWithProgram(cmpAbsoluteYProgram)
        cmpAbsoluteYBus.write(0x0202, 0x20)
        repeat(3) { cmpAbsoluteYCpu.step() }
        assertTrue((cmpAbsoluteYCpu.state.status and Cpu6502.Z) != 0, "CMP absolute,Y sets zero when values match")

        val cmpIndexedIndirectProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_CMP_INDX, 0x20)
        val (cmpIndexedIndirectCpu, cmpIndexedIndirectBus, _) = cpuWithProgram(cmpIndexedIndirectProgram)
        cmpIndexedIndirectBus.write(0x0022, 0x00)
        cmpIndexedIndirectBus.write(0x0023, 0x02)
        cmpIndexedIndirectBus.write(0x0200, 0x20)
        repeat(3) { cmpIndexedIndirectCpu.step() }
        assertTrue((cmpIndexedIndirectCpu.state.status and Cpu6502.Z) != 0, "CMP indexed-indirect sets zero through pointer")

        val cmpIndirectIndexedProgram = program(Cpu6502.OP_LDA_IMM, 0x20, Cpu6502.OP_LDY_IMM, 0x02, Cpu6502.OP_CMP_INDY, 0x20)
        val (cmpIndirectIndexedCpu, cmpIndirectIndexedBus, _) = cpuWithProgram(cmpIndirectIndexedProgram)
        cmpIndirectIndexedBus.write(0x0020, 0x00)
        cmpIndirectIndexedBus.write(0x0021, 0x02)
        cmpIndirectIndexedBus.write(0x0202, 0x20)
        repeat(3) { cmpIndirectIndexedCpu.step() }
        assertTrue((cmpIndirectIndexedCpu.state.status and Cpu6502.Z) != 0, "CMP indirect-indexed sets zero through pointer plus Y")

        val cpxAbsoluteProgram = program(Cpu6502.OP_LDX_IMM, 0x20, Cpu6502.OP_CPX_ABS, 0x00, 0x02)
        val (cpxAbsoluteCpu, cpxAbsoluteBus, _) = cpuWithProgram(cpxAbsoluteProgram)
        cpxAbsoluteBus.write(0x0200, 0x20)
        cpxAbsoluteCpu.step()
        cpxAbsoluteCpu.step()
        assertTrue((cpxAbsoluteCpu.state.status and Cpu6502.Z) != 0, "CPX absolute sets zero when values match")

        val cpyAbsoluteProgram = program(Cpu6502.OP_LDY_IMM, 0x20, Cpu6502.OP_CPY_ABS, 0x00, 0x02)
        val (cpyAbsoluteCpu, cpyAbsoluteBus, _) = cpuWithProgram(cpyAbsoluteProgram)
        cpyAbsoluteBus.write(0x0200, 0x20)
        cpyAbsoluteCpu.step()
        cpyAbsoluteCpu.step()
        assertTrue((cpyAbsoluteCpu.state.status and Cpu6502.Z) != 0, "CPY absolute sets zero when values match")
    }

    @Test
    fun `remaining increment and decrement opcodes mutate addressed memory and registers`() {
        val incZeroPageXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_INC_ZPX, 0x10)
        val (incZeroPageXCpu, incZeroPageXBus, _) = cpuWithProgram(incZeroPageXProgram)
        incZeroPageXBus.write(0x0012, 0x01)
        incZeroPageXCpu.step()
        incZeroPageXCpu.step()
        assertEquals(0x02, incZeroPageXBus.read(0x0012), "INC zero page,X increments indexed memory")

        val incAbsoluteProgram = program(Cpu6502.OP_INC_ABS, 0x00, 0x02)
        val (incAbsoluteCpu, incAbsoluteBus, _) = cpuWithProgram(incAbsoluteProgram)
        incAbsoluteBus.write(0x0200, 0x01)
        incAbsoluteCpu.step()
        assertEquals(0x02, incAbsoluteBus.read(0x0200), "INC absolute increments memory")

        val incAbsoluteXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_INC_ABSX, 0x00, 0x02)
        val (incAbsoluteXCpu, incAbsoluteXBus, _) = cpuWithProgram(incAbsoluteXProgram)
        incAbsoluteXBus.write(0x0202, 0x01)
        incAbsoluteXCpu.step()
        incAbsoluteXCpu.step()
        assertEquals(0x02, incAbsoluteXBus.read(0x0202), "INC absolute,X increments indexed memory")

        val decZeroPageXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_DEC_ZPX, 0x10)
        val (decZeroPageXCpu, decZeroPageXBus, _) = cpuWithProgram(decZeroPageXProgram)
        decZeroPageXBus.write(0x0012, 0x02)
        decZeroPageXCpu.step()
        decZeroPageXCpu.step()
        assertEquals(0x01, decZeroPageXBus.read(0x0012), "DEC zero page,X decrements indexed memory")

        val decAbsoluteProgram = program(Cpu6502.OP_DEC_ABS, 0x00, 0x02)
        val (decAbsoluteCpu, decAbsoluteBus, _) = cpuWithProgram(decAbsoluteProgram)
        decAbsoluteBus.write(0x0200, 0x02)
        decAbsoluteCpu.step()
        assertEquals(0x01, decAbsoluteBus.read(0x0200), "DEC absolute decrements memory")

        val decAbsoluteXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_DEC_ABSX, 0x00, 0x02)
        val (decAbsoluteXCpu, decAbsoluteXBus, _) = cpuWithProgram(decAbsoluteXProgram)
        decAbsoluteXBus.write(0x0202, 0x02)
        decAbsoluteXCpu.step()
        decAbsoluteXCpu.step()
        assertEquals(0x01, decAbsoluteXBus.read(0x0202), "DEC absolute,X decrements indexed memory")

        val dexProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_DEX)
        val (dexCpu, _, _) = cpuWithProgram(dexProgram)
        dexCpu.step()
        dexCpu.step()
        assertEquals(0x01, dexCpu.state.x, "DEX decrements X")

        val inyProgram = program(Cpu6502.OP_LDY_IMM, 0x01, Cpu6502.OP_INY)
        val (inyCpu, _, _) = cpuWithProgram(inyProgram)
        inyCpu.step()
        inyCpu.step()
        assertEquals(0x02, inyCpu.state.y, "INY increments Y")
    }

    @Test
    fun `remaining shift and rotate opcodes mutate addressed values`() {
        val aslZeroPageProgram = program(Cpu6502.OP_ASL_ZP, 0x10)
        val (aslZeroPageCpu, aslZeroPageBus, _) = cpuWithProgram(aslZeroPageProgram)
        aslZeroPageBus.write(0x0010, 0x40)
        aslZeroPageCpu.step()
        assertEquals(0x80, aslZeroPageBus.read(0x0010), "ASL zero page shifts memory left")

        val aslZeroPageXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ASL_ZPX, 0x10)
        val (aslZeroPageXCpu, aslZeroPageXBus, _) = cpuWithProgram(aslZeroPageXProgram)
        aslZeroPageXBus.write(0x0012, 0x40)
        aslZeroPageXCpu.step()
        aslZeroPageXCpu.step()
        assertEquals(0x80, aslZeroPageXBus.read(0x0012), "ASL zero page,X shifts indexed memory left")

        val aslAbsoluteProgram = program(Cpu6502.OP_ASL_ABS, 0x00, 0x02)
        val (aslAbsoluteCpu, aslAbsoluteBus, _) = cpuWithProgram(aslAbsoluteProgram)
        aslAbsoluteBus.write(0x0200, 0x40)
        aslAbsoluteCpu.step()
        assertEquals(0x80, aslAbsoluteBus.read(0x0200), "ASL absolute shifts memory left")

        val aslAbsoluteXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ASL_ABSX, 0x00, 0x02)
        val (aslAbsoluteXCpu, aslAbsoluteXBus, _) = cpuWithProgram(aslAbsoluteXProgram)
        aslAbsoluteXBus.write(0x0202, 0x40)
        aslAbsoluteXCpu.step()
        aslAbsoluteXCpu.step()
        assertEquals(0x80, aslAbsoluteXBus.read(0x0202), "ASL absolute,X shifts indexed memory left")

        val lsrAccumulatorProgram = program(Cpu6502.OP_LDA_IMM, 0x02, Cpu6502.OP_LSR_ACC)
        val (lsrAccumulatorCpu, _, _) = cpuWithProgram(lsrAccumulatorProgram)
        lsrAccumulatorCpu.step()
        lsrAccumulatorCpu.step()
        assertEquals(0x01, lsrAccumulatorCpu.state.a, "LSR accumulator shifts accumulator right")

        val lsrZeroPageXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_LSR_ZPX, 0x10)
        val (lsrZeroPageXCpu, lsrZeroPageXBus, _) = cpuWithProgram(lsrZeroPageXProgram)
        lsrZeroPageXBus.write(0x0012, 0x02)
        lsrZeroPageXCpu.step()
        lsrZeroPageXCpu.step()
        assertEquals(0x01, lsrZeroPageXBus.read(0x0012), "LSR zero page,X shifts indexed memory right")

        val lsrAbsoluteProgram = program(Cpu6502.OP_LSR_ABS, 0x00, 0x02)
        val (lsrAbsoluteCpu, lsrAbsoluteBus, _) = cpuWithProgram(lsrAbsoluteProgram)
        lsrAbsoluteBus.write(0x0200, 0x02)
        lsrAbsoluteCpu.step()
        assertEquals(0x01, lsrAbsoluteBus.read(0x0200), "LSR absolute shifts memory right")

        val lsrAbsoluteXProgram = program(Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_LSR_ABSX, 0x00, 0x02)
        val (lsrAbsoluteXCpu, lsrAbsoluteXBus, _) = cpuWithProgram(lsrAbsoluteXProgram)
        lsrAbsoluteXBus.write(0x0202, 0x02)
        lsrAbsoluteXCpu.step()
        lsrAbsoluteXCpu.step()
        assertEquals(0x01, lsrAbsoluteXBus.read(0x0202), "LSR absolute,X shifts indexed memory right")

        val rolAccumulatorProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDA_IMM, 0x40, Cpu6502.OP_ROL_ACC)
        val (rolAccumulatorCpu, _, _) = cpuWithProgram(rolAccumulatorProgram)
        repeat(3) { rolAccumulatorCpu.step() }
        assertEquals(0x81, rolAccumulatorCpu.state.a, "ROL accumulator rotates accumulator left through carry")

        val rolZeroPageXProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ROL_ZPX, 0x10)
        val (rolZeroPageXCpu, rolZeroPageXBus, _) = cpuWithProgram(rolZeroPageXProgram)
        rolZeroPageXBus.write(0x0012, 0x40)
        repeat(3) { rolZeroPageXCpu.step() }
        assertEquals(0x81, rolZeroPageXBus.read(0x0012), "ROL zero page,X rotates indexed memory left through carry")

        val rolAbsoluteProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_ROL_ABS, 0x00, 0x02)
        val (rolAbsoluteCpu, rolAbsoluteBus, _) = cpuWithProgram(rolAbsoluteProgram)
        rolAbsoluteBus.write(0x0200, 0x40)
        rolAbsoluteCpu.step()
        rolAbsoluteCpu.step()
        assertEquals(0x81, rolAbsoluteBus.read(0x0200), "ROL absolute rotates memory left through carry")

        val rolAbsoluteXProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ROL_ABSX, 0x00, 0x02)
        val (rolAbsoluteXCpu, rolAbsoluteXBus, _) = cpuWithProgram(rolAbsoluteXProgram)
        rolAbsoluteXBus.write(0x0202, 0x40)
        repeat(3) { rolAbsoluteXCpu.step() }
        assertEquals(0x81, rolAbsoluteXBus.read(0x0202), "ROL absolute,X rotates indexed memory left through carry")

        val rorZeroPageProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_ROR_ZP, 0x10)
        val (rorZeroPageCpu, rorZeroPageBus, _) = cpuWithProgram(rorZeroPageProgram)
        rorZeroPageBus.write(0x0010, 0x02)
        rorZeroPageCpu.step()
        rorZeroPageCpu.step()
        assertEquals(0x81, rorZeroPageBus.read(0x0010), "ROR zero page rotates memory right through carry")

        val rorZeroPageXProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ROR_ZPX, 0x10)
        val (rorZeroPageXCpu, rorZeroPageXBus, _) = cpuWithProgram(rorZeroPageXProgram)
        rorZeroPageXBus.write(0x0012, 0x02)
        repeat(3) { rorZeroPageXCpu.step() }
        assertEquals(0x81, rorZeroPageXBus.read(0x0012), "ROR zero page,X rotates indexed memory right through carry")

        val rorAbsoluteProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_ROR_ABS, 0x00, 0x02)
        val (rorAbsoluteCpu, rorAbsoluteBus, _) = cpuWithProgram(rorAbsoluteProgram)
        rorAbsoluteBus.write(0x0200, 0x02)
        rorAbsoluteCpu.step()
        rorAbsoluteCpu.step()
        assertEquals(0x81, rorAbsoluteBus.read(0x0200), "ROR absolute rotates memory right through carry")

        val rorAbsoluteXProgram = program(Cpu6502.OP_SEC, Cpu6502.OP_LDX_IMM, 0x02, Cpu6502.OP_ROR_ABSX, 0x00, 0x02)
        val (rorAbsoluteXCpu, rorAbsoluteXBus, _) = cpuWithProgram(rorAbsoluteXProgram)
        rorAbsoluteXBus.write(0x0202, 0x02)
        repeat(3) { rorAbsoluteXCpu.step() }
        assertEquals(0x81, rorAbsoluteXBus.read(0x0202), "ROR absolute,X rotates indexed memory right through carry")
    }

    @Test
    fun `every opcode has a decoder entry`() {
        repeat(256) { opcode ->
            val (cpu, _, _) = cpuWithProgram(program(opcode, 0, 0))
            cpu.step()
        }
    }

    @Test
    fun `memory RMW emits old value dummy write before final write`() {
        val (cpu, bus, _) = cpuWithProgram(program(Cpu6502.OP_ASL_ZP, 0x10))
        bus.write(0x10, 0x41)
        val cycles = mutableListOf<CpuBus.Cycle>()
        bus.setCycleListener { cycles += it }

        assertEquals(5, cpu.step())

        assertEquals(
            listOf(
                CpuBus.CycleType.READ,
                CpuBus.CycleType.READ,
                CpuBus.CycleType.READ,
                CpuBus.CycleType.DUMMY_WRITE,
                CpuBus.CycleType.WRITE,
            ),
            cycles.map { it.type },
        )
        assertEquals(listOf(0x41, 0x82), cycles.takeLast(2).map { it.value })
    }

    @Test
    fun `page crossing indexed read performs wrong page dummy read`() {
        val (cpu, bus, _) = cpuWithProgram(
            program(Cpu6502.OP_LDX_IMM, 1, Cpu6502.OP_LDA_ABSX, 0xFF, 0x00)
        )
        cpu.step()
        bus.write(0x0100, 0x5A)
        val cycles = mutableListOf<CpuBus.Cycle>()
        bus.setCycleListener { cycles += it }

        assertEquals(5, cpu.step())

        assertEquals(CpuBus.CycleType.DUMMY_READ, cycles[3].type)
        assertEquals(0x0000, cycles[3].address)
        assertEquals(0x0100, cycles[4].address)
        assertEquals(0x5A, cpu.state.a)
    }

    @Test
    fun `unofficial SLO shifts memory then ORs accumulator`() {
        val (cpu, bus, _) = cpuWithProgram(program(Cpu6502.OP_LDA_IMM, 0x01, 0x07, 0x10))
        bus.write(0x10, 0x40)
        cpu.step()

        assertEquals(5, cpu.step())

        assertEquals(0x80, bus.read(0x10))
        assertEquals(0x81, cpu.state.a)
    }
}
