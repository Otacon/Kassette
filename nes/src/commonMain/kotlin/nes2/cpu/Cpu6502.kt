package nes2.cpu

import nes.util.isNegative8Bit
import nes.util.low16Bits
import nes.util.low8Bits
import nes2.CpuBus
import nes2.cpu.AddressingMode.*

class Cpu6502(
    private val bus: CpuBus,
    private var state: CpuState = CpuState(),
) {

    private val instructions = arrayOfNulls<Instruction>(256)

    init {
        // ADC
        instructions[0x69] = Instruction(operation = Operation.ADC, addressingMode = IMMEDIATE, baseCycles = 2)
        instructions[0x65] = Instruction(operation = Operation.ADC, addressingMode = ZERO_PAGE, baseCycles = 3)
        instructions[0x75] = Instruction(operation = Operation.ADC, addressingMode = ZERO_PAGE_X, baseCycles = 4)
        instructions[0x61] = Instruction(operation = Operation.ADC, addressingMode = INDIRECT_X, baseCycles = 6)
        instructions[0x6D] = Instruction(operation = Operation.ADC, addressingMode = ABSOLUTE, baseCycles = 4)
        instructions[0x71] = Instruction(operation = Operation.ADC, addressingMode = INDIRECT_Y, baseCycles = 5)
        instructions[0x79] = Instruction(operation = Operation.ADC, addressingMode = ABSOLUTE_Y, baseCycles = 4)
        instructions[0x7D] = Instruction(operation = Operation.ADC, addressingMode = ABSOLUTE_X, baseCycles = 4)
    }

    fun reset() {
        state.pc = bus.read(0xFFFC) or (bus.read(0xFFFD) shl 8)
    }

    fun step() {
        val opCode = pcRead()
        val instruction = instructions[opCode] ?: throw IllegalArgumentException("Instruction#$opCode not found")
        execute(instruction = instruction)
    }

    private fun execute(instruction: Instruction) {
        when (instruction.operation) {
            Operation.ADC -> {
                val operand = readOperand(instruction.addressingMode)
                adc(operand)
            }
        }
    }

    private fun readOperand(mode: AddressingMode): Int {
        return when (mode) {
            IMMEDIATE -> {
                pcRead()
            }

            ZERO_PAGE -> {
                val address = pcRead()
                bus.read(address)
            }

            ZERO_PAGE_X -> {
                val address = (pcRead() + state.x).low8Bits()
                bus.read(address)
            }

            ABSOLUTE -> {
                val lo = pcRead()
                val hi = pcRead()
                val address = lo or (hi shl 8)
                bus.read(address)
            }

            ABSOLUTE_X -> {
                val lo = pcRead()
                val hi = pcRead()
                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.x).low16Bits()
                bus.read(address)
            }

            ABSOLUTE_Y -> {
                val lo = pcRead()
                val hi = pcRead()
                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()
                bus.read(address)
            }

            INDIRECT_X -> {
                val pointer = (pcRead() + state.x).low8Bits()
                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())
                val address = lo or (hi shl 8)
                bus.read(address)
            }

            INDIRECT_Y -> {
                val pointer = pcRead()

                val lo = bus.read(pointer)
                val hi = bus.read((pointer + 1).low8Bits())

                val baseAddress = lo or (hi shl 8)
                val address = (baseAddress + state.y).low16Bits()

                bus.read(address)
            }

            IMPLIED -> TODO()
            ACCUMULATOR -> TODO()
            ZERO_PAGE_Y -> TODO()
            RELATIVE -> TODO()
            INDIRECT -> TODO()
        }
    }

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

    private fun pcRead(): Int {
        val value = bus.read(state.pc)
        state.pc = (state.pc + 1).low16Bits()
        return value
    }

}

