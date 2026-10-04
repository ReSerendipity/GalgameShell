# 最小可启动 Ren'Py 测试游戏（仅用于验证内置引擎能出画面）
# 放在 .workbuddy/verify/ 下（gitignored），推送到设备外部目录后由 RenPyActivity 启动。
# 引擎不提供默认 say/main_menu 屏，故本目录自带 screens.rpy。

# options.rpy
define config.name = "GalgameShellTest"
define config.version = "1.0"

init python:
    config.window_title = "GalgameShell RenPy Test"
    config.main_menu_music = None
    config.has_sound = False
    config.has_music = False
    config.has_voice = False
    config.save_directory = "testrenpy-saves"
