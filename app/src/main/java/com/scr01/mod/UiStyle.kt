package com.scr01.mod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip

internal object AppUi {
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val PagePadding = PaddingValues(horizontal = SpaceLg, vertical = SpaceMd)
    val SectionPadding = PaddingValues(horizontal = SpaceLg, vertical = SpaceMd)
    val TouchHeight = 48.dp
    val ButtonHeight = 48.dp
    val ButtonPadding = PaddingValues(horizontal = SpaceMd, vertical = SpaceXs)
    val FieldHeight = 48.dp
    val ContentWidth = 960.dp
    val WideWidth = 600.dp
    val DialogShape = RoundedCornerShape(20.dp)
    val CardShape = RoundedCornerShape(16.dp)
    val ControlShape = RoundedCornerShape(22.dp)
    val FieldShape = ControlShape
}

internal enum class UiStatus { SUCCESS, WAITING, LOADING, ERROR, UNKNOWN, DISABLED }

@Composable
internal fun statusColor(status: UiStatus): Color = when (status) {
    UiStatus.SUCCESS -> if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF75CD96) else Color(0xFF237A43)
    UiStatus.ERROR -> MaterialTheme.colorScheme.error
    UiStatus.WAITING -> if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFE6BD72) else Color(0xFF805600)
    UiStatus.LOADING -> MaterialTheme.colorScheme.primary
    UiStatus.UNKNOWN, UiStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun useWideLayout(width: Dp): Boolean =
    width >= AppUi.WideWidth && LocalConfiguration.current.fontScale <= 1.3f

@Composable
internal fun ScrModPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = AppUi.ContentWidth).fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(AppUi.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppUi.SpaceMd), content = content)
    }
}

internal enum class ButtonKind { PRIMARY, SECONDARY, TEXT, DESTRUCTIVE }

/** The same action slots stack at large font sizes instead of squeezing their labels. */
@Composable
internal fun ScrModAdaptivePair(modifier: Modifier = Modifier,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (useWideLayout(maxWidth)) Row(horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceMd),
            verticalAlignment = Alignment.CenterVertically) {
            first(Modifier.weight(1f)); second(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
            first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ScrModDialog(title: String, onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val config = LocalConfiguration.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(AppUi.SpaceLg).widthIn(max = 680.dp).fillMaxWidth()
            .heightIn(max = (config.screenHeightDp - 32).coerceAtLeast(120).dp),
            shape = AppUi.DialogShape, color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(AppUi.SectionPadding), verticalArrangement = Arrangement.spacedBy(AppUi.SpaceMd)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppUi.SpaceSm), content = content)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

@Composable
internal fun ScrModSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = AppUi.ControlShape,
    contentPadding: PaddingValues = AppUi.ButtonPadding,
    content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick, modifier.minimumInteractiveComponentSize().heightIn(min = AppUi.ButtonHeight), enabled, shape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        contentPadding = contentPadding, content = content)
}

@Composable
internal fun ScrModTextButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = AppUi.ControlShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = AppUi.SpaceSm, vertical = AppUi.SpaceSm),
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier.minimumInteractiveComponentSize().heightIn(min = AppUi.ButtonHeight), enabled, shape,
        contentPadding = contentPadding, content = content)
}

@Composable
internal fun ScrModChoice(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: @Composable () -> Unit) {
    Surface(modifier.heightIn(min = AppUi.ButtonHeight - AppUi.SpaceSm).clip(AppUi.ControlShape).selectable(selected, enabled = enabled,
        role = Role.RadioButton, onClick = onClick), shape = AppUi.ControlShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(Modifier.padding(horizontal = AppUi.SpaceLg, vertical = AppUi.SpaceSm), contentAlignment = Alignment.Center) { label() }
    }
}

@Composable
internal fun <T> ScrModSelectionGroup(options: List<T>, selected: T, onSelect: (T) -> Unit,
    label: (T) -> String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(modifier, shape = AppUi.ControlShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(Modifier.selectableGroup().padding(AppUi.SpaceXs), horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceXs)) {
            options.forEach { option ->
                ScrModChoice(option == selected, { onSelect(option) }, Modifier.weight(1f), enabled) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun ScrModTextField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, readOnly: Boolean = false,
    singleLine: Boolean = true, shape: Shape = AppUi.FieldShape,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    // Unlabelled single-line controls share the action height; labelled/multiline
    // fields retain Material's expanding layout for accessibility.
    if (singleLine && label == null && supportingText == null) {
        val container = when {
            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
        val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        Surface(modifier.heightIn(min = AppUi.FieldHeight), shape = shape, color = container) {
            androidx.compose.foundation.text.BasicTextField(
                value = value, onValueChange = onValueChange, enabled = enabled,
                readOnly = readOnly, singleLine = true, keyboardOptions = keyboardOptions,
                textStyle = textStyle.copy(color = foreground),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppUi.SpaceLg, vertical = AppUi.SpaceMd),
                decorationBox = { editor ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppUi.SpaceSm)) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) placeholder?.invoke()
                            editor()
                        }
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            suffix?.invoke(); trailingIcon?.invoke()
                        }
                    }
                })
        }
        return
    }
    TextField(value = value, onValueChange = onValueChange,
        modifier = modifier.heightIn(min = AppUi.FieldHeight), enabled = enabled, readOnly = readOnly,
        singleLine = singleLine, shape = shape, textStyle = textStyle, label = label,
        placeholder = placeholder, suffix = suffix, trailingIcon = trailingIcon,
        supportingText = supportingText, isError = isError, keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
}

@Composable
internal fun ScrModCard(modifier: Modifier = Modifier, shape: Shape = AppUi.CardShape,
    content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = shape, colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface), content = content)
}

@Composable
internal fun ScrModButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = AppUi.ControlShape,
    kind: ButtonKind = ButtonKind.PRIMARY,
    contentPadding: PaddingValues = AppUi.ButtonPadding,
    content: @Composable RowScope.() -> Unit) {
    if (kind == ButtonKind.SECONDARY) {
        ScrModSecondaryButton(onClick, modifier, enabled, shape, contentPadding, content)
        return
    }
    if (kind == ButtonKind.TEXT) {
        ScrModTextButton(onClick, modifier, enabled, shape, contentPadding, content)
        return
    }
    Button(onClick, modifier.minimumInteractiveComponentSize().heightIn(min = AppUi.ButtonHeight), enabled = enabled, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = if (kind == ButtonKind.DESTRUCTIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (kind == ButtonKind.DESTRUCTIVE) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary),
        contentPadding = contentPadding, content = content)
}

@Composable
internal fun ScrModSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(checked, onCheckedChange, modifier, enabled = enabled, colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline))
}

internal fun displayAppVersion(context: android.content.Context): String =
    appVersionName(context).replace(Regex("\\s*\\(build(\\d+)\\)", RegexOption.IGNORE_CASE), " · Build $1")
