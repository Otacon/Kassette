package platform

import nes.cartridge.RomData

expect class FileChooser {
    suspend fun pickRom(): RomData?
}
