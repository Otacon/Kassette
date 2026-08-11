@file:OptIn(ExperimentalWasmJsInterop::class)

package frontend

actual fun hasTouchScreen(): Boolean = browserHasTouchScreen()

@JsFun("() => navigator.maxTouchPoints > 0 || matchMedia('(pointer: coarse)').matches")
private external fun browserHasTouchScreen(): Boolean
