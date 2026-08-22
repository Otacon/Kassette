package nes2.mapper

fun createMapper(romData: RomData): BaseMapper = when (romData.info.mapperID) {
    0 -> Mapper0(romData)
    1 -> MMC1(romData)
    2 -> UNROM(romData)
    3 -> CNROM(romData)
    4 -> MMC3(romData)
    7 -> AXROM(romData)
    11 -> ColorDreams(romData)
    34 -> when (romData.info.subMapperID) {
        1 -> Nina01(romData)
        2 -> BnRom(romData)
        else -> if (romData.chrRom.isNotEmpty()) Nina01(romData) else BnRom(romData)
    }
    66 -> GxRom(romData)
    71 -> BF909x(romData)
    79 -> Nina03_06(romData, multicartMode = false)
    87 -> JalecoJfxx(romData, orderedBits = false)
    113 -> Nina03_06(romData, multicartMode = true)
    144 -> ColorDreams(romData)
    else -> error("Unsupported NES mapper ${romData.info.mapperID}")
}
