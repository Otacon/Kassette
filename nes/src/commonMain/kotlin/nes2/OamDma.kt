package nes2

interface OamDma {
    val page: Int
    val active: Boolean
    fun start(page: Int)
    fun stop()
}

class OamDmaNes : OamDma {
    override var active = false
        private set

    override var page = 0
        private set

    override fun start(page: Int) {
        this.page = page and 0xFF
        active = true
    }

    override fun stop() {
        active = false
    }
}