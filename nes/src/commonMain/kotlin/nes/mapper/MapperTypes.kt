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

import kotlinx.serialization.Serializable

@Serializable
enum class PrgMemoryType {
    PrgRom,
    SaveRam,
    WorkRam,
    MapperRam,
}

@Serializable
enum class ChrMemoryType {
    Default,
    ChrRom,
    ChrRam,
    NametableRam,
    MapperRam,
}

object MemoryAccessType {
    const val Unspecified = -1
    const val NoAccess = 0x00
    const val Read = 0x01
    const val Write = 0x02
    const val ReadWrite = 0x03
}

@Serializable
enum class MirroringType {
    Horizontal,
    Vertical,
    ScreenAOnly,
    ScreenBOnly,
    FourScreens,
}

enum class BusConflictType {
    Default,
    Yes,
    No,
}

enum class GameSystem {
    Unknown,
    NesNtsc,
    NesPal,
    Dendy,
    VsSystem,
}

enum class GameInputType {
    Unspecified,
}

enum class PpuModel {
    Ppu2C02,
    Ppu2C05A,
    Ppu2C05B,
    Ppu2C05C,
    Ppu2C05D,
    Ppu2C05E,
}

enum class VsSystemType {
    Default,
    VsDualSystem,
}

enum class RomFormat {
    Unknown,
    INes,
    Fds,
    Nsf,
    Nsfe,
    Unif,
    StudyBox,
}

enum class RomHeaderVersion {
    iNes,
    Nes20,
    OldiNes,
}

data class NesHeader(
    val version: RomHeaderVersion = RomHeaderVersion.iNes,
)

data class HashInfo(
    val crc: UInt = 0u,
)

data class GameInfo(
    val crc: UInt = 0u,
    val system: String = "",
    val board: String = "",
    val pcb: String = "",
    val chip: String = "",
    val mapperID: Int = 0,
    val prgRomSize: Int = 0,
    val chrRomSize: Int = 0,
    val chrRamSize: Int = 0,
    val workRamSize: Int = 0,
    val saveRamSize: Int = 0,
    val hasBattery: Boolean = false,
    val mirroring: String = "",
    val inputType: GameInputType = GameInputType.Unspecified,
    val busConflicts: String = "",
    val submapperID: String = "",
    val vsType: VsSystemType = VsSystemType.Default,
    val vsPpuModel: PpuModel = PpuModel.Ppu2C02,
)

data class NsfHeader(
    val version: Int = 0,
)

data class NesRomInfo(
    val romName: String = "",
    val filename: String = "",
    val format: RomFormat = RomFormat.Unknown,
    val isNes20Header: Boolean = false,
    val isInDatabase: Boolean = false,
    val isHeaderlessRom: Boolean = false,
    val filePrgOffset: Int = 0,
    val mapperID: Int = 0,
    val subMapperID: Int = 0,
    val system: GameSystem = GameSystem.Unknown,
    val vsType: VsSystemType = VsSystemType.Default,
    val inputType: GameInputType = GameInputType.Unspecified,
    val vsPpuModel: PpuModel = PpuModel.Ppu2C02,
    val hasChrRam: Boolean = false,
    val hasBattery: Boolean = false,
    val hasEpsm: Boolean = false,
    val hasTrainer: Boolean = false,
    val mirroring: MirroringType = MirroringType.Horizontal,
    val busConflicts: BusConflictType = BusConflictType.Default,
    val hash: HashInfo = HashInfo(),
    val header: NesHeader = NesHeader(),
    val nsfInfo: NsfHeader = NsfHeader(),
    val databaseInfo: GameInfo = GameInfo(),
)

data class RomData(
    val info: NesRomInfo = NesRomInfo(),
    val chrRamSize: Int = -1,
    val saveChrRamSize: Int = -1,
    val saveRamSize: Int = -1,
    val workRamSize: Int = -1,
    val prgRom: ByteArray = ByteArray(0),
    val chrRom: ByteArray = ByteArray(0),
    val trainerData: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean = other is RomData &&
        info == other.info &&
        chrRamSize == other.chrRamSize &&
        saveChrRamSize == other.saveChrRamSize &&
        saveRamSize == other.saveRamSize &&
        workRamSize == other.workRamSize &&
        prgRom.contentEquals(other.prgRom) &&
        chrRom.contentEquals(other.chrRom) &&
        trainerData.contentEquals(other.trainerData)

    override fun hashCode(): Int {
        var result = info.hashCode()
        result = 31 * result + chrRamSize
        result = 31 * result + saveChrRamSize
        result = 31 * result + saveRamSize
        result = 31 * result + workRamSize
        result = 31 * result + prgRom.contentHashCode()
        result = 31 * result + chrRom.contentHashCode()
        result = 31 * result + trainerData.contentHashCode()
        return result
    }
}

