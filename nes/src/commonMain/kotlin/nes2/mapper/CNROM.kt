package nes2.mapper

class CNROM(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000
    override fun hasBusConflicts(): Boolean = romInfoState.subMapperID == 2

    override fun initMapper() {
        selectPrgPage(0, 0)
        selectChrPage(0, getPowerOnByte())
    }

    override fun writeRegister(addr: Int, value: Int) {
        selectChrPage(0, value)
    }
}
