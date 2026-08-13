package io

interface SavestateStore {
    fun saveState(sha: String, number: Int, data: ByteArray)
    fun loadState(sha: String, number: Int): ByteArray?
    fun hasState(sha: String, number: Int): Boolean
}

expect fun platformSavestateStore(): SavestateStore
