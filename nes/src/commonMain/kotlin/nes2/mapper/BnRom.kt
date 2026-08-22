package nes2.mapper

class BnRom(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000
    override fun hasBusConflicts(): Boolean = true

    override fun initMapper() {
        selectPrgPage(0, getPowerOnByte())
        selectChrPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        selectPrgPage(0, value)
    }
}
