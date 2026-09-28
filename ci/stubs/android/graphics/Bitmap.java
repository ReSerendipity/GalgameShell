package android.graphics;
public class Bitmap {
    public enum CompressFormat { PNG, JPEG, WEBP }
    public boolean compress(CompressFormat format, int quality, java.io.OutputStream stream) { return true; }
    public void recycle() {}
}
