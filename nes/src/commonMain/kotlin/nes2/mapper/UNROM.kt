package nes2.mapper

class UNROM(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x4000
    override fun getChrPageSize(): Int = 0x2000
    override fun hasBusConflicts(): Boolean = romInfoState.subMapperID == 2

    override fun initMapper() {
        selectPrgPage(0, 0)
        selectPrgPage(1, -1)
        selectChrPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        selectPrgPage(0, value)
    }
}
