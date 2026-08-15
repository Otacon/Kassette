package nes.ppu

data class PpuBusState(
    var nametables: ByteArray = ByteArray(4096),
    var paletteRam: ByteArray = ByteArray(32),
) {
    override fun equals(other: Any?): Boolean = other is PpuBusState &&
        nametables.contentEquals(other.nametables) && paletteRam.contentEquals(other.paletteRam)

    override fun hashCode(): Int = 31 * nametables.contentHashCode() + paletteRam.contentHashCode()
}

data class PpuState(
    var frameColorIds: Array<ByteArray> = Array(2) { ByteArray(256 * 240 * 4) },
    var renderFramebufferIndex: Int = 0,
    var completedFramebufferIndex: Int = 0,
    var oam: ByteArray = ByteArray(256),
    var ctrl: Int = 0,
    var mask: Int = 0,
    var status: Int = 0,
    var oamAddress: Int = 0,
    var v: Int = 0,
    var t: Int = 0,
    var fineX: Int = 0,
    var writeLatch: Boolean = false,
    var scanline: Int = -1,
    var cycle: Int = 340,
    var frameComplete: Boolean = false,
    var nmiRequested: Boolean = false,
    var nmiLine: Boolean = false,
    var ppuCycle: Long = 0,
    var openBusDecayStamps: IntArray = IntArray(8),
    var secondaryOam: ByteArray = ByteArray(32) { 0xFF.toByte() },
    var activeSpriteX: IntArray = IntArray(8),
    var activeSpriteAttributes: IntArray = IntArray(8),
    var activeSpriteLow: IntArray = IntArray(8),
    var activeSpriteHigh: IntArray = IntArray(8),
    var fetchedSpriteX: IntArray = IntArray(8),
    var fetchedSpriteAttributes: IntArray = IntArray(8),
    var fetchedSpriteLow: IntArray = IntArray(8),
    var fetchedSpriteHigh: IntArray = IntArray(8),
    var counters: IntArray = IntArray(28),
    var flags: BooleanArray = BooleanArray(9),
) {
    override fun equals(other: Any?): Boolean = other is PpuState &&
        renderFramebufferIndex == other.renderFramebufferIndex &&
        completedFramebufferIndex == other.completedFramebufferIndex &&
        ctrl == other.ctrl && mask == other.mask && status == other.status &&
        oamAddress == other.oamAddress && v == other.v && t == other.t && fineX == other.fineX &&
        writeLatch == other.writeLatch && scanline == other.scanline && cycle == other.cycle &&
        frameComplete == other.frameComplete && nmiRequested == other.nmiRequested && nmiLine == other.nmiLine &&
        ppuCycle == other.ppuCycle &&
        frameColorIds.contentDeepEquals(other.frameColorIds) &&
        oam.contentEquals(other.oam) &&
        secondaryOam.contentEquals(other.secondaryOam) &&
        activeSpriteX.contentEquals(other.activeSpriteX) &&
        activeSpriteAttributes.contentEquals(other.activeSpriteAttributes) &&
        activeSpriteLow.contentEquals(other.activeSpriteLow) &&
        activeSpriteHigh.contentEquals(other.activeSpriteHigh) &&
        openBusDecayStamps.contentEquals(other.openBusDecayStamps) &&
        fetchedSpriteX.contentEquals(other.fetchedSpriteX) &&
        fetchedSpriteAttributes.contentEquals(other.fetchedSpriteAttributes) &&
        fetchedSpriteLow.contentEquals(other.fetchedSpriteLow) &&
        fetchedSpriteHigh.contentEquals(other.fetchedSpriteHigh) &&
        counters.contentEquals(other.counters) && flags.contentEquals(other.flags)

    override fun hashCode(): Int {
        var result = frameColorIds.contentDeepHashCode()
        result = 31 * result + renderFramebufferIndex
        result = 31 * result + completedFramebufferIndex
        result = 31 * result + oam.contentHashCode()
        result = 31 * result + ctrl
        result = 31 * result + mask
        result = 31 * result + status
        result = 31 * result + oamAddress
        result = 31 * result + v
        result = 31 * result + t
        result = 31 * result + fineX
        result = 31 * result + writeLatch.hashCode()
        result = 31 * result + scanline
        result = 31 * result + cycle
        result = 31 * result + frameComplete.hashCode()
        result = 31 * result + nmiRequested.hashCode()
        result = 31 * result + nmiLine.hashCode()
        result = 31 * result + ppuCycle.hashCode()
        result = 31 * result + openBusDecayStamps.contentHashCode()
        result = 31 * result + secondaryOam.contentHashCode()
        result = 31 * result + counters.contentHashCode()
        result = 31 * result + flags.contentHashCode()
        return result
    }
}
