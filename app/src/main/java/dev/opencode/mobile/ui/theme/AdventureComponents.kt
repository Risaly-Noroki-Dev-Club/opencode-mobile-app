package dev.opencode.mobile.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AdventureBackground(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colors
    val adventure = MaterialTheme.adventure
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        adventure.surface2,
                        colors.background,
                    ),
                ),
            ),
        content = content,
    )
}

@Composable
fun AdventureTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        backgroundColor = MaterialTheme.adventure.topBar,
        contentColor = MaterialTheme.colors.onSurface,
        elevation = 4.dp,
    )
}

@Composable
fun AdventureCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.adventure.cardContainer,
    contentColor: Color = MaterialTheme.colors.onSurface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.adventure.divider),
    elevation: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        border = border,
        elevation = elevation,
        content = content,
    )
}

@Composable
fun AdventureFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colors.primary,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = MaterialTheme.colors.onPrimary,
            disabledBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
        ),
        elevation = ButtonDefaults.elevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
        content = content,
    )
}

@Composable
fun AdventureTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.primary),
        content = content,
    )
}

@Composable
fun AdventureOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.small,
        colors = adventureTextFieldColors(),
    )
}

@Composable
fun adventureTextFieldColors(): TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(
    textColor = MaterialTheme.colors.onSurface,
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = MaterialTheme.adventure.divider,
    focusedLabelColor = MaterialTheme.colors.primary,
    unfocusedLabelColor = MaterialTheme.adventure.textMedium,
    cursorColor = MaterialTheme.colors.primary,
    backgroundColor = MaterialTheme.colors.surface,
)

@Composable
fun AdventureSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.overline,
        color = MaterialTheme.adventure.textMedium,
    )
}
