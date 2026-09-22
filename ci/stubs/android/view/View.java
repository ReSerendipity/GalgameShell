package android.view;
public class View {
    public interface OnClickListener { void onClick(View v); }
    public void setOnClickListener(OnClickListener l) {}
    public int getId() { return 0; }
    public void setVisibility(int visibility) {}
    public static final int VISIBLE = 0;
    public static final int GONE = 8;
}
