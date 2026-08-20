package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class RMWTest : FreeSpec({
    lateinit var memory: IntArray
    lateinit var state: CpuState
    lateinit var bus: FakeBus
    lateinit var cpu: Cpu6502

    beforeTest {
        memory = IntArray(0x10_000)
        state = CpuState()
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
    }

    "SLO shifts memory left then ORs accumulator" {
        state.pc = 0x8000
        state.a = 0x01
        memory[0x8000] = 0x07
        memory[0x8001] = 0x20
        memory[0x0020] = 0x81

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x02
        state.a shouldBe 0x03
        state.c shouldBe true
        state.z shouldBe false
        state.n shouldBe false
        bus.writes shouldBe listOf(
            FakeBus.Write(0x0020, 0x81),
            FakeBus.Write(0x0020, 0x02),
        )
        cycles shouldBe 5
    }

    "RLA rotates memory left then ANDs accumulator" {
        state.pc = 0x8000
        state.a = 0xFF
        state.c = true
        memory[0x8000] = 0x27
        memory[0x8001] = 0x20
        memory[0x0020] = 0x40

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x81
        state.a shouldBe 0x81
        state.c shouldBe false
        state.n shouldBe true
        cycles shouldBe 5
    }

    "SRE shifts memory right then EORs accumulator" {
        state.pc = 0x8000
        state.a = 0xF0
        memory[0x8000] = 0x47
        memory[0x8001] = 0x20
        memory[0x0020] = 0x03

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x01
        state.a shouldBe 0xF1
        state.c shouldBe true
        state.n shouldBe true
        cycles shouldBe 5
    }

    "RRA rotates memory right then ADCs accumulator" {
        state.pc = 0x8000
        state.a = 0x40
        state.c = true
        memory[0x8000] = 0x67
        memory[0x8001] = 0x20
        memory[0x0020] = 0x02

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x81
        state.a shouldBe 0xC1
        state.c shouldBe false
        state.v shouldBe false
        state.n shouldBe true
        cycles shouldBe 5
    }

    "DCP decrements memory then compares accumulator" {
        state.pc = 0x8000
        state.a = 0x42
        memory[0x8000] = 0xC7
        memory[0x8001] = 0x20
        memory[0x0020] = 0x43

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x42
        state.a shouldBe 0x42
        state.c shouldBe true
        state.z shouldBe true
        state.n shouldBe false
        cycles shouldBe 5
    }

    "ISC increments memory then SBCs accumulator" {
        state.pc = 0x8000
        state.a = 0x50
        state.c = true
        memory[0x8000] = 0xE7
        memory[0x8001] = 0x20
        memory[0x0020] = 0x0F

        val cycles = cpu.step()

        memory[0x0020] shouldBe 0x10
        state.a shouldBe 0x40
        state.c shouldBe true
        state.z shouldBe false
        state.n shouldBe false
        cycles shouldBe 5
    }

    fun setupZeroPage(value: Int) {
        memory[0x8001] = 0x20
        memory[0x0020] = value
    }

    fun setupZeroPageX(value: Int) {
        state.x = 0x10
        memory[0x8001] = 0x20
        memory[0x0030] = value
    }

    fun setupAbsolute(value: Int) {
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1234] = value
    }

    fun setupAbsoluteX(value: Int) {
        state.x = 0x01
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1235] = value
    }

    fun setupAbsoluteY(value: Int) {
        state.y = 0x01
        memory[0x8001] = 0x34
        memory[0x8002] = 0x12
        memory[0x1235] = value
    }

    fun setupIndirectX(value: Int) {
        state.x = 0x04
        memory[0x8001] = 0x20
        memory[0x0024] = 0x34
        memory[0x0025] = 0x12
        memory[0x1234] = value
    }

    fun setupIndirectY(value: Int) {
        state.y = 0x01
        memory[0x8001] = 0x20
        memory[0x0020] = 0x34
        memory[0x0021] = 0x12
        memory[0x1235] = value
    }

    "all unofficial RMW addressing modes have expected cycles" {
        listOf(
            RmwOpcode(0x03, 8) { setupIndirectX(it) },
            RmwOpcode(0x07, 5) { setupZeroPage(it) },
            RmwOpcode(0x0F, 6) { setupAbsolute(it) },
            RmwOpcode(0x13, 8) { setupIndirectY(it) },
            RmwOpcode(0x17, 6) { setupZeroPageX(it) },
            RmwOpcode(0x1B, 7) { setupAbsoluteY(it) },
            RmwOpcode(0x1F, 7) { setupAbsoluteX(it) },
            RmwOpcode(0x23, 8) { setupIndirectX(it) },
            RmwOpcode(0x27, 5) { setupZeroPage(it) },
            RmwOpcode(0x2F, 6) { setupAbsolute(it) },
            RmwOpcode(0x33, 8) { setupIndirectY(it) },
            RmwOpcode(0x37, 6) { setupZeroPageX(it) },
            RmwOpcode(0x3B, 7) { setupAbsoluteY(it) },
            RmwOpcode(0x3F, 7) { setupAbsoluteX(it) },
            RmwOpcode(0x43, 8) { setupIndirectX(it) },
            RmwOpcode(0x47, 5) { setupZeroPage(it) },
            RmwOpcode(0x4F, 6) { setupAbsolute(it) },
            RmwOpcode(0x53, 8) { setupIndirectY(it) },
            RmwOpcode(0x57, 6) { setupZeroPageX(it) },
            RmwOpcode(0x5B, 7) { setupAbsoluteY(it) },
            RmwOpcode(0x5F, 7) { setupAbsoluteX(it) },
            RmwOpcode(0x63, 8) { setupIndirectX(it) },
            RmwOpcode(0x67, 5) { setupZeroPage(it) },
            RmwOpcode(0x6F, 6) { setupAbsolute(it) },
            RmwOpcode(0x73, 8) { setupIndirectY(it) },
            RmwOpcode(0x77, 6) { setupZeroPageX(it) },
            RmwOpcode(0x7B, 7) { setupAbsoluteY(it) },
            RmwOpcode(0x7F, 7) { setupAbsoluteX(it) },
            RmwOpcode(0xC3, 8) { setupIndirectX(it) },
            RmwOpcode(0xC7, 5) { setupZeroPage(it) },
            RmwOpcode(0xCF, 6) { setupAbsolute(it) },
            RmwOpcode(0xD3, 8) { setupIndirectY(it) },
            RmwOpcode(0xD7, 6) { setupZeroPageX(it) },
            RmwOpcode(0xDB, 7) { setupAbsoluteY(it) },
            RmwOpcode(0xDF, 7) { setupAbsoluteX(it) },
            RmwOpcode(0xE3, 8) { setupIndirectX(it) },
            RmwOpcode(0xE7, 5) { setupZeroPage(it) },
            RmwOpcode(0xEF, 6) { setupAbsolute(it) },
            RmwOpcode(0xF3, 8) { setupIndirectY(it) },
            RmwOpcode(0xF7, 6) { setupZeroPageX(it) },
            RmwOpcode(0xFB, 7) { setupAbsoluteY(it) },
            RmwOpcode(0xFF, 7) { setupAbsoluteX(it) },
        ).forEach { case ->
            memory = IntArray(0x10_000)
            state = CpuState(pc = 0x8000, a = 0x40, status = 0x21)
            bus = FakeBus(memory = memory)
            cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = case.opcode
            case.setup(0x20)

            cpu.step() shouldBe case.cycles
        }
    }
})

private data class RmwOpcode(
    val opcode: Int,
    val cycles: Int,
    val setup: (Int) -> Unit,
)
