/* 链接期垫片：Winlator rootfs 的 glibc 不导出 atexit / pthread_atfork
 * （只导出 __cxa_atexit / __register_atfork），而 box64 main 自 2026-09 起在
 * core.c / custommem.c / signals.c 里直接引用了前者，导致按官方配方链接失败。
 *
 * 这里按 glibc 自身的实现把两个公共入口转发到已有的内部符号，语义不变：
 *   atexit(f)              -> __cxa_atexit(f, NULL, &__dso_handle)
 *   pthread_atfork(p,pa,c) -> __register_atfork(p, NULL, pa, c, &__dso_handle)
 *
 * 只参与链接，不改 box64 任何行为。构建脚本见 tools/box64/build_box64_android.sh。
 */

extern int __cxa_atexit(void (*)(void), void *, void *);
extern int __register_atfork(void (*)(void), void *, void (*)(void), void (*)(void), void *);

extern void *__dso_handle __attribute__((visibility("hidden")));
void *__dso_handle = (void *)&__dso_handle;

int atexit(void (*func)(void))
{
    return __cxa_atexit(func, (void *)0, &__dso_handle);
}

int pthread_atfork(void (*prepare)(void), void (*parent)(void), void (*child)(void))
{
    return __register_atfork(prepare, (void *)0, parent, child, &__dso_handle);
}
