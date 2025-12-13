package com.ningyu.novelreader.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Primary filled button used throughout the app
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        content = content
    )
}

/**
 * Destructive filled button (red background) – used for dangerous actions
 */
@Composable
fun AppDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        content = content
    )
}

/**
 * Standard outlined button
 */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        content = content
    )
}

/**
 * Outlined button with red text – used for dangerous actions that should be less prominent
 */
@Composable
fun AppDestructiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        content = content
    )
}

/**
 * Simple text button – optionally destructive (red text)
 */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = if (isDestructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else ButtonDefaults.textButtonColors(),
        content = content
    )
}

/**
 * Icon-only button (usually placed in TopAppBar actions)
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}