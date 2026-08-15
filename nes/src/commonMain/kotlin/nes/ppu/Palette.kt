package nes.ppu

object Palette {
    // Public-domain style approximation, not copied from emulator assets.
    val COLORS = intArrayOf(
        0xFF545454.toInt(), 0xFF001E74.toInt(), 0xFF081090.toInt(), 0xFF300088.toInt(),
        0xFF440064.toInt(), 0xFF5C0030.toInt(), 0xFF540400.toInt(), 0xFF3C1800.toInt(),
        0xFF202A00.toInt(), 0xFF083A00.toInt(), 0xFF004000.toInt(), 0xFF003C00.toInt(),
        0xFF00323C.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),
        0xFF989698.toInt(), 0xFF084CC4.toInt(), 0xFF3032EC.toInt(), 0xFF5C1EE4.toInt(),
        0xFF8814B0.toInt(), 0xFFA01464.toInt(), 0xFF982220.toInt(), 0xFF783C00.toInt(),
        0xFF545A00.toInt(), 0xFF287200.toInt(), 0xFF087C00.toInt(), 0xFF007628.toInt(),
        0xFF006678.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),
        0xFFECEEEC.toInt(), 0xFF4C9AEC.toInt(), 0xFF787CEC.toInt(), 0xFFB062EC.toInt(),
        0xFFE454EC.toInt(), 0xFFEC58B4.toInt(), 0xFFEC6A64.toInt(), 0xFFD48820.toInt(),
        0xFFA0AA00.toInt(), 0xFF74C400.toInt(), 0xFF4CD020.toInt(), 0xFF38CC6C.toInt(),
        0xFF38B4CC.toInt(), 0xFF3C3C3C.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),
        0xFFECEEEC.toInt(), 0xFFA8CCEC.toInt(), 0xFFBCBCEC.toInt(), 0xFFD4B2EC.toInt(),
        0xFFECAEEC.toInt(), 0xFFECAED4.toInt(), 0xFFECB4B0.toInt(), 0xFFE4C490.toInt(),
        0xFFCCD278.toInt(), 0xFFB4DE78.toInt(), 0xFFA8E290.toInt(), 0xFF98E2B4.toInt(),
        0xFFA0D6E4.toInt(), 0xFFA0A2A0.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),
    )

    fun color(colorId: Int): Int {
        val color = COLORS[colorId and 0x3F]
        var red = (color shr 16) and 0xFF
        var green = (color shr 8) and 0xFF
        var blue = color and 0xFF
        if ((colorId and 0x40) != 0) {
            green = green * 3 / 4
            blue = blue * 3 / 4
        }
        if ((colorId and 0x80) != 0) {
            red = red * 3 / 4
            blue = blue * 3 / 4
        }
        if ((colorId and 0x100) != 0) {
            red = red * 3 / 4
            green = green * 3 / 4
        }
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}
