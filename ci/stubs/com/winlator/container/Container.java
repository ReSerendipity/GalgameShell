package com.winlator.container;
import java.io.File;
public class Container {
    public static final String DEFAULT_ENV_VARS = "";
    public static final String DEFAULT_DRIVES = "";
    public static final String DEFAULT_WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1";
    public final int id = 0;
    public Container(int id) {}
    public File getRootDir() { return null; }
    public String getEnvVars() { return ""; }
    public void setEnvVars(String envVars) {}
    public void putExtra(String name, Object value) {}
    public String getExtra(String name) { return null; }
    public String getExtra(String name, String fallback) { return fallback; }
    public void saveData() {}
    public String getName() { return ""; }
    public String getScreenSize() { return ""; }
    public String getGraphicsDriver() { return ""; }
    public String getDXWrapper() { return ""; }
    public String getAudioDriver() { return ""; }
    public String getWinComponents() { return ""; }
    public String getBox64Preset() { return ""; }
    public byte getStartupSelection() { return 0; }
}
