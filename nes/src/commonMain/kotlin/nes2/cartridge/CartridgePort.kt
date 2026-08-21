package nes2.cartridge

import nes.cartridge.Cartridge
import nes.cartridge.Mapper
import nes.cartridge.Mirroring

interface CartridgePort {

    val mirroring: Mirroring

    fun insert(cartridge: Cartridge)

    fun cpuRead(address: Int): Int

    fun cpuRead(address: Int, openBus: Int): Int

    fun cpuWrite(address: Int, value: Int)

    fun ppuRead(address: Int): Int

    fun ppuWrite(address: Int, value: Int)

    fun clockScanline()

    fun irqPending(): Boolean
}

class CartridgePortNes : CartridgePort {
    private var cartridge: Cartridge? = null
    private var mapper: Mapper? = null

    override var mirroring: Mirroring = Mirroring.VERTICAL
        private set


    override fun insert(cartridge: Cartridge) {
        this.cartridge = cartridge
        mapper = cartridge.mapper
        mirroring = mapper?.mirroring() ?: cartridge.mirroring
    }

    fun remove() {
        cartridge = null
        mapper = null
        mirroring = Mirroring.VERTICAL
    }

    fun reset() {
        val inserted = cartridge ?: return
        val activeMapper = mapper ?: return
        activeMapper.reset()
        mirroring = activeMapper.mirroring() ?: inserted.mirroring
    }

    override fun cpuRead(address: Int): Int {
        return mapper?.cpuRead(address) ?: 0
    }

    override fun cpuRead(address: Int, openBus: Int): Int {
        return mapper?.cpuRead(address, openBus) ?: 0
    }

    override fun cpuWrite(address: Int, value: Int) {
        val inserted = cartridge ?: return
        val activeMapper = mapper ?: return
        activeMapper.cpuWrite(address, value)
        mirroring = activeMapper.mirroring() ?: inserted.mirroring
    }

    override fun ppuRead(address: Int): Int {
        return mapper?.ppuRead(address) ?: 0
    }

    override fun ppuWrite(address: Int, value: Int) {
        mapper?.ppuWrite(address, value)
    }

    override fun clockScanline() {
        mapper?.clockScanline()
    }

    override fun irqPending(): Boolean {
        return mapper?.irqPending() ?: false
    }
}
