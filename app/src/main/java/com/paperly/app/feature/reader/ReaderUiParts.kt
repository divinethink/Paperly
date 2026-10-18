package com.paperly.app.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paperly.app.core.ui.theme.readerPalette
import com.paperly.app.domain.reader.ReaderTheme

private val CardRadius = 20.dp
private val PillHeight = 48.dp
private val ThemeDotSize = 44.dp
private val ThemeDotBorder = 1.dp
private val ThemeDotBorderSelected = 2.dp
private val StepButtonSize = 48.dp
private val ValueMinWidth = 56.dp

/** Rounded tonal card that groups a few rows (Reading settings, brightness, ...). */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}

/** Small caps label above a block (READING MODE / FONT / THEME). */
@Composable
internal fun BlockLabel(text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun selectedColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/** Full-width pill segmented control; the selected segment is a raised white pill. [badges]: option index -> count. */
@Composable
internal fun PillSegmented(
    options: List<Int>,
    selected: Int,
    badges: Map<Int, Int> = emptyMap(),
    onSelect: (Int) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selected
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = PillHeight).selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                ) {
                    Row(Modifier.padding(horizontal = 4.dp), Arrangement.Center, Alignment.CenterVertically) {
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.titleSmall,
                            color = selectedColor(isSelected),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        badges[index]?.takeIf { it > 0 }?.let { CountBadge(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Surface(Modifier.padding(start = 6.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            count.toString(),
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Label on the left, [− value +] pill on the right. Buttons are 48dp. */
@Composable
internal fun StepperRow(label: Int, value: String, onStep: (up: Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceBright) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { onStep(false) }
                Text(
                    value,
                    Modifier.widthIn(min = ValueMinWidth).padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                StepButton("+") { onStep(true) }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(StepButtonSize).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** One row of colour dots (Auto/Light/Sepia/Warm/Dark/AMOLED) with names; selected = brown ring. */
@Composable
internal fun ThemeRow(theme: ReaderTheme, onTheme: (ReaderTheme) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ReaderTheme.entries.forEach { option ->
            val isSelected = option == theme
            val name = stringResource(themeName(option))
            Column(
                Modifier.weight(1f).selectable(isSelected, role = Role.RadioButton, onClick = { onTheme(option) })
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemeDot(option, isSelected)
                Text(
                    name,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = selectedColor(isSelected),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThemeDot(theme: ReaderTheme, selected: Boolean) {
    val ring = if (selected) {
        BorderStroke(ThemeDotBorderSelected, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(ThemeDotBorder, MaterialTheme.colorScheme.outline)
    }
    val base = Modifier.size(ThemeDotSize).clip(CircleShape)
    if (theme == ReaderTheme.AUTO) {
        val light = readerPalette(ReaderTheme.LIGHT, false).background
        val dark = readerPalette(ReaderTheme.DARK, false).background
        Box(
            base.background(light).drawBehind {
                val path = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, dark)
            }.border(ring, CircleShape),
        )
    } else {
        Box(base.background(readerPalette(theme, false).background).border(ring, CircleShape))
    }
}
