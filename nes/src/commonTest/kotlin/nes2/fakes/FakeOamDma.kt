package nes2.fakes

import nes2.OamDma

class FakeOamDma : OamDma {
    override var page: Int = 0
        private set

    override var active: Boolean = false
        private set

    override fun start(page: Int) {
        this.page = page and 0xFF
        active = true
    }

    override fun stop() {
        active = false
    }

}