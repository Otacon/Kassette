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

class GxRom(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000

    override fun initMapper() {
        selectPrgPage(0, getPowerOnByte() and 0x03)
        selectChrPage(0, getPowerOnByte() and 0x03)
    }

    override fun writeRegister(addr: Int, value: Int) {
        selectPrgPage(0, (value shr 4) and 0x03)
        selectChrPage(0, value and 0x03)
    }
}
