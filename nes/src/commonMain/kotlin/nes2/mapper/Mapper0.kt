package nes2.mapper

class Mapper0(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x4000
    override fun getChrPageSize(): Int = 0x2000

    override fun initMapper() {
        selectPrgPage(0, 0)
        selectPrgPage(1, 1)
        selectChrPage(0, 0)
    }
}

typealias NROM = Mapper0
