package be.casperverswijvelt.tiles.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommandResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
) : Parcelable
