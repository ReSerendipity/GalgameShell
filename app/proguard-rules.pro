# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate

# GalgameShell：内置原生引擎（Kirikiroid2 / cocos2dx）。
# libgame.so 的 JNI 按硬编码类名 org/tvp/kirikiri2/KR2Activity 反射回调，
# 原生方法按名字绑定（RegisterNatives 短名），压缩器不得裁掉它们。
-keep class org.tvp.kirikiri2.KR2Activity { *; }
-keep class org.tvp.kirikiri2.DummyEdit { *; }
-keep class org.cocos2dx.lib.** { *; }
-keepclassmembers class org.cocos2dx.lib.** { native *; }
-keep class com.winlator.galgame.engine.** { *; }

# GalgameShell：内置 Ren'Py 运行时 Java 壳（SDL2 / renpy / jnius / jtar）。
# librenpython.so 内静态链入的 SDL2 在 JNI_OnLoad 对 org/libsdl/app/SDLActivity
# 做整表 RegisterNatives——R8 shrinking 会裁掉“只被 C 调用”的方法，任一缺失整表即失败
# （真机实证：E SDL: Failed to register methods of org/libsdl/app/SDLActivity → native crash）。
# C 侧另会 GetMethodID 回调 PythonSDLActivity.preparePython（RenPyActivity 覆写：解包引擎+写 game_dir.txt）。
-keep class org.libsdl.app.** { *; }
-keep class org.renpy.android.** { *; }
-keep class org.jnius.** { *; }
-keep class org.kamranzafar.jtar.** { *; }
-keep class com.winlator.renpy.** { *; }
-keepclassmembers class org.libsdl.app.** { native *; }
-keepclassmembers class org.renpy.android.** { native *; }