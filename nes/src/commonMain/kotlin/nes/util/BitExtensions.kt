package nes.util

/**
 * Returns this byte as an unsigned 8-bit integer in the range `0..255`.
 *
 * Example: `0xFF.toByte().toUnsignedInt()` returns `255` instead of `-1`.
 */
fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

/**
 * Truncates this integer to the low 8 bits used by byte-sized NES registers and buses.
 *
 * Example: `(0x1234).low8Bits()` returns `0x34`.
 */
fun Int.low8Bits(): Int = this and 0xFF

/**
 * Truncates this integer to the low 16 bits used by CPU addresses.
 *
 * Example: `(0x1_8000).low16Bits()` returns `0x8000`.
 */
fun Int.low16Bits(): Int = this and 0xFFFF

/**
 * Returns the 16-bit address page base, used for 6502 page-crossing checks.
 *
 * Example: `(0x12FF).pageBase()` returns `0x1200`.
 */
fun Int.pageBase(): Int = this and 0xFF00

fun Int.isNegative8Bit(): Boolean = (this and 0x80) != 0
