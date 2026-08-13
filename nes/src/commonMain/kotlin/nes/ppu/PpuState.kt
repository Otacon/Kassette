package nes.ppu

data class PpuBusState(
    var nametables: ByteArray = ByteArray(2048),
    var paletteRam: ByteArray = ByteArray(32),
) {
    override fun equals(other: Any?): Boolean = other is PpuBusState &&
        nametables.contentEquals(other.nametables) && paletteRam.contentEquals(other.paletteRam)

    override fun hashCode(): Int = 31 * nametables.contentHashCode() + paletteRam.contentHashCode()
}

data class PpuState(
    var frameColorIds: Array<ByteArray> = Array(2) { ByteArray(256 * 240) },
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
    var secondaryOam: ByteArray = ByteArray(32) { 0xFF.toByte() },
    var activeSpriteX: IntArray = IntArray(8),
    var activeSpriteAttributes: IntArray = IntArray(8),
    var activeSpriteLow: IntArray = IntArray(8),
    var activeSpriteHigh: IntArray = IntArray(8),
    var fetchedSpriteX: IntArray = IntArray(8),
    var fetchedSpriteAttributes: IntArray = IntArray(8),
    var fetchedSpriteLow: IntArray = IntArray(8),
    var fetchedSpriteHigh: IntArray = IntArray(8),
    var counters: IntArray = IntArray(25),
    var flags: BooleanArray = BooleanArray(7),
) {
    override fun equals(other: Any?): Boolean = other is PpuState &&
        frameColorIds.contentDeepEquals(other.frameColorIds) &&
        oam.contentEquals(other.oam) &&
        secondaryOam.contentEquals(other.secondaryOam) &&
        activeSpriteX.contentEquals(other.activeSpriteX) &&
        activeSpriteAttributes.contentEquals(other.activeSpriteAttributes) &&
        activeSpriteLow.contentEquals(other.activeSpriteLow) &&
        activeSpriteHigh.contentEquals(other.activeSpriteHigh) &&
        fetchedSpriteX.contentEquals(other.fetchedSpriteX) &&
        fetchedSpriteAttributes.contentEquals(other.fetchedSpriteAttributes) &&
        fetchedSpriteLow.contentEquals(other.fetchedSpriteLow) &&
        fetchedSpriteHigh.contentEquals(other.fetchedSpriteHigh) &&
        counters.contentEquals(other.counters) && flags.contentEquals(other.flags)

    override fun hashCode(): Int = frameColorIds.contentDeepHashCode()
}
