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

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("detect_test");
        System.out.println("temp root: " + root + "\n");

        // 1) 标准 Ren'Py 布局：根目录只有启动器 exe，脚本在 game/ 里（本次修复的目标）
        file("std_game/MyGame.exe", "MZ".getBytes());
        emptyFile("std_game/game/script.rpy");
        check("标准布局 game/script.rpy", "std_game", "RENPY");

        // 2) 标准布局 + 归档 .rpa（真实发行版的典型形态）
        file("rpa_game/MyGame.exe", "MZ".getBytes());
        file("rpa_game/game/archive.rpa", cat("RPA-3.0 ".getBytes(), "\n0000000000000000", 64));
        check("标准布局 game/archive.rpa", "rpa_game", "RENPY");

        // 3) 只有 .rpyc（源已被剥离的发行版）
        emptyFile("rpyc_game/game/script.rpyc");
        check("标准布局 game/script.rpyc", "rpyc_game", "RENPY");

        // 4) 其它 Ren'Py 扩展名
        emptyFile("rpym_game/game/foo.rpym");
        check("标准布局 game/foo.rpym", "rpym_game", "RENPY");
        emptyFile("rpyb_game/game/foo.rpyb");
        check("标准布局 game/foo.rpyb", "rpyb_game", "RENPY");

        // 5) 大写 GAME 目录（忽略大小写）
        emptyFile("upper_game/GAME/script.rpy");
        check("大写 GAME/ 目录", "upper_game", "RENPY");

        // 6) 原有字节级路径仍生效： .rpa 直接在游戏根目录
        file("rootrpa_game/archive.rpa", cat("RPA-3.0 ".getBytes(), "\n0000000000000000", 64));
        check("根目录直接放 .rpa (RPA-3.0)", "rootrpa_game", "RENPY");

        // 7) 加密变体 ARC-3.0 在根目录
        file("arc_game/patch.rpa", cat("ARC-3.0".getBytes(), "\n0000000000000000", 64));
        check("根目录 .rpa (ARC-3.0 加密变体)", "arc_game", "RENPY");

        // 8) KiriKiri 优先级：根有 .xp3(magic) 同时又有 game/*.rpy → 必须判 KIRIKIRI
        file("both_game/data.xp3", cat(XP3, "\npadding", 64));
        emptyFile("both_game/game/script.rpy");
        check("KiriKiri 与 Ren'Py 混合(优先级)", "both_game", "KIRIKIRI");

        // 9) 纯 KiriKiri
        file("krkr_game/data.xp3", cat(XP3, "\npadding", 64));
        check("纯 KiriKiri data.xp3", "krkr_game", "KIRIKIRI");

        // 10) 负例：只有启动器，没有 game/
        file("exe_only/MyGame.exe", "MZ".getBytes());
        check("仅启动器 exe（无 game/）", "exe_only", "UNKNOWN");

        // 11) 负例：有 game/ 但里面没有 Ren'Py 内容（不得误判）
        emptyFile("empty_game/game/readme.txt");
        check("game/ 内无 Ren'Py 内容", "empty_game", "UNKNOWN");

        // 12) 负例：game/ 是文件而不是目录（findSubDir 应跳过）
        emptyFile("gamefile_game/game");
        check("game 是文件而非目录", "gamefile_game", "UNKNOWN");

        // 13) 负例：Siglus（走 A 路由）
        emptyFile("siglus_game/SiglusEngine.exe");
        check("Siglus 引擎", "siglus_game", "SIGLUS");

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }
}
