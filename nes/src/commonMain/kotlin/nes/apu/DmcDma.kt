package nes.apu

/** Coordinates an asynchronous DMC sample request with CPU-bus DMA arbitration. */
class DmcDma {
    private var address = NO_ADDRESS
    private var result = NO_RESULT
    private var phase = IDLE

    /** Queues a sample address if no request or unread result is already active. */
    fun request(address: Int): Boolean {
        if (phase == IDLE && result == NO_RESULT) {
            this.address = address
            phase = HALT
            return true
        }
        return false
    }

    /** Returns whether the CPU still needs to execute cycles for this request. */
    fun pending(): Boolean = phase != IDLE

    /** Returns whether halt and dummy cycles are complete and the sample can be read. */
    fun readyToRead(): Boolean = phase == GET

    /** Cancels a request that has not reached its halt cycle yet. */
    fun cancelBeforeHalt(): Boolean {
        if (phase != HALT) return false
        address = NO_ADDRESS
        phase = IDLE
        return true
    }

    /** Returns the sample address currently requested by the DMC reader. */
    fun requestedAddress(): Int = address

    /** Advances the halt/dummy pipeline at the start of one DMA-owned cycle. */
    fun beginCycle() {
        phase = when (phase) {
            HALT -> DUMMY
            DUMMY -> GET
            else -> phase
        }
    }

    /** Completes the get cycle and leaves the result for the APU to consume. */
    fun complete(value: Int) {
        result = value and 0xFF
        address = NO_ADDRESS
        phase = IDLE
    }

    /** Takes a completed sample, or returns `-1` while DMA is still pending. */
    fun takeResult(): Int {
        if (result == NO_RESULT) return NO_RESULT
        val value = result
        result = NO_RESULT
        return value
    }

    /** Cancels pending and completed DMA state during machine reset. */
    fun reset() {
        address = NO_ADDRESS
        result = NO_RESULT
        phase = IDLE
    }

    private companion object {
        const val NO_ADDRESS = -1
        const val NO_RESULT = -1
        const val IDLE = 0
        const val HALT = 1
        const val DUMMY = 2
        const val GET = 3
    }
}
