// 当前本地 Android / Termux / proot 环境无法构建 SuperLiquid，请使用 GitHub CI（GitHub Actions）。
// 流程见仓库根目录 AGENTS.md；构建验证以 Actions 对应提交的结果为准。

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "SuperLiquid"
include(":app")
