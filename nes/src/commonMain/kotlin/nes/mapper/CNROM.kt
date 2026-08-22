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
