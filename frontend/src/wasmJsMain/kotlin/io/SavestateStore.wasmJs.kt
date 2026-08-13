@file:OptIn(ExperimentalWasmJsInterop::class)

package io

actual fun platformSavestateStore(): SavestateStore = LocalStorageSavestateStore()

private class LocalStorageSavestateStore : SavestateStore {
    override fun saveState(sha: String, number: Int, data: ByteArray) {
        val array = uint8Array(data.size)
        data.forEachIndexed { index, byte -> uint8ArraySet(array, index, byte.toInt() and 0xFF) }
        localStorageSet(key(sha, number), bytesToBase64(array))
    }

    override fun loadState(sha: String, number: Int): ByteArray? {
        val value = localStorageGet(key(sha, number)) ?: return null
        val array = base64ToBytes(value)
        return ByteArray(uint8ArrayLength(array)) { index -> uint8ArrayGet(array, index).toByte() }
    }

    override fun hasState(sha: String, number: Int): Boolean = localStorageGet(key(sha, number)) != null

    private fun key(sha: String, number: Int): String = "savestate:$sha:$number"
}

@JsFun("(key, value) => localStorage.setItem(key, value)")
private external fun localStorageSet(key: String, value: String)

@JsFun("(key) => localStorage.getItem(key)")
private external fun localStorageGet(key: String): String?

@JsFun("(length) => new Uint8Array(length)")
private external fun uint8Array(length: Int): JsAny

@JsFun("(array, index, value) => { array[index] = value; }")
private external fun uint8ArraySet(array: JsAny, index: Int, value: Int)

@JsFun("(array) => array.length")
private external fun uint8ArrayLength(array: JsAny): Int

@JsFun("(array, index) => array[index]")
private external fun uint8ArrayGet(array: JsAny, index: Int): Int

@JsFun("""
    (array) => {
        let binary = '';
        for (let i = 0; i < array.length; i++) binary += String.fromCharCode(array[i]);
        return btoa(binary);
    }
""")
private external fun bytesToBase64(array: JsAny): String

@JsFun("""
    (value) => {
        const binary = atob(value);
        const array = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
        return array;
    }
""")
private external fun base64ToBytes(value: String): JsAny
