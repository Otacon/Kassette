package nes2.mapper

class ColorDreams(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000
    override fun hasBusConflicts(): Boolean = true

    override fun initMapper() {
        selectPrgPage(0, 0)
        selectChrPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        var v = value
        if (romInfoState.mapperID == 144) {
            v = v or (readRam(addr) and 0x01)
        }
        selectPrgPage(0, v and 0x0F)
        selectChrPage(0, (v shr 4) and 0x0F)
    }
}
