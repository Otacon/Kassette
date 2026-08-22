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

class Nina01(romData: RomData) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x1000
    override fun registerStartAddress(): Int = 0x7FFD
    override fun registerEndAddress(): Int = 0x7FFF

    override fun initMapper() {
        selectPrgPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        when (addr) {
            0x7FFD -> selectPrgPage(0, value)
            0x7FFE -> selectChrPage(0, value)
            0x7FFF -> selectChrPage(1, value)
        }
        writePrgRam(addr, value)
    }
}
