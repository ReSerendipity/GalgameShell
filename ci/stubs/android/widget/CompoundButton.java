package android.widget;
public class CompoundButton extends Button {
    public CompoundButton(android.content.Context context) { super(context); }
    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }
    public void setChecked(boolean checked) {}
    public boolean isChecked() { return false; }
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {}
}
