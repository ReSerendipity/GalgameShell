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