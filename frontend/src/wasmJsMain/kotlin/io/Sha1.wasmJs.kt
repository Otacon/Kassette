@file:OptIn(ExperimentalWasmJsInterop::class)

package io

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun sha1Hex(bytes: ByteArray): String? {
    if (!hasSubtleCrypto()) return null

    return suspendCancellableCoroutine { continuation ->
    val input = uint8Array(bytes.size)
    bytes.forEachIndexed { index, byte -> uint8ArraySet(input, index, byte.toInt() and 0xff) }

    digestSha1(
        input,
        onSuccess = { buffer ->
            if (continuation.isActive) continuation.resume(buffer.toByteArray().toHexString())
        },
        onError = {
            if (continuation.isActive) continuation.resumeWithException(RuntimeException("Unable to calculate SHA-1"))
        },
    )
}
}

@JsFun("() => !!(globalThis.crypto && globalThis.crypto.subtle && globalThis.crypto.subtle.digest)")
private external fun hasSubtleCrypto(): Boolean

private fun JsAny?.toByteArray(): ByteArray {
    val buffer = requireNotNull(this) { "Missing SHA-1 digest" }
    return ByteArray(arrayBufferLength(buffer)) { index -> arrayBufferGet(buffer, index).toByte() }
}

@JsFun("(length) => new Uint8Array(length)")
private external fun uint8Array(length: Int): JsAny

@JsFun("(array, index, value) => { array[index] = value; }")
private external fun uint8ArraySet(array: JsAny, index: Int, value: Int)

@JsFun(
    """
    (bytes, onSuccess, onError) => {
        globalThis.crypto.subtle.digest('SHA-1', bytes).then(onSuccess, onError);
    }
    """
)
private external fun digestSha1(
    bytes: JsAny,
    onSuccess: (JsAny?) -> Unit,
    onError: (JsAny?) -> Unit,
)

@JsFun("(buffer) => new Uint8Array(buffer).length")
private external fun arrayBufferLength(buffer: JsAny): Int

@JsFun("(buffer, index) => new Uint8Array(buffer)[index]")
private external fun arrayBufferGet(buffer: JsAny, index: Int): Int
