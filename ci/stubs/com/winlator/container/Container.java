package com.winlator.container;
import java.io.File;
public class Container {
    public static final String DEFAULT_ENV_VARS = "";
    public static final String DEFAULT_DRIVES = "";
    public Container(int id) {}
    public File getRootDir() { return null; }
    public String getEnvVars() { return ""; }
    public void setEnvVars(String envVars) {}
    public void putExtra(String name, Object value) {}
    public String getExtra(String name) { return null; }
    public String getExtra(String name, String fallback) { return fallback; }
    public void saveData() {}
}
