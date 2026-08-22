package nes2.mapper

class Nina03_06(romData: RomData, private val multicartMode: Boolean) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000
    override fun registerStartAddress(): Int = 0x4100
    override fun registerEndAddress(): Int = 0x5FFF

    override fun initMapper() {
        selectPrgPage(0, 0)
        selectChrPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        if ((addr and 0xE100) != 0x4100) return
        if (multicartMode) {
            selectPrgPage(0, (value shr 3) and 0x07)
            selectChrPage(0, (value and 0x07) or ((value shr 3) and 0x08))
            setMirroringType(if ((value and 0x80) == 0x80) MirroringType.Vertical else MirroringType.Horizontal)
        } else {
            selectPrgPage(0, (value shr 3) and 0x01)
            selectChrPage(0, value and 0x07)
        }
    }
}
