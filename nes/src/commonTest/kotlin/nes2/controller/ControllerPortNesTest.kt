package nes2.controller

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ControllerPortNesTest : FreeSpec({

    lateinit var controller: ControllerPortNes

    beforeTest {
        controller = ControllerPortNes()
    }

    "reads latched buttons serially in NES button order" {
        controller.update(0b1010_0101)

        controller.write(1)
        controller.write(0)

        controller.read() shouldBe 1 // A
        controller.read() shouldBe 0 // B
        controller.read() shouldBe 1 // Select
        controller.read() shouldBe 0 // Start
        controller.read() shouldBe 0 // Up
        controller.read() shouldBe 1 // Down
        controller.read() shouldBe 0 // Left
        controller.read() shouldBe 1 // Right
    }

    "changing controller state does not affect an already latched read" {
        controller.update(0b0000_0001)

        controller.write(1)
        controller.write(0)

        controller.update(0b0000_0010)

        controller.read() shouldBe 1 // latched A
        controller.read() shouldBe 0 // latched B
    }

    "returns one after all buttons have been shifted out" {
        controller.update(0)

        controller.write(1)
        controller.write(0)

        repeat(8) {
            controller.read()
        }

        controller.read() shouldBe 1
        controller.read() shouldBe 1
    }

    "reads the latched controller state serially" {
        controller.update(0b1010_0101)

        controller.write(1)
        controller.write(0)

        listOf(
            1, // A
            0, // B
            1, // Select
            0, // Start
            0, // Up
            1, // Down
            0, // Left
            1, // Right
        ).forEach {
            controller.read() shouldBe it
        }
    }

    "controller updates do not modify an already latched state" {
        controller.update(0b0000_0001)

        controller.write(1)
        controller.write(0)

        controller.update(0b0000_0010)

        controller.read() shouldBe 1 // old A
        controller.read() shouldBe 0 // old B
    }

    "reads return one after all eight buttons have been consumed" {
        controller.update(0)

        controller.write(1)
        controller.write(0)

        repeat(8) {
            controller.read()
        }

        repeat(4) {
            controller.read() shouldBe 1
        }
    }

    "while strobed reads current A button without shifting" {
        controller.update(0b0000_0001)
        controller.write(1)

        controller.read() shouldBe 1
        controller.read() shouldBe 1
        controller.read() shouldBe 1

        controller.update(0b0000_0000)

        controller.read() shouldBe 0
    }

    "only bit zero controls the strobe" {
        controller.update(0b0000_0011)

        controller.write(0xFF)
        controller.write(0xFE)

        controller.read() shouldBe 1 // A
        controller.read() shouldBe 1 // B
    }

    "preserves raw opposite directions" {
        controller.update(0b1111_0000)

        controller.write(1)
        controller.write(0)

        repeat(4) { controller.read() }
        controller.read() shouldBe 1 // Up
        controller.read() shouldBe 1 // Down
        controller.read() shouldBe 1 // Left
        controller.read() shouldBe 1 // Right
    }

    "reset clears serial state without clearing live buttons" {
        controller.update(0b0000_0011)
        controller.write(1)
        controller.write(0)
        controller.read() shouldBe 1

        controller.reset()

        controller.read() shouldBe 0
        controller.write(1)
        controller.read() shouldBe 1
    }
})
