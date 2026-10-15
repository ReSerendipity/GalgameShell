import com.winlator.galgame.GalgameLanguageDetector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GalgameLanguageDetector.detectLocale() 的纯 JVM 回归（无需 Android/设备）。
 *
 * 覆盖点：
 *  - 强信号关键字命中（汉化/简中/chs/cht/zh-cn/chinese/cn，忽略大小写）-> zh_CN
 *  - CJK 汉字弱信号（含扩展 A/B 代理对）-> zh_CN
 *  - 无信号/纯假名/空输入 -> 默认 ja_JP（保守策略：不误伤日语游戏）
 *
 * 用法：由 tools/run_detect_test.sh 统一编译运行（复制真实源码后编译，测的始终是当前代码）。
 */
public class LanguageDetectorTest {

    static int pass = 0, fail = 0;
    static Path root;

    static void check(String name, File folder, String exe, String expect) {
        String got = GalgameLanguageDetector.detectLocale(folder, exe);
        boolean ok = got.equals(expect);
        if (ok) { pass++; System.out.println("  PASS  " + name + " -> " + got); }
        else    { fail++; System.out.println("  FAIL  " + name + " -> got " + got + ", expect " + expect); }
    }

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("lang_detect_test");
        File base = root.toFile();
        System.out.println("temp root: " + base + "\n");

        // 1) 空输入：null 文件夹 -> 默认日语
        check("null folder", null, "game.exe", GalgameLanguageDetector.JA_LOCALE);

        // 2) 强信号：路径/文件夹名（不区分大小写）
        check("hint hanhua",  new File(base, "某游戏 汉化补丁版"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint jianzhong", new File(base, "某游戏 简中"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint chs exe", new File(base, "SomeGame"), "patch.chs.exe", GalgameLanguageDetector.ZH_LOCALE);
        check("hint cht dir", new File(base, "CHT_release"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint zh-cn",   new File(base, "zh-CN pack"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint chinese", new File(base, "chinese edition"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint cn dir",  new File(base, "cn_release"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint cn exe (real device case HANA9cn.EXE)", new File(base, "Simple"), "HANA9cn.EXE", GalgameLanguageDetector.ZH_LOCALE);

        // 3) 弱信号：CJK 汉字（统一表意 / 扩展 A / 扩展 B 代理对）
        check("han unified",  new File(base, "逆転裁判"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("han ext-A",    new File(base, "\u3400\u3401test"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("han ext-B surrogate", new File(base, "\uD842\uDFB7野家"), null, GalgameLanguageDetector.ZH_LOCALE);

        // 4) 负例：无信号 / 纯假名 -> 默认日语
        check("no signal",    new File(base, "PlainGame"), "game.exe", GalgameLanguageDetector.JA_LOCALE);
        check("kana only",    new File(base, "とりの あくど"), "game.exe", GalgameLanguageDetector.JA_LOCALE);
        check("null exe",     new File(base, "Simple"), null, GalgameLanguageDetector.JA_LOCALE);

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }
}
