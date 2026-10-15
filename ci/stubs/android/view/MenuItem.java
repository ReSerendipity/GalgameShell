package android.view;

/** CI 桩：android.view.MenuItem。仅保留热点入口类引用到的符号。 */
public interface MenuItem {
    int getItemId();
    int getGroupId();
    CharSequence getTitle();
    MenuItem setTitle(int titleId);
    MenuItem setIcon(int iconId);
}
