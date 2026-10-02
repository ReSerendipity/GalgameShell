package com.winlator.galgame;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * B1 引擎指纹识别（Plan Part III §III.2，source-verified）。
 *
 * 路由：B = 原生播放器唤起（体验最好）；A = Winlator 模拟兜底（本 fork 内核，万能）。
 * 优先级裁决（命中 B 优先）：
 *   KiriKiri > Ren'Py > TyranoScript > ONScripter > Siglus/YU-RIS/Artemis/CatSystem2
 *
 * 检测手段：magic bytes 优先（.xp3 / .rpa / ARC-3.0），可执行名/特征文件辅助。
 * RK-11：Siglus / YU-RIS / Artemis / CatSystem2 仍为「可执行名 + 扩展名」启发式，待真实样本校准。
 */
public final class EngineDetector {

    public enum Route { A, B }

    public enum Engine {
        KIRIKIRI(Route.B, "Kirikiroid2"),   // org.tvp.kirikiri2
        RENPY(Route.B, "JoiPlay"),          // cyou.joiplay (+ renpy 插件)
        TYRANO(Route.B, "Tyranor"),         // com.akira.tyranoemu
        ONSCRIPTER(Route.B, "ONScripter"),  // jp.ogapee.onscripter.release
        RPGMAKER2K(Route.B, "EasyRPG"),     // org.easyrpg.player（RPG Maker 2000/2003）
        RPGMAKERRGSS(Route.B, "JoiPlay"),   // XP/VX/VX Ace：.rgssad/.rgss2a/.rgss3a
        RPGMAKERMV(Route.B, "JoiPlay"),     // MV/MZ：www/ + data/（JoiPlay 插件）
        WOLFRPG(Route.B, "JoiPlay"),        // Data/*.wolf
        PSP(Route.B, "PPSSPP"),             // PSP_GAME/ 或 .cso（大量 galgame 的 PSP 移植）
        SIGLUS(Route.A, null),
        YURIS(Route.A, null),
        ARTEMIS(Route.A, null),
        CATSYSTEM2(Route.A, null),
        UNKNOWN(Route.A, null);

        public final Route route;
        public final String nativePlayer; // null => 仅 A 路由（无公开一键 Intent）

        Engine(Route route, String nativePlayer) {
            this.route = route;
            this.nativePlayer = nativePlayer;
        }
    }

    // ---- Magic bytes（Plan Part III §III.2，已核实）----
    private static final byte[] XP3_MAGIC = {
            0x58, 0x50, 0x33, 0x0D, 0x0A, 0x20, 0x0A, 0x1A, (byte) 0x8B, 0x67, 0x01
    }; // "XP3\r\n \n\x1a\x8bg\x01"
    private static final byte[] RPA_MAGIC = "RPA-3.0 ".getBytes(); // 52 50 41 2D 33 2E 30 20
    private static final byte[] ARC_MAGIC = "ARC-3.0".getBytes();   // 41 52 43 2D 33 2E 30（Ren'Py 加密变体）

    private EngineDetector() {}

    /** 读取文件前 n 字节并与 magic 比对。 */
    private static boolean startsWith(File f, byte[] magic) {
        if (f == null || !f.isFile() || magic.length == 0) return false;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[magic.length];
            int n = in.read(buf);
            if (n < magic.length) return false;
            for (int i = 0; i < magic.length; i++) {
                if (buf[i] != magic[i]) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasSubDir(File[] files, String dirName) {
        for (File f : files) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase(dirName)) return true;
        }
        return false;
    }

    /**
     * 识别游戏目录的引擎并给出路由（B1 优先级裁决）。
     * @param gameDir 游戏根目录（A3 复制后的容器外/容器内路径）
     */
    public static Engine detect(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory()) return Engine.UNKNOWN;
        File[] files = gameDir.listFiles();
        if (files == null) return Engine.UNKNOWN;