enum class MapperStateValueType {
    None,
    String,
    Bool,
    Number8,
    Number16,
    Number32,
}

data class MapperStateEntry(
    val address: String = "",
    val name: String = "",
    val value: String = "",
    val rawValue: Long = Long.MIN_VALUE,
    val type: MapperStateValueType = MapperStateValueType.Number8,
)

data class CartridgeState(
    var prgRomSize: Int = 0,
    var chrRomSize: Int = 0,
    var chrRamSize: Int = 0,
    var prgPageCount: Int = 0,
    var prgPageSize: Int = 0,
    var prgMemoryOffset: IntArray = IntArray(0x100),
    var prgType: Array<PrgMemoryType> = Array(0x100) { PrgMemoryType.PrgRom },
    var prgMemoryAccess: IntArray = IntArray(0x100),
    var chrPageCount: Int = 0,
    var chrPageSize: Int = 0,
    var chrRamPageSize: Int = 0,
    var chrMemoryOffset: IntArray = IntArray(0x40),
    var chrType: Array<ChrMemoryType> = Array(0x40) { ChrMemoryType.Default },
    var chrMemoryAccess: IntArray = IntArray(0x40),
    var workRamPageSize: Int = 0,
    var saveRamPageSize: Int = 0,
    var mirroring: MirroringType = MirroringType.Horizontal,
    var hasBattery: Boolean = false,
    var customEntries: List<MapperStateEntry> = emptyList(),
)

@Serializable
data class MapperSnapshot(
    val saveRam: ByteArray = ByteArray(0),
    val workRam: ByteArray = ByteArray(0),
    val chrRam: ByteArray = ByteArray(0),
    val mapperRam: ByteArray = ByteArray(0),
    val nametableRam: ByteArray = ByteArray(0),
    val prgMemoryOffset: IntArray = IntArray(0x100),
    val prgMemoryType: Array<PrgMemoryType> = Array(0x100) { PrgMemoryType.PrgRom },
    val prgMemoryAccess: IntArray = IntArray(0x100),
    val chrMemoryOffset: IntArray = IntArray(0x40),
    val chrMemoryType: Array<ChrMemoryType> = Array(0x40) { ChrMemoryType.Default },
    val chrMemoryAccess: IntArray = IntArray(0x40),
    val mirroring: MirroringType = MirroringType.Horizontal,
    val extra: MapperExtraSnapshot = MapperExtraSnapshot(),
) {
    override fun equals(other: Any?): Boolean = other is MapperSnapshot &&
        saveRam.contentEquals(other.saveRam) &&
        workRam.contentEquals(other.workRam) &&
        chrRam.contentEquals(other.chrRam) &&
        mapperRam.contentEquals(other.mapperRam) &&
        nametableRam.contentEquals(other.nametableRam) &&
        prgMemoryOffset.contentEquals(other.prgMemoryOffset) &&
        prgMemoryType.contentEquals(other.prgMemoryType) &&
        prgMemoryAccess.contentEquals(other.prgMemoryAccess) &&
        chrMemoryOffset.contentEquals(other.chrMemoryOffset) &&
        chrMemoryType.contentEquals(other.chrMemoryType) &&
        chrMemoryAccess.contentEquals(other.chrMemoryAccess) &&
        mirroring == other.mirroring &&
        extra == other.extra

    override fun hashCode(): Int {
        var result = saveRam.contentHashCode()
        result = 31 * result + workRam.contentHashCode()
        result = 31 * result + chrRam.contentHashCode()
        result = 31 * result + mapperRam.contentHashCode()
        result = 31 * result + nametableRam.contentHashCode()
        result = 31 * result + prgMemoryOffset.contentHashCode()
        result = 31 * result + prgMemoryType.contentHashCode()
        result = 31 * result + prgMemoryAccess.contentHashCode()
        result = 31 * result + chrMemoryOffset.contentHashCode()
        result = 31 * result + chrMemoryType.contentHashCode()
        result = 31 * result + chrMemoryAccess.contentHashCode()
        result = 31 * result + mirroring.hashCode()
        result = 31 * result + extra.hashCode()
        return result
    }
}

@Serializable
data class MapperExtraSnapshot(
    val ints: IntArray = IntArray(0),
    val longs: LongArray = LongArray(0),
    val booleans: BooleanArray = BooleanArray(0),
) {
    override fun equals(other: Any?): Boolean = other is MapperExtraSnapshot &&
        ints.contentEquals(other.ints) &&
        longs.contentEquals(other.longs) &&
        booleans.contentEquals(other.booleans)

    override fun hashCode(): Int {
        var result = ints.contentHashCode()
        result = 31 * result + longs.contentHashCode()
        result = 31 * result + booleans.contentHashCode()
        return result
    }
}
