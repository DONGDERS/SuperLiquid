package io.github.liuran001.mmliquidglass.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Core-icon aliases for the names the ported KernelSU screens reference.
 * material-icons-extended (tens of thousands of classes) is deliberately not
 * shipped to keep R8 fast and the APK small; these stand-ins keep the ported
 * layouts compiling against material-icons-core only.
 */
val Icons.Filled.MenuOpen: ImageVector get() = Menu
val Icons.Filled.Brightness1: ImageVector get() = Star
val Icons.Filled.Brightness3: ImageVector get() = Star
val Icons.Filled.Brightness4: ImageVector get() = Star
val Icons.Filled.Brightness7: ImageVector get() = Star
val Icons.Filled.Star1: ImageVector get() = Star
val Icons.Filled.Star3: ImageVector get() = Star
val Icons.Filled.Star4: ImageVector get() = Star
val Icons.Filled.Star7: ImageVector get() = Star
val Icons.Filled.AspectRatio: ImageVector get() = Info
val Icons.Filled.Description: ImageVector get() = Info
val Icons.Filled.DesignServices: ImageVector get() = Settings
val Icons.Filled.Pin: ImageVector get() = Place
val Icons.Filled.Style: ImageVector get() = Home
val Icons.Filled.Swipe: ImageVector get() = PlayArrow
val Icons.Filled.ViewCarousel: ImageVector get() = PlayArrow
val Icons.Filled.Wallpaper: ImageVector get() = Home
val Icons.Filled.WaterDrop: ImageVector get() = Info
val Icons.Filled.DisplaySettings: ImageVector get() = Settings
val Icons.Filled.Palette: ImageVector get() = Star
val Icons.Filled.RestartAlt: ImageVector get() = Refresh
val Icons.Filled.BlurOn: ImageVector get() = Info
val Icons.Filled.CallToAction: ImageVector get() = Info
val Icons.Filled.Colorize: ImageVector get() = Info
