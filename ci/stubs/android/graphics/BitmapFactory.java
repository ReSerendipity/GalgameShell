package android.graphics;
public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize;
        public int outWidth;
        public int outHeight;
    }
    public static Bitmap decodeByteArray(byte[] data, int offset, int length) { return null; }
    public static Bitmap decodeFile(String pathName) { return null; }
    public static Bitmap decodeFile(String pathName, Options opts) { return null; }
}
