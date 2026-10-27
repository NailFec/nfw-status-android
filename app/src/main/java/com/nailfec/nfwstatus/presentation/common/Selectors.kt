package com.nailfec.nfwstatus.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.model.Level
import com.nailfec.nfwstatus.model.MealSlot
import com.nailfec.nfwstatus.ui.theme.LevelUnknown

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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val needed = 112.dp * levels.size
        val rowWidth = if (needed > maxWidth) needed else maxWidth
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(rowWidth)) {
                levels.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = selected == level,
                        onClick = { onSelect(level) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                        icon = { LevelDot(level = level, decorative = true) },
                        label = {
                            Text(
                                text = stringResource(level.labelRes),
                                maxLines = 1,
                            )
                        },
                    )
                }
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
