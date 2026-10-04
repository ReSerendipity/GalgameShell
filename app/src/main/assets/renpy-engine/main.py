# -*- coding: utf-8 -*-
#
# GalgameShell — 内置 Ren'Py 启动器（等价于 RAPT 的 private/main.py）
#
# 运行契约（已对 librenpython.so 的 start_python() 与 renpy 源码逐条核实）：
#   * librenpython.so 的 start_python() 固定执行 getenv("ANDROID_PRIVATE")/main.py，
#     并 chdir 到该目录（sys.path[0] 与 PYTHONHOME 都指向它）。
#   * PythonSDLActivity.preparePython() 已把 ANDROID_PRIVATE 设为 getFilesDir()，
#     因此本文件位于 getFilesDir()/main.py，renpy_base = getFilesDir()。
#   * 引擎包位于 getFilesDir()/renpy/；renpy/__main__.py 提供
#     path_to_gamedir / path_to_common / path_to_saves 等函数。
#   * python-for-android 的 android / jnius 包位于 getFilesDir()/lib/，需手动加进
#     sys.path（renpy 在 renpy.android 分支会 import android、from jnius import autoclass）。
#
# 与 RAPT 的差异：RAPT 把游戏数据打进 assets 并落在 ANDROID_PUBLIC/game；本项目改为
# 导入外部存储上的 Ren'Py 项目，因此这里把游戏的**根目录**作为 renpy 的 basedir 传入
# （renpy.arguments 把 basedir 定义为位置参数 nargs="?"），由
# renpy.__main__.path_to_gamedir 推导出 gamedir = <游戏根>/game。

import os
import sys

# getFilesDir()：本文件所在目录，同时用作 renpy.config.renpy_base。
renpy_base = os.path.dirname(os.path.abspath(__file__))

# python-for-android 运行时包（android / jnius）。
lib_dir = os.path.join(renpy_base, "lib")
if os.path.isdir(lib_dir) and lib_dir not in sys.path:
    sys.path.insert(0, lib_dir)


def _read_game_dir():
    """读取 RenPyActivity 在启动前写入的游戏根目录。"""
    marker = os.path.join(renpy_base, "game_dir.txt")
    try:
        with open(marker, "r", encoding="utf-8") as f:
            path = f.read().strip()
    except Exception:
        return None
    if path and os.path.isdir(path):
        return path
    return None


def main():
    game_dir = _read_game_dir()

    if game_dir:
        # renpy.arguments 以位置参数解析 basedir / command。
        sys.argv = [sys.argv[0], game_dir, "run"]
        print("GalgameShell: booting Ren'Py game at", game_dir)
    else:
        # 无游戏目录：basedir 留空，bootstrap 会回落到 renpy_base(=getFilesDir())。
        sys.argv = [sys.argv[0]]
        print("GalgameShell: no game_dir.txt, falling back to renpy_base")

    import renpy.bootstrap

    # renpy_base 决定 renpy.config.renpy_base；path_to_common() 据此定位
    # getFilesDir()/renpy/common，故引擎必须解包到该布局。
    renpy.bootstrap.bootstrap(renpy_base)


if __name__ == "__main__":
    main()
