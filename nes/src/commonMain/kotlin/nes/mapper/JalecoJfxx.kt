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

class JalecoJfxx(romData: RomData, private val orderedBits: Boolean) : BaseMapper(romData) {
    override fun getPrgPageSize(): Int = 0x8000
    override fun getChrPageSize(): Int = 0x2000
    override fun registerStartAddress(): Int = 0x6000
    override fun registerEndAddress(): Int = 0x7FFF

    override fun initMapper() {
        selectPrgPage(0, 0)
        selectChrPage(0, 0)
    }

    override fun writeRegister(addr: Int, value: Int) {
        selectChrPage(0, if (orderedBits) value else ((value and 0x01) shl 1) or ((value and 0x02) shr 1))
    }
}