        // 1) 字节级优先：KiriKiri(.xp3) / Ren'Py(.rpa)
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".xp3") && startsWith(f, XP3_MAGIC)) return Engine.KIRIKIRI;
            if (name.endsWith(".rpa") && (startsWith(f, RPA_MAGIC) || startsWith(f, ARC_MAGIC))) {
                return Engine.RENPY;
            }
        }
        // 2) 文件名/特征文件：TyranoScript / ONScripter
        for (File f : files) {
            String name = f.getName();
            if ("tyrano".equalsIgnoreCase(name) || name.endsWith(".nsa")
                    || ("index.html".equalsIgnoreCase(name) && hasSubDir(files, "tyrano"))
                    || ("data".equalsIgnoreCase(name) && hasSubDir(files, "scenario"))) {
                return Engine.TYRANO;
            }
            if ("nscript.dat".equalsIgnoreCase(name) || "0.txt".equalsIgnoreCase(name)
                    || "00.txt".equalsIgnoreCase(name) || "nscr_sec.dat".equalsIgnoreCase(name)
                    || name.endsWith(".nsa")) {
                return Engine.ONSCRIPTER;
            }
            // RPG Maker 2000/2003 → EasyRPG Player
            if ("rpg_rt.exe".equalsIgnoreCase(name) || "rpg_rt.ldb".equalsIgnoreCase(name)) {
                return Engine.RPGMAKER2K;
            }
            // RPG Maker XP/VX/VX Ace/MV/MZ（JoiPlay 插件）
            if (name.toLowerCase().endsWith(".rgssad") || name.toLowerCase().endsWith(".rgss2a")
                    || name.toLowerCase().endsWith(".rgss3a") || name.toLowerCase().endsWith(".rvdata2")) {
                return Engine.RPGMAKERRGSS;
            }
            if (f.isDirectory() && "www".equalsIgnoreCase(name) && f.listFiles() != null
                    && hasSubDir(f.listFiles(), "data")) {
                return Engine.RPGMAKERMV;
            }
            // Wolf RPG Editor（JoiPlay 插件）
            if (name.toLowerCase().endsWith(".wolf")) return Engine.WOLFRPG;
            // PSP 镜像解包目录 / 压缩镜像 → PPSSPP
            if (f.isDirectory() && "psp_game".equalsIgnoreCase(name)) return Engine.PSP;
            if (name.toLowerCase().endsWith(".cso") || name.toLowerCase().endsWith(".pbp")) {
                return Engine.PSP;
            }
        }
        // 3) A 路由可执行名（RK-11：启发式，待样本校准）
        for (File f : files) {
            String n = f.getName();
            if ("SiglusEngine.exe".equalsIgnoreCase(n)) return Engine.SIGLUS;
            if ("YurisEngine.exe".equalsIgnoreCase(n)) return Engine.YURIS;
            if ("REALLIVE.exe".equalsIgnoreCase(n)) return Engine.ARTEMIS;
            if ("CatSystem2.exe".equalsIgnoreCase(n)) return Engine.CATSYSTEM2;
        }
        return Engine.UNKNOWN;
    }

    /**
     * 加密特征检测（A5：仅检测 + 提示，不破解）。
     * @return 命中的标记描述（空列表 = 未检测到）
     */
    public static List<String> encryptionMarkers(File gameDir) {
        List<String> marks = new ArrayList<>();
        if (gameDir == null || !gameDir.isDirectory()) return marks;
        File[] files = gameDir.listFiles();
        if (files == null) return marks;
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if ("xp3filter.tjs".equals(n)) marks.add("xp3filter.tjs（KiriKiri 加密脚本）");
            if (n.endsWith(".rpa") && startsWith(f, ARC_MAGIC)) marks.add("ARC-3.0（Ren'Py 加密归档）");
            // .sgd/.yrg/.cst 本身即加密容器格式 → 标记「可能加密」（MVP 不验证，仅提示）
            if (n.endsWith(".sgd")) marks.add(".sgd（Siglus 加密容器，可能加密）");
            if (n.endsWith(".yrg")) marks.add(".yrg（YU-RIS 加密容器，可能加密）");
            if (n.endsWith(".cst")) marks.add(".cst（CatSystem2 加密容器，可能加密）");
        }
        return marks;
    }

    public static boolean isEncrypted(File gameDir) {
        return !encryptionMarkers(gameDir).isEmpty();
    }
}
