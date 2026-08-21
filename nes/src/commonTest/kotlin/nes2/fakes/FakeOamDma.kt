package nes2.fakes

import nes2.OamDma

class FakeOamDma : OamDma {

    override var cpuBusRead: (Int) -> Int = { 0 }

    override var isActive = false
    var transfers = 0
    var page: Int = 0

    override fun reset() {
        isActive = false
        transfers = 0
        page = 0
    }

    override fun start(page: Int) {
        this.page = page and 0xFF
        isActive = true
    }

    override fun transfer() {
        transfers++
        isActive = false
    }
}
