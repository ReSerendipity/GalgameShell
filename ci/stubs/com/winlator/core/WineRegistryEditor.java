package com.winlator.core;
import java.io.Closeable;
import java.io.File;
public class WineRegistryEditor implements Closeable {
    public WineRegistryEditor(File file) {}
    public void setCreateKeyIfNotExist(boolean b) {}
    public void setStringValue(String key, String name, String value) {}
    public void setStringValues(String key, String[]... items) {}
    public void setDwordValue(String key, String name, int value) {}
    public void setHexValues(String key, String name, byte[] bytes) {}
    public String getStringValue(String key, String name) { return null; }
    public void removeValue(String key, String name) {}
    public boolean removeKey(String key) { return false; }
    public void close() {}
}
