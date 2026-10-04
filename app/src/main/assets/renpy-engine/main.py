# -*- coding: utf-8 -*-
#
# GalgameShell — 内置 Ren'Py 启动器（等价于 RAPT 的 private/main.py）
#
# 运行契约（已对 librenpython.so 的 start_python()、SDK 官方启动器 renpy.py 与
# renpy 源码逐条核实，详见 DOCS/native-engine-integration.md §4.5）：
#   * librenpython.so 的 start_python() 固定执行 getenv("ANDROID_PRIVATE")/main.py，
#     并 chdir 到该目录（sys.path[0] 与 PYTHONHOME 都指向它）。
#   * PythonSDLActivity.preparePython() 已把 ANDROID_PRIVATE 设为 getFilesDir()。
#   * 引擎位于 getFilesDir()/renpy/，CPython 标准库位于 getFilesDir()/lib/python3.12/，
#     python-for-android 的 android / jnius 包位于 getFilesDir()/lib/。
#   * 官方启动器契约：renpy.bootstrap / renpy.main 通过 renpy.__main__ 回调
#     path_to_gamedir / path_to_common / path_to_saves / path_to_logdir /
#     predefined_searchpath（bootstrap.py:334/385/396，main.py:349/350/430，
#     savetoken.py:301，script.py:212）。因此本文件必须：
#       1) 定义全部 path_to_* 函数；
#       2) 在 bootstrap() 之前执行 renpy.__main__ = sys.modules[__name__]
#          （官方 renpy.py 的 main() 同款；漏掉会报
#          "module '__main__' has no attribute 'path_to_gamedir'"）。
#
# 与官方启动器的差异：RAPT 把游戏数据打进 assets 并落在 ANDROID_PUBLIC/game；
# 本项目改为导入外部存储上的 Ren'Py 项目，游戏根目录由 RenPyActivity 写入
# game_dir.txt，这里读出后作为 renpy 的 basedir 位置参数传入
# （renpy.arguments 把 basedir 定义为位置参数 nargs="?"）。

from __future__ import print_function, absolute_import

import os
import sys
import warnings

# getFilesDir()：本文件所在目录，同时用作 renpy.config.renpy_base。
renpy_base = os.path.dirname(os.path.abspath(__file__))

# python-for-android 运行时包（android / jnius）。
lib_dir = os.path.join(renpy_base, "lib")
if os.path.isdir(lib_dir) and lib_dir not in sys.path:
    sys.path.insert(0, lib_dir)


def _read_game_root():
    """读取 RenPyActivity 在启动前写入的游戏根目录（含 game/ 子目录）。"""
    marker = os.path.join(renpy_base, "game_dir.txt")
    try:
        with open(marker, "r", encoding="utf-8") as f:
            path = f.read().strip()
    except Exception:
        return None
    if path and os.path.isdir(path):
        return path
    return None


# ---- 官方启动器契约函数（签名与语义对齐 SDK renpy.py） --------------------

def path_to_gamedir(basedir, name):
    """
    返回游戏脚本/资源目录（config.gamedir）。

    GalgameShell：`basedir` 即 RenPyActivity 传入的外部游戏根目录，
    其下 game/ 子目录为 gamedir（与官方 candidates 逻辑的落点一致）。
    """
    candidates = [name]

    game_name = name
    while game_name:
        prefix = game_name[0]
        game_name = game_name[1:]
        if prefix == " " or prefix == "_":
            candidates.append(game_name)

    candidates.extend(["game", "data"])

    for i in candidates:
        if i == "renpy":
            continue
        gamedir = os.path.join(basedir, i)
        if os.path.isdir(gamedir):
            return gamedir

    return basedir


def path_to_common(renpy_base):
    """返回 Ren'Py common 目录（renpy/main.py:349 调用）。"""
    path = renpy_base + "/renpy/common"
    if os.path.isdir(path):
        return path
    return None


