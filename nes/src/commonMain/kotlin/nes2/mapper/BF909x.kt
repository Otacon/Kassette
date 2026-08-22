package nes2.mapper

class BF909x(romData: RomData) : BaseMapper(romData) {
    private var bf9097Mode = false

    override fun getPrgPageSize(): Int = 0x4000
    override fun getChrPageSize(): Int = 0x2000

    override fun initMapper() {
        if (romInfoState.subMapperID == 1) bf9097Mode = true
        selectPrgPage(0, 0)
        selectPrgPage(1, -1)
        selectChrPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        if (addr == 0x9000) bf9097Mode = true
        if (addr >= 0xC000 || !bf9097Mode) {
            selectPrgPage(0, value)
        } else if (addr < 0xC000) {
            setMirroringType(if ((value and 0x10) != 0) MirroringType.ScreenAOnly else MirroringType.ScreenBOnly)
        }
    }
}
