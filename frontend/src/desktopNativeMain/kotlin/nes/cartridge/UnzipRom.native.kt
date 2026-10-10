package nes.cartridge

actual suspend fun unzipRom(zipData: RomData): UnzipRomResult = UnzipRomResult.UnknownError
