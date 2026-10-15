package android.widget;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

/** CI 桩：android.widget.PopupMenu。仅保留热点入口类引用到的符号。 */
public class PopupMenu {
    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }
    public PopupMenu(Context context, View anchor) {}
    public PopupMenu(Context context, View anchor, int gravity) {}
    public Menu getMenu() { return null; }
    public void inflate(int menuRes) {}
    public void setForceShowIcon(boolean forceShow) {}
    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {}
    public void show() {}
}
