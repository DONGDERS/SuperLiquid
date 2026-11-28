# SuperLiquid 开发说明

## 构建与验证（开始任务前阅读）

- 当前本地 Android / Termux / proot 环境无法构建 SuperLiquid，请使用 GitHub CI（GitHub Actions）。
- 本地只做源码编辑和静态检查（例如 `git diff --check`）；不要用本地 Gradle、`build.sh` 或 `setup-tools.sh` 作为验证，也不要为此临时安装工具链。
- Debug 验证使用 `.github/workflows/build.yml`（`build`）；签名发布包使用 `.github/workflows/release.yml`（`release`）。优先在 CI 仓库的独立分支验证普通改动；不为验证而推送主仓库 `main` 或发布 Release。
- 核对 Actions 运行的提交 SHA 和结果。编译通过不等于真机 UI 已验证。
- 构建完成后，安装 APK 并核对设备上的 `versionCode`，随后先向用户报告构建与安装结果，明确标注界面尚未验收。默认不主动打开目标 App 或操作界面；用户明确要求后再做真机 UI 测试。

## 版本管理
- 每次为新测试包或发布包触发构建前，将 `app/build.gradle.kts` 中的 `versionCode` 在最近一次出包所用版本号的基础上递增 1，便于识别新旧 APK。
- `versionName` 仅在发布正式版本时更新。日常测试构建沿用当前版本名称。
