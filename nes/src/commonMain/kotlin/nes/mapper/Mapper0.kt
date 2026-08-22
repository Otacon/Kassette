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
