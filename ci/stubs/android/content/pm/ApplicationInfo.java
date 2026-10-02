package android.content.pm;

/** CI 离线门禁桩：仅需 nativeLibraryDir 字段（真实 APK 安装后的原生库目录）。 */
public class ApplicationInfo {
    public String nativeLibraryDir;
    public String packageName;
    public String dataDir;
}
