import com.winlator.galgame.EngineDetector;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EngineDetector.detect() 的纯 JVM 验证（无需 Android/模拟器）。
 *
 * 覆盖点：
 *  - 本次修复的 1b 段：项目根下 game/ 子目录含 Ren'Py 脚本/归档 → RENPY
 *  - 原有字节级路径不被破坏（根目录下带 magic 的 .rpa）
 *  - KiriKiri 优先级高于 Ren'Py（不得被 1b 段抢先）
 *  - 负例不误判（只有启动器 .exe / game/ 里没有 Ren'Py 内容）
 */
public class DetectTest {

    static int pass = 0, fail = 0;
    static Path root;

    static final byte[] XP3 = {
        0x58, 0x50, 0x33, 0x0D, 0x0A, 0x20, 0x0A, 0x1A, (byte) 0x8B, 0x67, 0x01
    };

    static byte[] cat(byte[] head, String tail, int total) {
        byte[] t = tail.getBytes();
        byte[] out = new byte[Math.max(head.length + t.length, total)];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(t, 0, out, head.length, Math.min(t.length, out.length - head.length));
        return out;
    }

    static File mk(String rel) throws Exception {
        File f = root.resolve(rel).toFile();
        f.getParentFile().mkdirs();
        return f;
    }

    static void file(String rel, byte[] content) throws Exception {
        File f = mk(rel);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(content); }
    }

    static void emptyFile(String rel) throws Exception { file(rel, new byte[0]); }

    static void check(String name, String dirRel, String expect) {
        File dir = root.resolve(dirRel).toFile();
        String got = EngineDetector.detect(dir).name();
        boolean ok = got.equals(expect);
        if (ok) { pass++; System.out.println("  PASS  " + name + " -> " + got); }
        else    { fail++; System.out.println("  FAIL  " + name + " -> got " + got + ", expect " + expect); }
    }

    static void checkRoot(String name, String dirRel, String expectRel) {
        File got = EngineDetector.resolveGameRoot(root.resolve(dirRel).toFile());
        File exp = root.resolve(expectRel).toFile();
        boolean ok = got.getAbsolutePath().equals(exp.getAbsolutePath());
        if (ok) { pass++; System.out.println("  PASS  " + name + " -> " + expectRel); }
        else    { fail++; System.out.println("  FAIL  " + name + " -> got " + got + ", expect " + exp); }
    }

    static void checkUnwrapped(String name, String dirRel, String expect) {
        File dir = EngineDetector.resolveGameRoot(root.resolve(dirRel).toFile());
        String got = EngineDetector.detect(dir).name();
        boolean ok = got.equals(expect);
        if (ok) { pass++; System.out.println("  PASS  " + name + " -> " + got); }
        else    { fail++; System.out.println("  FAIL  " + name + " -> got " + got + ", expect " + expect); }
    }

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("detect_test");
        System.out.println("temp root: " + root + "\n");

        // 用例名一律 ASCII 稳定标识（E01…E14）：中文标签在 Windows 控制台会随代码页变成乱码，
        // 一旦某条断言失败就认不出断了哪一条。中文语义说明保留在每条用例上方的注释里。
        // 1) 标准 Ren'Py 布局：根目录只有启动器 exe，脚本在 game/ 里（本次修复的目标）
        file("std_game/MyGame.exe", "MZ".getBytes());
        emptyFile("std_game/game/script.rpy");
        check("E01 script-rpy-under-game", "std_game", "RENPY");

        // 2) 标准布局 + 归档 .rpa（真实发行版的典型形态）
        file("rpa_game/MyGame.exe", "MZ".getBytes());
        file("rpa_game/game/archive.rpa", cat("RPA-3.0 ".getBytes(), "\n0000000000000000", 64));
        check("E02 archive-rpa-under-game", "rpa_game", "RENPY");

        // 3) 只有 .rpyc（源已被剥离的发行版）
        emptyFile("rpyc_game/game/script.rpyc");
        check("E03 script-rpyc-under-game", "rpyc_game", "RENPY");

        // 4) 其它 Ren'Py 扩展名
        emptyFile("rpym_game/game/foo.rpym");
        check("E04 foo-rpym-under-game", "rpym_game", "RENPY");
        emptyFile("rpyb_game/game/foo.rpyb");
        check("E05 foo-rpyb-under-game", "rpyb_game", "RENPY");

        // 5) 大写 GAME 目录（忽略大小写）
        emptyFile("upper_game/GAME/script.rpy");
        check("E06 uppercase-GAME-dir", "upper_game", "RENPY");

        // 6) 原有字节级路径仍生效： .rpa 直接在游戏根目录
        file("rootrpa_game/archive.rpa", cat("RPA-3.0 ".getBytes(), "\n0000000000000000", 64));
        check("E07 rpa-at-game-root-RPA3", "rootrpa_game", "RENPY");

        // 7) 加密变体 ARC-3.0 在根目录
        file("arc_game/patch.rpa", cat("ARC-3.0".getBytes(), "\n0000000000000000", 64));
        check("E08 rpa-at-game-root-ARC3-encrypted", "arc_game", "RENPY");

        // 8) KiriKiri 优先级：根有 .xp3(magic) 同时又有 game/*.rpy → 必须判 KIRIKIRI
        file("both_game/data.xp3", cat(XP3, "\npadding", 64));
        emptyFile("both_game/game/script.rpy");
        check("E09 kirikiri-beats-renpy-priority", "both_game", "KIRIKIRI");

        // 9) 纯 KiriKiri
        file("krkr_game/data.xp3", cat(XP3, "\npadding", 64));
        check("E10 pure-kirikiri-xp3", "krkr_game", "KIRIKIRI");

        // 10) 负例：只有启动器，没有 game/
        file("exe_only/MyGame.exe", "MZ".getBytes());
        check("E11 exe-only-no-game-dir", "exe_only", "UNKNOWN");

        // 11) 负例：有 game/ 但里面没有 Ren'Py 内容（不得误判）
        emptyFile("empty_game/game/readme.txt");
        check("E12 game-dir-without-renpy-content", "empty_game", "UNKNOWN");

        // 12) 负例：game/ 是文件而不是目录（findSubDir 应跳过）
        emptyFile("gamefile_game/game");
        check("E13 game-is-file-not-dir", "gamefile_game", "UNKNOWN");

        // 13) 负例：Siglus（走 A 路由）
        emptyFile("siglus_game/SiglusEngine.exe");
        check("E14 siglus-engine", "siglus_game", "SIGLUS");

        // 14) 解压"套娃"归一（2026-10-26 真机形态：外层只有一个同名包装子目录）
        file("wrap_unity/wrap_inner/Shiina.exe", "MZ".getBytes());
        emptyFile("wrap_unity/wrap_inner/UnityPlayer.dll");
        emptyFile("wrap_unity/wrap_inner/Shiina_Data/globalgamemanagers");
        checkRoot("E15 unwrap-single-wrapper-dir", "wrap_unity", "wrap_unity/wrap_inner");
        checkUnwrapped("E16 detect-after-unwrap-unity", "wrap_unity", "UNITY");
        // 未归一时 detect 仍应靠浅层下钻认 UNITY（回归护栏：归一不得改变 detect 语义）
        check("E17 detect-on-wrapper-still-unity", "wrap_unity", "UNITY");

        // 15) 平铺根目录不得被剥（原样返回）
        file("uni_flat/Game.exe", "MZ".getBytes());
        emptyFile("uni_flat/UnityPlayer.dll");
        checkRoot("E18 flat-root-not-unwrapped", "uni_flat", "uni_flat");

        // 16) 本层已有游戏标记（exe）时不得下钻，即使只有一个子目录
        file("mix_root/Game.exe", "MZ".getBytes());
        emptyFile("mix_root/sub/readme.txt");
        checkRoot("E19 markers-stop-descent", "mix_root", "mix_root");

        // 17) 唯一子目录是运行时目录名（files/）时不得下钻
        emptyFile("rt_wrap/files/save.dat");
        checkRoot("E20 runtime-dir-child-not-unwrapped", "rt_wrap", "rt_wrap");

        // 18) 两层套娃逐层剥到真根
        emptyFile("w2/w2b/inner/UnityPlayer.dll");
        checkRoot("E21 unwrap-two-levels", "w2", "w2/w2b/inner");

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }
}
