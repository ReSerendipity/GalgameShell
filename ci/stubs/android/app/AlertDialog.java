package android.app;
import android.content.Context;
import android.content.DialogInterface;
public class AlertDialog implements DialogInterface {
    public void show() {}
    public void dismiss() {}
    public boolean isShowing() { return false; }
    public android.view.Window getWindow() { return new android.view.Window(); }
    public static class Builder {
        public Builder(Context context) {}
        public Builder setTitle(CharSequence title) { return this; }
        public Builder setTitle(int titleId) { return this; }
        public Builder setMessage(CharSequence message) { return this; }
        public Builder setMessage(int messageId) { return this; }
        public Builder setView(android.view.View view) { return this; }
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) { return this; }
        // GalgameShell：切换运行方式用单选列表，补真实 AlertDialog.Builder 的签名。
        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem,
                                           DialogInterface.OnClickListener l) { return this; }
        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener l) { return this; }
        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
        public Builder setNeutralButton(int textId, DialogInterface.OnClickListener l) { return this; }
        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener l) { return this; }
        public Builder setCancelable(boolean cancelable) { return this; }
        public AlertDialog create() { return new AlertDialog(); }
        public AlertDialog show() { return new AlertDialog(); }
    }
}
