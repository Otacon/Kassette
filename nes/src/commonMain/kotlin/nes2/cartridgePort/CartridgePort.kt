package nes2.cartridgePort

import nes.ConsoleRegion
import nes.cartridge.Cartridge
import nes.cartridge.Mapper
import nes.cartridge.MapperState
import nes.cartridge.Mirroring

interface CartridgePort {

    fun ppuRead(address: Int): Int

    fun ppuWrite(address: Int, value: Int)
}

class CartridgePortNes : CartridgePort {
    private var cartridge: Cartridge? = null
    private var mapper: Mapper? = null

    var mirroring: Mirroring? = null
        private set

    var region: ConsoleRegion = ConsoleRegion.NTSC
        private set

    fun insert(cartridge: Cartridge) {
        this.cartridge = cartridge
        mapper = cartridge.mapper
        mirroring = mapper?.mirroring() ?: cartridge.mirroring
        region = cartridge.region
    }

    fun remove() {
        cartridge = null
        mapper = null
        mirroring = null
        region = ConsoleRegion.NTSC
    }

    fun reset() {
        val inserted = cartridge ?: return
        val activeMapper = mapper ?: return
        activeMapper.reset()
        mirroring = activeMapper.mirroring() ?: inserted.mirroring
    }

    fun cpuRead(address: Int): Int {
        return mapper?.cpuRead(address) ?: 0
    }

    fun cpuRead(address: Int, openBus: Int): Int {
        return mapper?.cpuRead(address, openBus) ?: 0
    }

    fun cpuWrite(address: Int, value: Int) {
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

    fun clockScanline() {
        mapper?.clockScanline()
    }

    fun irqPending(): Boolean {
        return mapper?.irqPending() ?: false
    }

    fun captureMapperState(): MapperState = requireNotNull(mapper).captureState()

    fun restoreMapperState(state: MapperState) {
        val inserted = cartridge ?: return
        val activeMapper = mapper ?: return
        activeMapper.restoreState(state)
        mirroring = activeMapper.mirroring() ?: inserted.mirroring
    }
}
