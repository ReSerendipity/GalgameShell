package android.widget;
public class EditText extends TextView {
    public EditText(android.content.Context context) { super(context); }
    public android.text.Editable getText() { return null; }
    public void setHint(int resId) {}
    public void setHint(CharSequence hint) {}
}
