#!/system/bin/sh
# GalgameShell — wine prefix repair for a container
#
# Why this exists:
#   * The shipped prefix templates are missing most DLL/driver stub files. Wine
#     resolves builtin DLLs through the prefix, so a missing stub turns into
#     "could not load kernel32.dll, status c0000135" (and later
#     "open_mapping(winex11.drv) = OBJECT_NAME_NOT_FOUND").
#   * The prefix also has no [Software\Wine\Drivers] registry entry, so wine
#     never loads an X11 graphics driver ("The graphics driver is missing").
#
# Both are fixed here: every builtin file (all suffixes: .dll .exe .drv .cpl
# .sys ...) is copied into system32 (64-bit) and syswow64 (32-bit, needed for
# the many 32-bit galgame exes that run under WOW64), then Graphics=x11 is
# written to HKCU\Software\Wine\Drivers.
#
# Usage (on device, as the app user):
#   run-as com.winlator sh /data/local/tmp/galgame-fix-prefix.sh <prefix_dir>
# e.g. prefix_dir = /data/data/com.winlator/files/rootfs/home/xuser-1/.wine

set -u
ROOT=/data/data/com.winlator/files/rootfs
WINE_DIR=$ROOT/opt/wine/lib/wine

PREFIX=${1:-$ROOT/home/xuser-1/.wine}
S32=$PREFIX/drive_c/windows/system32
SW64=$PREFIX/drive_c/windows/syswow64
B64=$WINE_DIR/x86_64-windows
B32=$WINE_DIR/i386-windows

mkdir -p "$S32" "$SW64"

copy_missing() { # $1 = source dir, $2 = dest dir
    src=$1; dst=$2; n=0
    for f in "$src"/*; do
        [ -f "$f" ] || continue
        b=${f##*/}
        if [ ! -e "$dst/$b" ]; then cp "$f" "$dst/$b" 2>/dev/null && n=$((n+1)); fi
    done
    echo $n
}

echo "prefix: $PREFIX"
a=$(copy_missing "$B64" "$S32")
b=$(copy_missing "$B32" "$SW64")
echo "stubs added: system32=$a syswow64=$b (now $(ls "$S32" | wc -l)/$(ls "$SW64" | wc -l) files)"

# X11 graphics driver: without this wine reports "graphics driver is missing".
export HOME=$PREFIX
export TMPDIR=$ROOT/tmp
export DISPLAY=:0
unset LD_LIBRARY_PATH
cd "$ROOT"
env LD_LIBRARY_PATH=$ROOT/usr/lib BOX64_LD_LIBRARY_PATH=$ROOT/lib/x86_64-linux-gnu \
    LD_PRELOAD=/data/data/com.winlator/files/libmpshim.so \
    $ROOT/usr/local/bin/box64 $ROOT/opt/wine/bin/wine \
    reg add "HKCU\\Software\\Wine\\Drivers" /v Graphics /t REG_SZ /d x11 /f
echo "Graphics=x11 set"
