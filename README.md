# SuperLiquid — 液态玻璃底栏

LSPosed 模块（libxposed API 102）：iOS 26 风格液态玻璃底部导航栏。

**由 GLM（智谱）编写扩展** · 原项目：[liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)

## 支持的 App

| App | 包名 | 版本 | 方案 |
|---|---|---|---|
| 微信 | `com.tencent.mm` | 8.0.72+ (3085+) | 借真栏 reparent（原项目） |
| QQ | `com.tencent.mobileqq` | 9.2.85+ (13860+) | 借真栏 reparent（原项目） |
| **Bilibili** | `tv.danmaku.bili` | 9.13.0 (9130500+) | **GLM 新增**：真栏原地隐藏 + 自绘双 pill（左 4 tab + 右发布键），setCurrentItem/getCurrentItem 驱动，routeTo 学习发布路由 |
| **小黑盒** | `com.max.xiaoheihe` | 主页底栏 View 版 | **GLM 新增**：真栏原地隐藏 + 自绘三 pill（左 4 tab 快照 + 右发布键），快照借原生帧动画，宽度默认 0.5（管理器 0.2-0.9 可调） |

## 构建

```bash
./setup-tools.sh   # 下载 aapt2/d8/android.jar/libxposed api（需 JDK 17+，javac 可用即可）
./build.sh         # 产物 LiquidGlass-<version>.apk
```

## License

MIT（原项目）— 致谢 [sjtt2/HeyBox-LiquidGlass](https://github.com/sjtt2/HeyBox-LiquidGlass)、[tiann/KernelSU](https://github.com/tiann/KernelSU)

## v0.4.0 — 管理器 GUI

- 内置设置面板（KernelSU 管理器风格，**Mat / Miuix 双主题**可切换）
- 三页：主页 / App 配置器 / 设置
- per-app 参数：启用、宽度、底部偏移、模糊强度、反射色调强度
- 修复：已在主页时再点主页按钮 → 触发原生刷新
- 配置通道：LSPosed RemotePreferences + XSharedPreferences 双写
- **包名变更**：`io.github.liuran001.mmliquidglass` → `dongder.super.liquid`，
  旧版需在 LSPosed 停用后卸载，再安装本版并重新勾选作用域

UI 骨架参考 LSPosed / KernelSU 管理器（GPL-3.0），致谢原项目。
