package nes.cartridge

interface InesParser {
    suspend fun parse(romData: RomData): InesParseResult
}

sealed interface InesParseResult {
    data class Success(val cartridge: Cartridge) : InesParseResult
    data object InvalidRom : InesParseResult
    data object UnknownError : InesParseResult
}

data class RomData(
    val name: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RomData

        if (name != other.name) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

sealed interface UnzipRomResult {
    data class Success(val romData: RomData) : UnzipRomResult
    data object NotFound : UnzipRomResult
    data object MultipleRoms : UnzipRomResult
    data object UnknownError : UnzipRomResult
}

expect suspend fun unzipRom(zipData: RomData): UnzipRomResult
