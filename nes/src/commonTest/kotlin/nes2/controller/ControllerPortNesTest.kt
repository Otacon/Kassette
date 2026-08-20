package nes2.controller

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ControllerPortNesTest : FreeSpec({

    lateinit var controller: ControllerPortNes

    beforeEach {
        controller = ControllerPortNes()
    }

    "reads latched buttons serially in NES button order" {
        controller.update(0b1010_0101)

        controller.write(1)
        controller.write(0)

        controller.read() shouldBe 0x41 // A      = 1
        controller.read() shouldBe 0x40 // B      = 0
        controller.read() shouldBe 0x41 // Select = 1
        controller.read() shouldBe 0x40 // Start  = 0
        controller.read() shouldBe 0x40 // Up     = 0
        controller.read() shouldBe 0x41 // Down   = 1
        controller.read() shouldBe 0x40 // Left   = 0
        controller.read() shouldBe 0x41 // Right  = 1
    }

    "changing controller state does not affect an already latched read" {
        controller.update(0b0000_0001)

        controller.write(1)
        controller.write(0)

        controller.update(0b0000_0010)

        controller.read() shouldBe 0x41 // latched A
        controller.read() shouldBe 0x40 // latched B
    }

    "returns one after all buttons have been shifted out" {
        controller.update(0)

        controller.write(1)
        controller.write(0)

        repeat(8) {
            controller.read()
        }

        controller.read() shouldBe 0x41
        controller.read() shouldBe 0x41
    }

    "reads the latched controller state serially" {
        controller.update(0b1010_0101)

        controller.write(1)
        controller.write(0)

        listOf(
            0x41, // A
            0x40, // B
            0x41, // Select
            0x40, // Start
            0x40, // Up
            0x41, // Down
            0x40, // Left
            0x41, // Right
        ).forEach {
            controller.read() shouldBe it
        }
    }

    "controller updates do not modify an already latched state" {
        controller.update(0b0000_0001)

        controller.write(1)
        controller.write(0)

        controller.update(0b0000_0010)

        controller.read() shouldBe 0x41 // old A
        controller.read() shouldBe 0x40 // old B
    }

    "reads return one after all eight buttons have been consumed" {
        controller.update(0)

        controller.write(1)
        controller.write(0)

        repeat(8) {
            controller.read()
        }

        repeat(4) {
            controller.read() shouldBe 0x41
        }
    }

    "while strobed reads current A button without shifting" {
        controller.update(0b0000_0001)
        controller.write(1)

        controller.read() shouldBe 0x41
        controller.read() shouldBe 0x41
        controller.read() shouldBe 0x41

        controller.update(0b0000_0000)

        controller.read() shouldBe 0x40
    }

    "only bit zero controls the strobe" {
        controller.update(0b0000_0011)

        controller.write(0xFF)
        controller.write(0xFE)

        controller.read() shouldBe 0x41 // A
        controller.read() shouldBe 0x41 // B
    }
})