def path_to_saves(gamedir, save_directory=None):
    """返回存档目录（renpy/main.py:430、savetoken.py:301、script.py:212 调用）。"""
    import renpy

    if save_directory is None:
        save_directory = renpy.config.save_directory
        save_directory = renpy.exports.fsencode(save_directory)

    def test_writable(d):
        try:
            fn = os.path.join(d, "test.txt")
            open(fn, "w").close()
            open(fn, "r").close()
            os.unlink(fn)
            return True
        except Exception:
            return False

    # Android 分支（与官方一致）：优先外部公共目录，回落 ANDROID_PUBLIC/saves。
    if renpy.android:
        paths = [
            os.path.join(os.environ["ANDROID_OLD_PUBLIC"], "game/saves"),
            os.path.join(os.environ["ANDROID_PRIVATE"], "saves"),
            os.path.join(os.environ["ANDROID_PUBLIC"], "saves"),
        ]

        for rv in paths:
            if os.path.isdir(rv) and test_writable(rv):
                break
        else:
            rv = paths[-1]

        print("Saving to", rv)
        return rv

    if not save_directory:
        return os.path.join(gamedir, "saves")

    return os.path.expanduser("~/.renpy/" + save_directory)


def path_to_logdir(basedir):
    """返回日志目录（renpy/bootstrap.py:396 调用）。"""
    import renpy

    if renpy.android:
        return os.environ["ANDROID_PUBLIC"]

    return basedir


def predefined_searchpath(commondir):
    """返回搜索路径（renpy/main.py:350 调用）。"""
    import renpy

    # 默认 gamedir 在最前。
    searchpath = [renpy.config.gamedir]

    if renpy.android:
        # 外部公共目录的 game/（若有）优先。
        if "ANDROID_PUBLIC" in os.environ:
            android_game = os.path.join(os.environ["ANDROID_PUBLIC"], "game")

            if os.path.exists(android_game):
                searchpath.insert(0, android_game)

        # Asset packs（本项目未用，保留官方结构）。
        packs = [
            "ANDROID_PACK_FF1", "ANDROID_PACK_FF2",
            "ANDROID_PACK_FF3", "ANDROID_PACK_FF4",
        ]

        for i in packs:
            if i not in os.environ:
                continue

            assets = os.environ[i]

            for i in ["renpy/common", "game"]:
                dn = os.path.join(assets, i)
                if os.path.isdir(dn):
                    searchpath.append(dn)
    else:
        if "RENPY_SEARCHPATH" in os.environ:
            searchpath.extend(os.environ["RENPY_SEARCHPATH"].split("::"))

    if commondir and os.path.isdir(commondir):
        searchpath.append(commondir)

    if renpy.android or renpy.ios:
        print("Mobile search paths:", " ".join(searchpath))

    return searchpath


def path_to_renpy_base():
    """返回 Ren'Py base 目录（含 renpy/ 与 lib/python3.12/）。"""
    return os.path.abspath(renpy_base)


# ---- 入口 ------------------------------------------------------------------

def main():
    game_root = _read_game_root()

    if game_root:
        # renpy.arguments 以位置参数解析 basedir / command。
        sys.argv = [sys.argv[0], game_root, "run"]
        print("GalgameShell: booting Ren'Py game at", game_root)
    else:
        # 无游戏目录：basedir 留空，bootstrap 会回落到 renpy_base(=getFilesDir())。
        sys.argv = [sys.argv[0]]
        print("GalgameShell: no game_dir.txt, falling back to renpy_base")

    # 官方 renpy.py main() 同款：忽略弃用警告。
    warnings.simplefilter("ignore", DeprecationWarning)

    try:
        import renpy.bootstrap
    except ImportError:
        print("Could not import renpy.bootstrap. Please ensure you decompressed Ren'Py",
              file=sys.stderr)
        print("correctly, preserving the directory structure.", file=sys.stderr)
        raise

    # 官方契约：把本启动器绑为 renpy.__main__，bootstrap/renpy.main 经由
    # renpy.__main__ 调用上面的 path_to_* 函数（漏掉必然 AttributeError）。
    import renpy

    renpy.__main__ = sys.modules[__name__]  # type: ignore

    renpy.bootstrap.bootstrap(renpy_base)


if __name__ == "__main__":
    main()
