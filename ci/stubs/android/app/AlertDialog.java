package android.app;
import android.content.Context;
import android.content.DialogInterface;
public class AlertDialog implements DialogInterface {
    public void show() {}
    public void dismiss() {}
    public static class Builder {
        public Builder(Context context) {}
        public Builder setTitle(CharSequence title) { return this; }
        public Builder setTitle(int titleId) { return this; }
        public Builder setMessage(CharSequence message) { return this; }
        public Builder setMessage(int messageId) { return this; }
        public Builder setView(android.view.View view) { return this; }
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) { return this; }
        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener l) { return this; }
        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener l) { return this; }
        public Builder setCancelable(boolean cancelable) { return this; }
        public AlertDialog create() { return new AlertDialog(); }
        public AlertDialog show() { return new AlertDialog(); }
    }
}
