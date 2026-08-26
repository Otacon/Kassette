package nes

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class NesInstructionRomTest : FreeSpec({

    "Blargg instr_test-v5 - all instructions" {
        val console = createConsole(resourceName = "/all_instrs.nes")

        val result = runBlarggTest(
            console = console,
            maxFrames = 20_000,
        )

        result.status shouldBe 0
    }
})
