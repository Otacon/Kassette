@file:OptIn(ExperimentalWasmJsInterop::class)

package platform

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import nes.cartridge.RomData
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.coroutines.resume

actual class FileChooser {
    private val input = document.getElementById("rom") as HTMLInputElement

    actual suspend fun pickRom(): RomData? = suspendCancellableCoroutine { continuation ->
        input.value = ""
        input.onchange = {
            val file = input.files?.asList()?.firstOrNull()
            if (file == null) {
                continuation.resume(null)
            } else {
                file.readRomData { continuation.resume(it) }
            }
        }
        input.click()
    }

    private fun File.readRomData(onLoaded: (RomData?) -> Unit) {
        val reader = FileReader()
        reader.onload = {
            onLoaded(RomData(name, reader.result.toByteArray()))
        }
        reader.onerror = { onLoaded(null) }
        reader.readAsArrayBuffer(this)
    }
}

private fun JsAny?.toByteArray(): ByteArray {
    val buffer = requireNotNull(this) { "Missing file contents" }
    return ByteArray(arrayBufferLength(buffer)) { index -> arrayBufferGet(buffer, index).toByte() }
}

@JsFun("(buffer) => new Uint8Array(buffer).length")
private external fun arrayBufferLength(buffer: JsAny): Int

@JsFun("(buffer, index) => new Uint8Array(buffer)[index]")
private external fun arrayBufferGet(buffer: JsAny, index: Int): Int
