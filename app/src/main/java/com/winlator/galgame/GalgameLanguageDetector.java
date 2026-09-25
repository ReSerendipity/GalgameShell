package com.winlator.galgame;

import java.io.File;
import java.util.Locale;

/**
 * 中文游戏检测（最高优先：中文显示修复产品化，2026-09-25）。
 *
 * 保守策略：仅在「强关键字信号」或「名称含汉字（CJK 统一表意文字）」时判为中文，
 * 否则维持默认日语（ja_JP.UTF-8），避免误把日语 galgame 改成中文而破坏原有兼容。
 *
 * 检测来源：游戏源文件夹路径/名称 + 可选 exe 名称（stageGameFiles 阶段更准）。
 * 不读取游戏二进制内容（不可靠），仅用文件名/路径信号。
 */
public final class GalgameLanguageDetector {

    private GalgameLanguageDetector() {}

    public static final String ZH_LOCALE = "zh_CN.UTF-8";
    public static final String JA_LOCALE = "ja_JP.UTF-8";

    // 强信号关键字（路径/名称含这些 → 中文），忽略大小写
    private static final String[] STRONG_HINTS = {
            "cn", "chs", "cht", "简体", "简中", "漢化", "汉化", "中文",
            "chinese", "zh-cn", "zh_cn", "zhcn", "zhongwen", "hanhua"
    };

    /**
     * 检测游戏源文件夹的语言（返回建议 locale）。
     * @param sourceFolder 游戏源文件夹
     * @param exeName      可选 exe 文件名（stageGameFiles 阶段更准）
     * @return {@link #ZH_LOCALE} 或 {@link #JA_LOCALE}
     */
    public static String detectLocale(File sourceFolder, String exeName) {
        if (sourceFolder == null) return JA_LOCALE;

        String path = lower(sourceFolder.getAbsolutePath());
        String exe = lower(exeName);

        for (String h : STRONG_HINTS) {
            if (path.contains(h) || exe.contains(h)) return ZH_LOCALE;
        }

        // 弱信号：文件夹名或 exe 名含 CJK 汉字
        if (containsHan(sourceFolder.getName()) || containsHan(exeName)) return ZH_LOCALE;

        return JA_LOCALE;
    }

    /** 文件夹名/名称是否含 CJK 统一表意文字（含扩展 A/B 与兼容区）。 */
    private static boolean containsHan(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            int c = s.codePointAt(i);
            if (Character.isHighSurrogate(s.charAt(i)) && i < s.length() - 1) i++; // 跳过代理对低字节
            if ((c >= 0x4E00 && c <= 0x9FFF)          // CJK 统一表意文字
                    || (c >= 0x3400 && c <= 0x4DBF)   // 扩展 A
                    || (c >= 0x20000 && c <= 0x2FA1F) // 扩展 B+（代理对）
                    || (c >= 0xF900 && c <= 0xFAFF)) { // 兼容表意
                return true;
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
