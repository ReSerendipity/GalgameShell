package com.winlator.container;
import android.content.Context;
import com.winlator.core.Callback;
import org.json.JSONObject;
import java.util.ArrayList;
public class ContainerManager {
    public ContainerManager(Context context) {}
    public Context getContext() { return null; }
    public ArrayList<Container> getContainers() { return new ArrayList<>(); }
    public void createContainerAsync(JSONObject data, Callback<Container> callback) {}
    public Container getContainerById(int id) { return null; }
}
