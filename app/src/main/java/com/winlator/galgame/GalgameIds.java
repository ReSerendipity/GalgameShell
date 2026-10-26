package com.winlator.galgame;

import java.util.Collection;

/**
 * 游戏 ID 派生（容器名 / S: 盘子目录 / 容器内 C:\galgame\&lt;id&gt; 路径共用）。
 *
 * <p>两条硬约束（都有真机证据，别"优化"掉）：
 * <ul>
 *   <li><b>必须 ASCII</b>：ID 会进 Wine 的 C: 路径。非 ASCII 主程序路径在 wine 启动器早期
 *       （prefix 加载前、ACP 未初始化）会被按 ANSI 解析成乱码宽字符而打不开文件
 *       （{@code box64-wine-launch-chain.md} §10.2，bgi_ryo 一案），目录路径同类风险。</li>
 *   <li><b>不能碰撞</b>：旧实现 {@code [^A-Za-z0-9._-] → '_'} 会把日文/中文目录名塌成
 *       一串下划线——同长度的不同名字得到同一 ID，进而容器同名；按名解析容器时会命中
 *       错的那个（2026-10-26 真机：坏导入的孤儿容器遮住了好容器，启动被误拒）。</li>
 * </ul>
 * 因此：剥离非 ASCII 时附 4 位稳定哈希（{@code String.hashCode} 为 JVM 规范保证），
 * 显示标题另走原名（见 {@code GalgameLibraryIndex.markContainerized} 的 title 参数），不受影响。
 */
public final class GalgameIds {

    private GalgameIds() {}

    public static String gameId(String folderName, Collection<String> existingIds) {
        if (folderName == null || folderName.isEmpty()) folderName = "game";
        String base = folderName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty() || base.equals("_")) base = "game";
        String id = base;
        if (!base.equals(folderName)) {
            id = base + "-" + String.format("%04x", folderName.hashCode() & 0xffff);
        }
        if (existingIds != null) {
            int n = 2;
            String probe = id;
            while (existingIds.contains(probe)) probe = id + "-" + (n++);
            id = probe;
        }
        return id;
    }
}
