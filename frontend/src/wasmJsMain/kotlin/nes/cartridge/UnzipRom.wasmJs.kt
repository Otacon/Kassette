@file:OptIn(ExperimentalWasmJsInterop::class)

package nes.cartridge

import kotlinx.coroutines.suspendCancellableCoroutine
import nes.util.toUnsignedInt
import kotlin.coroutines.resume

actual suspend fun unzipRom(zipData: RomData): UnzipRomResult = try {
    when (val entry = zipData.bytes.singleNesZipEntry()) {
        ZipEntryResult.NotFound -> UnzipRomResult.NotFound
        ZipEntryResult.MultipleRoms -> UnzipRomResult.MultipleRoms
        ZipEntryResult.UnknownError -> UnzipRomResult.UnknownError
        is ZipEntryResult.Found -> entry.toRomData()
    }
} catch (_: Throwable) {
    UnzipRomResult.UnknownError
}

private fun ByteArray.singleNesZipEntry(): ZipEntryResult {
    val entries = mutableListOf<ZipEntryResult.Found>()
    var offset = 0
    while (offset + ZIP_LOCAL_HEADER_SIZE <= size) {
        if (readIntLe(offset) != ZIP_LOCAL_FILE_HEADER_SIGNATURE) break

        val flags = readShortLe(offset + 6)
        val method = readShortLe(offset + 8)
        val compressedSize = readIntLe(offset + 18)
        val nameLength = readShortLe(offset + 26)
        val extraLength = readShortLe(offset + 28)
        val nameStart = offset + ZIP_LOCAL_HEADER_SIZE
        val dataStart = nameStart + nameLength + extraLength
        val dataEnd = dataStart + compressedSize
        if ((flags and ZIP_DATA_DESCRIPTOR_FLAG) != 0 || dataEnd > size) return ZipEntryResult.UnknownError

        val name = decodeAscii(nameStart, nameLength)
        if (!name.endsWith('/') && name.endsWith(".nes", ignoreCase = true)) {
            entries += ZipEntryResult.Found(
                name = name.substringAfterLast('/'),
                method = method,
                compressedBytes = copyOfRange(dataStart, dataEnd),
            )
            if (entries.size > 1) return ZipEntryResult.MultipleRoms
        }
        offset = dataEnd
    }

    return when (entries.size) {
        0 -> ZipEntryResult.NotFound
        1 -> entries.single()
        else -> ZipEntryResult.MultipleRoms
    }
}

private suspend fun ZipEntryResult.Found.toRomData(): UnzipRomResult = when (method) {
    ZIP_METHOD_STORED -> UnzipRomResult.Success(RomData(name, compressedBytes))
    ZIP_METHOD_DEFLATED -> inflate(compressedBytes)?.let { UnzipRomResult.Success(RomData(name, it)) }
        ?: UnzipRomResult.UnknownError

    else -> UnzipRomResult.UnknownError
}

private suspend fun inflate(bytes: ByteArray): ByteArray? = suspendCancellableCoroutine { continuation ->
    try {
        inflateRaw(
            bytes = bytes.toUint8Array(),
            onSuccess = { result ->
                if (continuation.isActive) continuation.resume(result?.toByteArray())
            },
            onError = {
                if (continuation.isActive) continuation.resume(null)
            },
        )
    } catch (_: Throwable) {
        if (continuation.isActive) continuation.resume(null)
    }
}

private fun ByteArray.readShortLe(offset: Int): Int = this[offset].toUnsignedInt() or
    (this[offset + 1].toUnsignedInt() shl 8)

private fun ByteArray.readIntLe(offset: Int): Int = readShortLe(offset) or (readShortLe(offset + 2) shl 16)

private fun ByteArray.decodeAscii(offset: Int, length: Int): String = buildString(length) {
    repeat(length) { index -> append(this@decodeAscii[offset + index].toInt().toChar()) }
}

private fun ByteArray.toUint8Array(): JsAny = createUint8Array(size).also { array ->
    forEachIndexed { index, byte -> uint8ArraySet(array, index, byte.toInt() and 0xFF) }
}

private fun JsAny.toByteArray(): ByteArray = ByteArray(uint8ArrayLength(this)) { index ->
    uint8ArrayGet(this, index).toByte()
}

@JsFun(
    """
    (bytes, onSuccess, onError) => {
        try {
            if (typeof DecompressionStream === 'undefined') {
                onError();
                return;
            }
            const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
            new Response(stream).arrayBuffer()
                .then((buffer) => onSuccess(new Uint8Array(buffer)))
                .catch(onError);
        } catch (_) {
            onError();
        }
    }
    """
)
private external fun inflateRaw(
    bytes: JsAny,
    onSuccess: (JsAny?) -> Unit,
    onError: () -> Unit,
)

@JsFun("(length) => new Uint8Array(length)")
private external fun createUint8Array(length: Int): JsAny

@JsFun("(array, index, value) => { array[index] = value; }")
private external fun uint8ArraySet(array: JsAny, index: Int, value: Int)

@JsFun("(array) => array.length")
private external fun uint8ArrayLength(array: JsAny): Int

@JsFun("(array, index) => array[index]")
private external fun uint8ArrayGet(array: JsAny, index: Int): Int

private sealed interface ZipEntryResult {
    data class Found(
        val name: String,
        val method: Int,
        val compressedBytes: ByteArray,
    ) : ZipEntryResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Found

            if (method != other.method) return false
            if (name != other.name) return false
            if (!compressedBytes.contentEquals(other.compressedBytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = method
            result = 31 * result + name.hashCode()
            result = 31 * result + compressedBytes.contentHashCode()
            return result
        }
    }

    data object NotFound : ZipEntryResult
    data object MultipleRoms : ZipEntryResult
    data object UnknownError : ZipEntryResult
}

private const val ZIP_LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50
private const val ZIP_LOCAL_HEADER_SIZE = 30
private const val ZIP_DATA_DESCRIPTOR_FLAG = 0x08
private const val ZIP_METHOD_STORED = 0
private const val ZIP_METHOD_DEFLATED = 8
