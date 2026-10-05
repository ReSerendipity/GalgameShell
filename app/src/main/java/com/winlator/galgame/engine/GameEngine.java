package com.winlator.galgame.engine;

import android.content.Context;

/**
 * 一等游戏引擎抽象：把「怎么把一款游戏跑起来」这件事收敛成同一份契约。
 *
 * <p>本 fork 历史上存在<b>三条互不相通</b>的启动路径（见 DOCS/plan-engine-equivalence-ui.md §2.1）：
 * <ol>
 *   <li>内置原生引擎：{@link BuiltinEngine} → Kirikiroid2 / Ren'Py；</li>
 *   <li>外部 APK 唤起：{@link com.winlator.galgame.NativeRouteLauncher}；</li>
 *   <li>容器 / Wine 路线：直接 {@code startActivity(XServerDisplayActivity)}。</li>
 * </ol>
 * 其中 ①③ 都属于「我们自己的 APK 里跑」，理应等价；②涉及第三方 App，
 * 已拍板<b>不纳入本抽象</b>（我方能控的只有 Intent，没有运行时，谈「可用性/许可证」没有意义）。
 *
 * <p>本接口即在 ①③ 之上补出缺失的一等公民：容器路线从此是一个普通的 {@link GameEngine}
 * 实现，而不是散落在 UI 里的 {@code startActivity}。
 *
 * <p><b>零改铁律</b>：本包全部为新增类，唯一改动是让既有 {@link BuiltinEngine}
 * 多一个 {@code implements GameEngine} 声明，不触碰其行为。
 */
public interface GameEngine {

    /** 稳定标识符（落持久化索引用，不可随意改）。 */
    String id();

    /** 展示名（中文，面向用户）。 */
    String displayName();

    /** 上游项目地址（LGPL-2.1 源码提供义务，A6 / AC-18）。 */
    String upstreamUrl();

    /** 许可证标识。 */
    String license();

    /**
     * 运行时是否已就位。必须<b>纯探测</b>，不得触发 {@code System.loadLibrary}
     * （那是不可逆副作用）。
     */
    boolean isAvailable(Context context);

    /** 不可用时的原因（展示给使用者）；仅当 {@link #isAvailable} 为 false 时有意义。 */
    String unavailableReason(Context context);

    /**
     * 用本引擎启动游戏。
     *
     * @param request 启动请求；各实现按需取值（内置引擎只看 {@code gamePath}，
     *                容器引擎看 {@code container}/{@code containerId}）
     * @return true 表示已成功拉起
     */
    boolean launch(Context context, GameLaunchRequest request);

    /**
     * 该实现是否需要容器（决定是否要把 {@code container} 塞进请求、是否要校验 overlay exe）。
     * 供 UI 决定是否走「容器缺失提示」等特有分支。
     */
    default boolean requiresContainer() { return false; }
}
