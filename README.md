# SuperLiquid — 液态玻璃底栏

> **构建说明：** 当前本地 Android / Termux / proot 环境无法构建 SuperLiquid，请使用 GitHub CI（GitHub Actions）。 详见 [AGENTS.md](AGENTS.md)。

LSPosed 模块（libxposed API 102）：iOS 26 风格液态玻璃底部导航栏。

**由 GLM（智谱）编写扩展** · 原项目：[liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)

## 支持的 App

| App | 包名 | 版本 | 方案 |
|---|---|---|---|
| 微信 | `com.tencent.mm` | 8.0.72+ (3085+) | 借真栏 reparent（原项目） |
| QQ | `com.tencent.mobileqq` | 9.2.85+ (13860+) | 借真栏 reparent（原项目） |
| **Bilibili** | `tv.danmaku.bili` | 9.13.0 (9130500+) | **GLM 新增**：真栏原地隐藏 + 自绘双 pill（左 4 tab + 右发布键），setCurrentItem/getCurrentItem 驱动，routeTo 学习发布路由 |
| **小黑盒** | `com.max.xiaoheihe` | 主页底栏 View 版 | **GLM 新增**：真栏原地隐藏 + 自绘三 pill（左 4 tab 快照 + 右发布键），快照借原生帧动画，宽度默认 0.5（管理器 0.2-0.9 可调） |
| **酷狗音乐** | `com.kugou.android` | 20.5.3 (20539) | 复用共享液态玻璃底栏与动效，保留原生页面切换 |

| **淘宝** | `com.taobao.taobao` | 10.62.20 (849) | 接入原生 TabHost，复用共享液态玻璃底栏与动效 |
| **拼多多** | `com.xunmeng.pinduoduo` | 7.96.0 (79600) | 接入原生底栏，复用共享液态玻璃底栏与动效 |
| **铁路 12306** | `com.MobileTicket` | 5.9.6.8 (280) | 镜像原生底栏，保留原生切页；可在应用配置中调整首页「通知上移」 |
## 宿主适配必查（真机）

- [ ] **拖动玻璃选中色**：长按拖过各标签，再松手或取消。拖动中的选中态高亮必须跟随宿主原生选中色，不能错误回退成兜底绿色（原生选中色本身为绿色的宿主除外）。
- [ ] **拖动松手落点**：12306 跨标签拖动后松手，检查选中态直接取原生按钮状态，胶囊不先退回旧项再前往新项；重复测试左右方向及隐藏/排序后的标签。
- [ ] **运行时调参**：宿主保持运行（含分屏），调整玻璃参数后检查自动更新；切回宿主无需杀进程。核对低档位高度逐档移动，B 站/小黑盒同时检查独立发布按钮。
- [ ] **配置生效**：分别调高、调低高度、横向偏移、宽度和玻璃大小，返回宿主确认变化；再检查模糊、反射色调、饱和度、光泽、边框和宿主专用配置。关闭采样的页面不用于判断背景模糊和饱和度。

- [ ] **切页后底部留空**：支付宝启动后切换各标签再回首页，并从二级页面返回，检查异步重布局后内容区仍延伸到底部；12306 宽度反复往返调节，同一数值应始终得到相同宽度。
## 参数更新
- 所有宿主的玻璃参数监听 LSPosed RemotePreferences 变化，在主线程合并刷新布局和渲染；返回宿主时补读配置，兼容 XSharedPreferences 文件监听后备通道。
- 铁路 12306 内置默认高度为 3dp，通知上移为 16dp；已有自定义值保留，恢复默认使用新数值。
- 新测试包装完先报告版本与安装结果，再进行另行安排的真机 UI 验证。
## 构建


```bash
./setup-tools.sh   # 下载 aapt2/d8/android.jar/libxposed api（需 JDK 17+，javac 可用即可）
./build.sh         # 产物 LiquidGlass-<version>.apk
```

## License

MIT（继承自原项目 [liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)）—
另致谢参考项目 [sjtt2/HeyBox-LiquidGlass](https://github.com/sjtt2/HeyBox-LiquidGlass)、[tiann/KernelSU](https://github.com/tiann/KernelSU)


## v0.5.2-R2
- 修复了已知问题

## v0.5.2-R1
- 添加了对淘宝与拼多多的支持

## v0.5.1-R1
- 修复了已知 Bug
- 添加了对酷狗音乐的支持

## v0.4.9-R1 — UI 优化与问题修复

- 优化：主页布局，新增「版本信息」「状态」区块与双主题图标
- 优化：Miuix 状态大卡片勾选排版，Material 卡片视觉保持不变
- 优化：App 配置页显示应用图标、「+玻璃参数设置」独立分区
- 优化：App 页面新增搜索，支持应用名称与包名筛选
- 修复：Material 主题下 App 配置卡片点击区域过小的问题
- 修复：小黑盒缺少加号尺寸/位置参数的问题
- 修复：Miuix 固定底栏实时模糊被截断的问题
- 移除：Bilibili「隐藏原生底栏」开关（原生底栏默认隐藏）

## v0.4.0 — 管理器 GUI

- 内置设置面板（KernelSU 管理器风格，**Mat / Miuix 双主题**可切换）
- 三页：主页 / App 配置器 / 设置
- per-app 参数：启用、宽度、底部偏移、模糊强度、反射色调强度
- 修复：已在主页时再点主页按钮 → 触发原生刷新
- 配置通道：LSPosed RemotePreferences + XSharedPreferences 双写
- **包名变更**：`io.github.liuran001.mmliquidglass` → `dongder.super.liquid`，
  旧版需在 LSPosed 停用后卸载，再安装本版并重新勾选作用域

UI 骨架参考 LSPosed / KernelSU 管理器（GPL-3.0），致谢原项目。
