image bg dusk = "images/bg_dusk.png"
image heroine normal = "images/heroine.png"

define ayu = Character("ayu", color="#ffcc66")

label start:
    scene bg dusk with fade
    show heroine normal with dissolve
    ayu "evening. the sun is setting behind the ridge."
    ayu "background and sprite are both decoded by the engine at runtime."
    ayu "Ren'Py 8.5.3 / Python 3.12 / running on Android."
    ayu "press space or tap to advance."
    return
