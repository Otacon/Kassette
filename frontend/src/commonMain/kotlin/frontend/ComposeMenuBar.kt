package frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.VideoFilter

@Composable
fun ComposeMenuBar(
    onOpenRom: () -> Unit,
    onPauseToggle: () -> Unit,
    onReset: () -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    gameActionsEnabled: Boolean,
    paused: Boolean,
    loadStateSlots: Set<Int>,
    onMenuOpened: () -> Unit,
    onMenuDismissed: () -> Unit,
    videoFilter: VideoFilter,
    onToggleCrt: () -> Unit,
    onControllerSettings: () -> Unit,
    onExit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var expandedMenu by remember { mutableStateOf<MenuId?>(null) }
    val density = LocalDensity.current
    val popupOffset = remember(density, expandedMenu) {
        with(density) {
            IntOffset(
                x = when (expandedMenu) {
                    MenuId.File, null -> 4.dp.roundToPx()
                    MenuId.Game -> (4.dp + MENU_BUTTON_WIDTH).roundToPx()
                    MenuId.Video -> (4.dp + MENU_BUTTON_WIDTH * 2).roundToPx()
                    MenuId.Input -> (4.dp + MENU_BUTTON_WIDTH * 3).roundToPx()
                },
                y = MENU_HEIGHT.roundToPx(),
            )
        }
    }
    Column(modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(MENU_HEIGHT)
                .background(MENU_BAR_COLOR)
                .border(1.dp, MENU_BORDER_COLOR)
                .padding(horizontal = 4.dp),
        ) {
            MenuButton(
                label = "File",
                selected = expandedMenu == MenuId.File,
                onClick = { expandedMenu = expandedMenu.toggle(MenuId.File, onMenuOpened) },
            )
            MenuButton(
                label = "Game",
                selected = expandedMenu == MenuId.Game,
                onClick = { expandedMenu = expandedMenu.toggle(MenuId.Game, onMenuOpened) },
            )
            MenuButton(
                label = "Video",
                selected = expandedMenu == MenuId.Video,
                onClick = { expandedMenu = expandedMenu.toggle(MenuId.Video, onMenuOpened) },
            )
            MenuButton(
                label = "Input",
                selected = expandedMenu == MenuId.Input,
                onClick = { expandedMenu = expandedMenu.toggle(MenuId.Input, onMenuOpened) },
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            content(Modifier.fillMaxSize())
        }
    }

    if (expandedMenu != null) {
        Popup(
            alignment = Alignment.TopStart,
            offset = popupOffset,
            onDismissRequest = {
                expandedMenu = null
                onMenuDismissed()
            },
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                Modifier
                    .width(180.dp)
                    .shadow(6.dp)
                    .background(MENU_POPUP_COLOR)
                    .border(1.dp, MENU_BORDER_COLOR)
                    .padding(vertical = 4.dp),
            ) {
                when (expandedMenu) {
                    MenuId.File -> {
                        MenuItem("Open ROM...") {
                            expandedMenu = null
                            onOpenRom()
                        }
                        onExit?.let {
                            MenuSeparator()
                            MenuItem("Exit") {
                                expandedMenu = null
                                it.invoke()
                            }
                        }
                    }

                    MenuId.Game -> {
                        MenuItem(
                            label = if (paused) "Resume" else "Pause",
                            enabled = gameActionsEnabled,
                        ) {
                            expandedMenu = null
                            onPauseToggle()
                        }
                        MenuItem(
                            label = "Reset",
                            enabled = gameActionsEnabled,
                        ) {
                            expandedMenu = null
                            onReset()
                        }
                        MenuSeparator()
                        (1..5).forEach { slot ->
                            MenuItem(
                                label = "Save State $slot",
                                enabled = gameActionsEnabled,
                            ) {
                                expandedMenu = null
                                onSaveState(slot)
                            }
                        }
                        MenuSeparator()
                        (1..5).forEach { slot ->
                            MenuItem(
                                label = "Load State $slot",
                                enabled = gameActionsEnabled && slot in loadStateSlots,
                            ) {
                                expandedMenu = null
                                onLoadState(slot)
                            }
                        }
                    }

                    MenuId.Video -> {
                        MenuItem(
                            label = "CRT Effect",
                            checked = videoFilter == VideoFilter.CRT,
                            role = Role.Checkbox,
                        ) {
                            expandedMenu = null
                            onToggleCrt()
                        }
                    }

                    MenuId.Input -> {
                        MenuItem(
                            label = "Bindings...",
                        ) {
                            expandedMenu = null
                            onControllerSettings()
                        }
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxHeight()
            .width(MENU_BUTTON_WIDTH)
            .focusProperties { canFocus = false }
            .hoverable(interactionSource)
            .background(if (selected || hovered) MENU_SELECTION_COLOR else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = MENU_TEXT_STYLE)
    }
}

@Composable
private fun MenuItem(
    label: String,
    checked: Boolean? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .focusProperties { canFocus = false }
            .hoverable(interactionSource, enabled)
            .background(if (enabled && hovered) MENU_SELECTION_COLOR else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            CheckboxMark(checked)
        }
        BasicText(label, style = if (enabled) MENU_TEXT_STYLE else MENU_DISABLED_TEXT_STYLE)
    }
}

@Composable
private fun MenuSeparator() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(MENU_BORDER_COLOR),
    )
}

@Composable
private fun CheckboxMark(checked: Boolean) {
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(14.dp)
            .background(Color.White)
            .border(1.dp, MENU_CHECKBOX_BORDER_COLOR),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(MENU_TEXT_COLOR),
            )
        }
    }
}

private fun MenuId?.toggle(menu: MenuId, onMenuOpened: () -> Unit): MenuId? =
    if (this == menu) {
        null
    } else {
        onMenuOpened()
        menu
    }

private enum class MenuId { File, Game, Video, Input }

private val MENU_HEIGHT = 30.dp
private val MENU_BUTTON_WIDTH = 56.dp
private val MENU_BAR_COLOR = Color(0xFFF1F1F1)
private val MENU_POPUP_COLOR = Color(0xFFF7F7F7)
private val MENU_BORDER_COLOR = Color(0xFFB8B8B8)
private val MENU_CHECKBOX_BORDER_COLOR = Color(0xFF6F6F6F)
private val MENU_SELECTION_COLOR = Color(0xFFD9E8F8)
private val MENU_TEXT_COLOR = Color(0xFF161616)
private val MENU_DISABLED_TEXT_COLOR = Color(0xFF8A8A8A)
private val MENU_TEXT_STYLE = TextStyle(color = MENU_TEXT_COLOR, fontSize = 13.sp)
private val MENU_DISABLED_TEXT_STYLE = TextStyle(color = MENU_DISABLED_TEXT_COLOR, fontSize = 13.sp)
