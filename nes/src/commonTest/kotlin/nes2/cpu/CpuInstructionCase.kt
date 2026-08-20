package nes2.cpu

data class CpuInstructionCase(
    val name: String,
    val cpuState: CpuState,
    val value: Int = 0,
    val expectedA: Int = 0,
    val expectedX: Int = 0,
    val expectedY: Int = 0,
    val expectedValue: Int = 0,
    val expectedC: Boolean = false,
    val expectedV: Boolean = false,
    val expectedZ: Boolean = false,
    val expectedN: Boolean = false,
)
