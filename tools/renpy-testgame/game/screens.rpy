# 自包含的最小屏定义（不依赖 gui.rpy / 引擎默认屏）

screen say(who, what):
    frame:
        xalign 0.5
        yalign 1.0
        xfill True
        left_padding 24
        right_padding 24
        top_padding 16
        bottom_padding 16
        background "#2a3244e0"
        vbox:
            spacing 8
            if who is not None:
                text who:
                    id "who"
                    color "#ffcc66"
                    size 30
            text what:
                id "what"
                color "#ffffff"
                size 26

screen main_menu():
    tag menu
    add "#101018"
    vbox:
        xalign 0.5
        yalign 0.5
        spacing 16
        text "GalgameShell" size 52 color "#ffffff"
        text "Built-in Ren'Py Engine" size 30 color "#aaaaaa"
    # 短暂停留后自动进入 label start，便于直接截到对话画面
    timer 1.2 action Start()
