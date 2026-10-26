package android.database;

/**
 * CI 桩：Cursor（android.database，真实为接口）。
 * 仅保留 galgame 子树引用到的符号。勿用于运行。
 */
public interface Cursor extends AutoCloseable {
    boolean moveToNext();

    String getString(int columnIndex);

    void close();
}
