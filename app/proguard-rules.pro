# SuperLiquid release keeps.

# LSPosed entry point — referenced only from META-INF/xposed/java_init.list,
# invisible to R8.
-keep class io.github.liuran001.mmliquidglass.LiquidGlassModule { *; }
-keep class io.github.liuran001.mmliquidglass.** { *; }

# libxposed service binder plumbing (reflection inside the AAR).
-keep class io.github.libxposed.service.** { *; }

# R8 for Compose / Material3
-dontwarn androidx.compose.**
