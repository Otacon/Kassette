package nes2

interface ControllerPort {
    fun read(): Int
    fun write(value: Int)
}