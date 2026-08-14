@file:OptIn(ExperimentalWasmJsInterop::class)

package io

internal actual fun readTextResource(path: String): String = readTextResourceImpl(path.removePrefix("/"))

@JsFun(
    """
    (path) => {
        const resource = globalThis.kassetteResources?.[path];
        if (resource !== undefined) return resource;
        throw new Error('Resource was not preloaded: ' + path);
    }
    """
)
private external fun readTextResourceImpl(path: String): String
