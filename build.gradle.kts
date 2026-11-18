// 当前本地 Android / Termux / proot 环境无法构建 SuperLiquid，请使用 GitHub CI（GitHub Actions）。
// 流程见仓库根目录 AGENTS.md；构建验证以 Actions 对应提交的结果为准。

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
