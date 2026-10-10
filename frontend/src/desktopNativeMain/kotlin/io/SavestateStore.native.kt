package io

actual fun platformSavestateStore(): SavestateStore = NoOpSavestateStore

private object NoOpSavestateStore : SavestateStore {
    override fun saveState(sha: String, number: Int, data: ByteArray) = Unit

    override fun loadState(sha: String, number: Int): ByteArray? = null

    override fun hasState(sha: String, number: Int): Boolean = false
}
