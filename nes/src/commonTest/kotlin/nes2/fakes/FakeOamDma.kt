package nes2.fakes

import nes2.OamDma

class FakeOamDma : OamDma {

    override var page = 0
        private set

    override var active = false
        private set

    var transfers = 0
        private set

    override fun start(page: Int) {
        this.page = page and 0xFF
        active = true
    }

    override fun transfer() {
        transfers++
        active = false
    }
}