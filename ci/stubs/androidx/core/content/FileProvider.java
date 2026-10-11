package androidx.core.content;

/**
 * CI 桩：androidx.core.content.FileProvider（第二轮移动端评估 T2 所需）。
 * 真实实现由 androidx.core:core 提供；本桩仅满足 javac 门禁（ci/stubs 编译 galgame 树）。
 * 与真实 API 签名保持一致：getUriForFile(Context, String, File) → Uri。
 */
public class FileProvider {
    public static android.net.Uri getUriForFile(
            android.content.Context context, String authority, java.io.File file) {
        return null;
    }
}
