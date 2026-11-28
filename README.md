# SuperLiquid — 液态玻璃底栏

LSPosed 模块（libxposed API 102），为支持的应用提供 iOS 26 风格液态玻璃底栏。主控支持 Material、Miuix 和 LiquidGlass 主题。

原项目：[liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass) · [下载与更新日志](https://github.com/DONGDERS/SuperLiquid/releases)

## 支持的 App

| App | 包名 | 版本 | 方案 |
|---|---|---|---|
| 微信 | `com.tencent.mm` | 8.0.72+ (3085+) | 借真栏 reparent（原项目） |
| QQ | `com.tencent.mobileqq` | 9.2.85+ (13860+) | 借真栏 reparent（原项目） |
| **Bilibili** | `tv.danmaku.bili` | 9.13.0 (9130500+) | 真栏原地隐藏 + 自绘双 pill（左 4 tab + 右发布键），setCurrentItem/getCurrentItem 驱动，routeTo 学习发布路由 |
| **小黑盒** | `com.max.xiaoheihe` | 主页底栏 View 版 | 真栏原地隐藏 + 自绘三 pill（左 4 tab 快照 + 右发布键），快照借原生帧动画，宽度默认 0.5（管理器 0.2-0.9 可调） |
| **酷狗音乐** | `com.kugou.android` | 20.5.3 (20539) | 复用共享液态玻璃底栏与动效，保留原生页面切换 |
| **淘宝** | `com.taobao.taobao` | 10.62.20 (849) | 接入原生 TabHost，复用共享液态玻璃底栏与动效 |
| **拼多多** | `com.xunmeng.pinduoduo` | 7.96.0 (79600) | 接入原生底栏，复用共享液态玻璃底栏与动效 |
| **铁路 12306** | `com.MobileTicket` | 5.9.6.8 (280) | 镜像原生底栏，保留原生切页；可在应用配置中调整首页「通知上移」 |
| **支付宝** | `com.eg.android.AlipayGphone` | 12.12.20 | 接入原生首页底栏，复用共享液态玻璃底栏与动效 |

## 使用配置

- 在「设置 → 性能」选择「标准」或「极致」。「极致」使用 Glasmio 实现；未能挂载时回退到标准实现。也可以在应用配置里覆盖全局模式。
- 在「设置 → 界面风格」切换主控主题。主控主题与宿主底栏性能模式相互独立。

## 构建

请使用 GitHub Actions 构建，详见 [AGENTS.md](AGENTS.md)。

## License

MIT（继承自原项目 [liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)）。

致谢：[NEORUAA/Glasmio](https://github.com/NEORUAA/Glasmio)、[sjtt2/HeyBox-LiquidGlass](https://github.com/sjtt2/HeyBox-LiquidGlass)、[tiann/KernelSU](https://github.com/tiann/KernelSU)。Glasmio 使用 Apache-2.0，相关许可见 [随附文件](app/src/main/assets/licenses/glasmio-0.1.4)。

该项目由GLM(以前使用)与GPT强力驱动
