package com.winlator.core;
import java.io.File;
public class FileUtils {
    public static boolean copy(File srcFile, File dstFile) { return false; }
    public static void chmod(File file, int mode) {}
    public static boolean isSymlink(File file) { return false; }
    public static void symlink(File linkTarget, File linkFile) {}
    public static void symlink(String linkTarget, String linkFile) {}
    public static boolean delete(File targetFile) { return false; }
    public static String readString(File file) { return null; }
}
