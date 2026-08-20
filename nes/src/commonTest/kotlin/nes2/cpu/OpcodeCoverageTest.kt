package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class OpcodeCoverageTest : FreeSpec({
    "all opcodes are explicitly implemented or intentional KIL opcodes" {
        val kilOpcodes = setOf(
            0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72,
            0x92, 0xB2, 0xD2, 0xF2,
        )
        val unhandledOpcodes = mutableListOf<Int>()

        for (opcode in 0x00..0xFF) {
            val memory = IntArray(0x10_000)
            val state = CpuState(pc = 0x8000, sp = 0xFD, status = 0x24)
            val bus = FakeBus(memory = memory)
            val cpu = Cpu6502(bus = bus, state = state)

            memory[0x8000] = opcode

            try {
                cpu.step()
            } catch (exception: IllegalArgumentException) {
                if (exception.message == "Unhandled opcode") {
                    unhandledOpcodes += opcode
                } else {
                    throw exception
                }
            }

            state.halted shouldBe (opcode in kilOpcodes)
        }

        unhandledOpcodes shouldBe emptyList()
    }
})
