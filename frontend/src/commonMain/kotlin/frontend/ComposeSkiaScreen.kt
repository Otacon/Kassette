package frontend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.key.onPreviewKeyEvent
import io.VideoFilter
import kotlin.math.roundToInt

@Composable
fun ComposeSkiaScreen(
    frameBuffer: SharedFrameBuffer,
    renderer: PlatformRenderer,
    keyboardInput: PlatformKeyboardInput?,
    videoFilter: VideoFilter,
    focusRequestKey: Any,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val frameState = remember(frameBuffer) { mutableStateOf(frameBuffer.initialFrame) }

    LaunchedEffect(frameBuffer) {
        frameBuffer.frames.collect { frameState.value = it }
    }

    LaunchedEffect(focusRequestKey) {
        focusRequester.requestFocus()
    }

    DisposableEffect(renderer, videoFilter) {
        renderer.init(videoFilter)
        onDispose(renderer::close)
    }

    Canvas(
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyboardInput?.onKeyEvent(it) == true }
            .onFocusChanged { state ->
                if (!state.isFocused) keyboardInput?.releaseAll()
            }
            .focusable(),
    ) {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        if (width > 0 && height > 0) {
            renderer.present(frameState.value, width, height)
            renderer.draw(drawContext.canvas.skiaCanvas)
        }
    }
}
