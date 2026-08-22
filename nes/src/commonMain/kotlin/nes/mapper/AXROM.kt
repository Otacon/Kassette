/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.mapper

class AXROM(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000
    override fun hasBusConflicts(): Boolean = romInfoState.subMapperID == 2

    override fun initMapper() {
        selectChrPage(0, 0)
        writeRegister(0, getPowerOnByte())
    }

    override fun writeRegister(addr: Int, value: Int) {
        selectPrgPage(0, value and 0x0F)
        setMirroringType(if ((value and 0x10) == 0x10) MirroringType.ScreenBOnly else MirroringType.ScreenAOnly)
    }
}
