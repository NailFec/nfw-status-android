package com.nailfec.nfwstatus.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.ui.theme.LevelAfk
import com.nailfec.nfwstatus.ui.theme.LevelBusy
import com.nailfec.nfwstatus.ui.theme.LevelDoing
import com.nailfec.nfwstatus.ui.theme.LevelFree
import com.nailfec.nfwstatus.ui.theme.LevelVeryFree

enum class Level(
    val wire: String,
    @StringRes val labelRes: Int,
    val color: Color,
) {
    VERY_FREE("very_free", R.string.level_very_free, LevelVeryFree),
    FREE("free", R.string.level_free, LevelFree),
    DOING("doing", R.string.level_doing, LevelDoing),
    BUSY("busy", R.string.level_busy, LevelBusy),
    AFK("afk", R.string.level_afk, LevelAfk),
    ;

    companion object {
        fun fromWire(value: String): Level? = entries.firstOrNull { it.wire == value }
    }
}

enum class MealSlot(
    val wire: String,
    @StringRes val labelRes: Int,
) {
    BREAKFAST("breakfast", R.string.slot_breakfast),
    LUNCH("lunch", R.string.slot_lunch),
    DINNER("dinner", R.string.slot_dinner),
    ;

    companion object {
        fun fromWire(value: String): MealSlot? = entries.firstOrNull { it.wire == value }
    }
}

@StringRes
fun stageLabelRes(stage: String): Int = when (stage) {
    "awake" -> R.string.stage_awake
    "light" -> R.string.stage_light
    "deep" -> R.string.stage_deep
    "rem" -> R.string.stage_rem
    else -> R.string.stage_unknown
}
