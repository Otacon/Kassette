package nes2.cpu

data class CpuState(
    var pc: Int = 0,
    var a: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var sp: Int = 0,
    var status: Int = 0,
    var irqLine: Boolean = false,
    var nmiPending: Boolean = false,
    var irqPollI: Boolean = true,
) {

    var c: Boolean
        get() = getFlag(C)
        set(value) = setFlag(C, value)

    var z: Boolean
        get() = getFlag(Z)
        set(value) = setFlag(Z, value)

    var i: Boolean
        get() = getFlag(I)
        set(value) = setFlag(I, value)

    var d: Boolean
        get() = getFlag(D)
        set(value) = setFlag(D, value)

    var v: Boolean
        get() = getFlag(V)
        set(value) = setFlag(V, value)

    var n: Boolean
        get() = getFlag(N)
        set(value) = setFlag(N, value)

    private fun getFlag(flag: Int): Boolean =
        (status and flag) != 0

    private fun setFlag(flag: Int, enabled: Boolean) {
        status = if (enabled) {
            status or flag
        } else {
            status and flag.inv()
        }

        status = (status or U) and B.inv()
    }

    private companion object {
        const val C = 1 shl 0 // Carry
        const val Z = 1 shl 1 // Zero
        const val I = 1 shl 2 // Interrupt Disable
        const val D = 1 shl 3 // Decimal
        const val B = 1 shl 4 // Break
        const val U = 1 shl 5 // Unused
        const val V = 1 shl 6 // Overflow
        const val N = 1 shl 7 // Negative
    }
}