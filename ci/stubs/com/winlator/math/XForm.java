package com.winlator.math;
public class XForm {
    public static float[] getInstance() { return new float[9]; }
    public static void makeTranslation(float[] m, float tx, float ty) {}
    public static void scale(float[] m, float sx, float sy) {}
    public static void makeScale(float[] m, float sx, float sy) {}
    public static float[] transformPoint(float[] m, float x, float y) { return new float[2]; }
}
