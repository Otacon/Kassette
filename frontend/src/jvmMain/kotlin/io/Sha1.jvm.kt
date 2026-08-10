package io

import java.security.MessageDigest

actual suspend fun sha1Hex(bytes: ByteArray): String? =
    MessageDigest.getInstance("SHA-1")
        .digest(bytes)
        .toHexString()
