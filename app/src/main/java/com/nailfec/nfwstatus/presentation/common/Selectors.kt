package com.nailfec.nfwstatus.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.model.Level
import com.nailfec.nfwstatus.model.MealSlot
import com.nailfec.nfwstatus.ui.theme.LevelUnknown
import kotlin.math.roundToInt

@Composable
fun LevelDot(
    level: Level?,
    decorative: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = level?.color ?: LevelUnknown
    val description = stringResource(level?.labelRes ?: R.string.level_unknown)
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (decorative) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    )
}

@Composable
fun LevelSelector(
    selected: Level,
    onSelect: (Level) -> Unit,
    modifier: Modifier = Modifier,
) {
    val levels = Level.entries
    val selectedIndex = levels.indexOf(selected).coerceAtLeast(0)
    val description = stringResource(selected.labelRes)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LevelDot(level = selected, decorative = true)
            Text(text = description, style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { raw ->
                val next = raw.roundToInt().coerceIn(0, levels.lastIndex)
                val level = levels[next]
                if (level != selected) onSelect(level)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
            valueRange = 0f..levels.lastIndex.toFloat(),
            steps = (levels.size - 2).coerceAtLeast(0),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            levels.forEachIndexed { index, level ->
                val label = stringResource(level.labelRes)
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = if (level == selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        levels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 2,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun MealSlotSelector(
    selected: MealSlot,
    onSelect: (MealSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = MealSlot.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        slots.forEachIndexed { index, slot ->
            SegmentedButton(
                selected = selected == slot,
                onClick = { onSelect(slot) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = slots.size),
                label = {
                    Text(
                        text = stringResource(slot.labelRes),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
