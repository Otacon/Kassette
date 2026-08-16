package nes2.cpu

data class Instruction(
    val operation: Operation,
    val addressingMode: AddressingMode,
    val baseCycles: Int
)