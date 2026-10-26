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
        ADVHD(Route.A, null),        // あかべぇそふとつぅ 系 Windows 引擎（A 路由）
        SYSTEMNNN(Route.A, null),    // SystemNNN 系（A 路由）
        BGI(Route.A, null),          // BGI/Ethornell 系（A 路由）
        CMVS(Route.A, null),         // CMVS 系（A 路由）
        UNITY(Route.A, null),        // Unity 独立游戏（A 路由）
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
        return findSubDir(files, dirName) != null;
    }

    /** 在直接子项中找同名目录（忽略大小写），找不到返回 null。 */
    private static File findSubDir(File[] files, String dirName) {
        for (File f : files) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase(dirName)) return f;
        }
        return null;
    }

    /**
     * 目录内是否含 Ren'Py 脚本/归档（判据与 {@code RenPyEngine.looksLikeRenPyGame} 一致：
     * .rpy / .rpym / .rpyc / .rpa / .rpyb）。
     */
    private static boolean hasRenPyContent(File dir) {
        File[] fs = (dir == null) ? null : dir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (n.endsWith(".rpy") || n.endsWith(".rpym") || n.endsWith(".rpyc")
                    || n.endsWith(".rpa") || n.endsWith(".rpyb")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 引擎运行时目录名（KR2 的 getWritablePath 会在<b>游戏源目录</b>里创建
     * {@code .preference/}、{@code files/}（KrkrEngine javadoc 有载），里面带
     * XP3 魔数的存档封包；wine 路线的存档落在 S: 重定向不落源目录。这些目录
     * 不是游戏——被当游戏目录选中/扫到时会误判 KIRIKIRI 并入库成打不开的条目
     * （真机实测「files」「.preference」两条目即此来源，2026-10-25）。
     */
    public static boolean isRuntimeDirName(String name) {
        if (name == null || name.isEmpty()) return false;
        String n = name.toLowerCase();
        return n.equals(".preference") || n.equals("files") || n.equals("savedata")
                || n.equals("savedata ") || n.equals(".trash");
    }

    /**
     * 解压"套娃"归一：归档解包常产出「外层目录里只有一个包装子目录」的形态
     * （在手机上用文件管理器解压尤其常见）。用户选中外层时 {@link #detect} 靠浅层下钻
     * 仍能认引擎，但导入的复制与 {@code findExecutable} 只看顶层，会复制出一个套娃目录
     * 并报缺 exe（2026-10-26 真机：《椎名真穂のヒミツ》选外层 → UNKNOWN +
     * galgame_exe_missing）。这里把「只有一个子目录、且本层无任何游戏标记」的链逐层剥掉。
     *
     * @param dir 用户选中的目录；不可读或无套娃时原样返回
     */
    public static File resolveGameRoot(File dir) {
        File cur = dir;
        for (int depth = 0; depth < 4; depth++) {
            File[] children = cur.listFiles();
            if (children == null || children.length != 1) break;
            File only = children[0];
            if (!only.isDirectory() || isRuntimeDirName(only.getName())) break;
            if (hasGameMarkers(cur)) break;
            cur = only;
        }
        return cur;
    }

    private static boolean hasGameMarkers(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".exe") || n.endsWith(".xp3") || n.endsWith(".ypf") || n.endsWith(".rpa")
                    || n.endsWith("_data") || n.endsWith(".wolf") || n.endsWith(".rgssad")
                    || n.endsWith(".cso") || n.endsWith(".pbp") || n.endsWith(".nsa")
                    || n.equals("unityplayer.dll") || n.equals("www") || n.equals("psp_game")
                    || n.equals("game") || n.equals("tyrano") || n.equals("nscript.dat")) return true;
        }
        return false;
    }

    /**
     * 识别游戏目录的引擎并给出路由（B1 优先级裁决）。
     * @param gameDir 游戏根目录（A3 复制后的容器外/容器内路径）
     */
    public static Engine detect(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory()) return Engine.UNKNOWN;
        if (isRuntimeDirName(gameDir.getName())) return Engine.UNKNOWN;
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
        // 1b) Ren'Py 标准布局：项目根下 game/ 子目录内含脚本/归档。
        //     真实 Ren'Py 游戏把 .rpa/.rpyc 都放在 game/ 里，根目录只有启动器 .exe，
        //     仅扫根目录会漏判成 UNKNOWN，导致内置 Ren'Py 引擎永远选不上。
        //     判据与 RenPyEngine.looksLikeRenPyGame 保持一致。
        File gameSubDir = findSubDir(files, "game");
        if (gameSubDir != null && hasRenPyContent(gameSubDir)) return Engine.RENPY;
        // 2) 文件名/特征文件：TyranoScript / ONScripter
        for (File f : files) {
            String name = f.getName();
            String lowerName = name.toLowerCase();
            // 闭源 Windows 引擎指纹：仅用于 A 路由预设选择与展示名，不改变运行方式
            if (lowerName.equals("unityplayer.dll") || lowerName.equals("vk_swiftshader.dll")) return Engine.UNITY;
            if (lowerName.endsWith(".ypf")) return Engine.YURIS;
            if (lowerName.endsWith(".exe") && lowerName.contains("advhd")) return Engine.ADVHD;
            if (lowerName.startsWith("nnnconfig") || lowerName.equals("systemnnn.exe")) return Engine.SYSTEMNNN;
            if (lowerName.equals("bgi.exe") || lowerName.equals("bgi_tsk.exe") || lowerName.equals("bhvc.exe")) return Engine.BGI;
            if (lowerName.equals("cmvs.exe") || lowerName.equals("cmvs32.exe") || lowerName.equals("cmvs64.exe")) return Engine.CMVS;
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
        // Unity 备用布局：<Game>_Data/globalgamemanagers
        for (File f : files) {
            if (f.isDirectory() && f.getName().toLowerCase().endsWith("_data")
                    && new File(f, "globalgamemanagers").isFile()) return Engine.UNITY;
        }

        // 2b) 浅层下钻（仅一层）：真实发布包常把 exe/资源收在单层子目录，
        // 导入时不做深扫会一律判 UNKNOWN，导致预设与展示名失准。
        for (File d : files) {
            if (!d.isDirectory() || isRuntimeDirName(d.getName())) continue;
            File[] sub = d.listFiles();
            if (sub == null) continue;
            for (File f : sub) {
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase();
                if (n.endsWith(".xp3") && startsWith(f, XP3_MAGIC)) return Engine.KIRIKIRI;
                if (n.endsWith(".rpa") && (startsWith(f, RPA_MAGIC) || startsWith(f, ARC_MAGIC))) return Engine.RENPY;
                if (n.endsWith(".ypf")) return Engine.YURIS;
                if (n.equals("unityplayer.dll") || n.equals("vk_swiftshader.dll")) return Engine.UNITY;
                if (n.equals("siglusengine.exe")) return Engine.SIGLUS;
                if (n.endsWith(".exe") && n.contains("advhd")) return Engine.ADVHD;
                if (n.equals("bgi.exe") || n.equals("bgi_tsk.exe") || n.equals("bhvc.exe")) return Engine.BGI;
                if (n.startsWith("nnnconfig") || n.equals("systemnnn.exe")) return Engine.SYSTEMNNN;
                if (n.equals("cmvs.exe") || n.equals("cmvs32.exe") || n.equals("cmvs64.exe")) return Engine.CMVS;
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
     * 启动期重检测：先按常规浅扫，未命中再有限深度下钻。
     *
     * <p>导入登记可能因「xp3 在子目录」而误判为 UNKNOWN；启动时深层扫描把它救回原生路线。
     * 仅用于 {@link GameLauncher} 的升级判定，导入本身仍用 {@link #detect(File)}，
     * 以免提前改写登记值、失去升级日志与 override 语义。
     */
    public static Engine detectDeep(File gameDir) {
        return detectDeep(gameDir, 0, new int[]{4000});
    }

    private static Engine detectDeep(File dir, int depth, int[] budget) {
        if (dir == null || !dir.isDirectory()) return Engine.UNKNOWN;
        Engine hit = detect(dir);
        if (hit != Engine.UNKNOWN) return hit;
        if (depth >= 3) return Engine.UNKNOWN;
        File[] files = dir.listFiles();
        if (files == null) return Engine.UNKNOWN;
        for (File f : files) {
            if (budget[0]-- <= 0) return Engine.UNKNOWN;
            if (!f.isDirectory()) continue;
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (n.equalsIgnoreCase("savedata") || n.equalsIgnoreCase("save")
                    || n.equalsIgnoreCase("plugin")) continue;
            Engine sub = detectDeep(f, depth + 1, budget);
            if (sub != Engine.UNKNOWN) return sub;
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

    /** 面向用户的引擎展示名（库/详情/导入向导统一使用）。 */
    public static String displayName(Engine e) {
        if (e == null) return "未知";
        switch (e) {
            case KIRIKIRI:   return "Kirikiri";
            case RENPY:      return "Ren'Py";
            case TYRANO:     return "Tyrano";
            case ONSCRIPTER: return "ONScripter";
            case RPGMAKER2K: return "RPG Maker 2k";
            case RPGMAKERRGSS: return "RPG Maker XP/VX";
            case RPGMAKERMV: return "RPG Maker MV";
            case WOLFRPG:    return "Wolf RPG";
            case PSP:        return "PSP";
            case SIGLUS:     return "Siglus";
            case YURIS:      return "YURIS";
            case ARTEMIS:    return "Artemis";
            case CATSYSTEM2: return "CatSystem2";
            case ADVHD:      return "AdvHD";
            case SYSTEMNNN:  return "SystemNNN";
            case BGI:        return "BGI";
            case CMVS:       return "CMVS";
            case UNITY:      return "Unity";
            default:         return "未识别";
        }
    }
}
