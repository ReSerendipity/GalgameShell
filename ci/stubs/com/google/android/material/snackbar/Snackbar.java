package com.google.android.material.snackbar;
import android.view.View;
public class Snackbar {
    public static final int LENGTH_SHORT = -1;
    public static final int LENGTH_LONG = 0;
    public static Snackbar make(View view, CharSequence text, int duration) { return new Snackbar(); }
    public Snackbar setText(CharSequence message) { return this; }
    public Snackbar setAction(CharSequence text, Runnable action) { return this; }
    public void show() {}
}
