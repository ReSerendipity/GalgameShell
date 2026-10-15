package android.view;

/** CI 桩：android.view.Menu。仅保留热点入口类引用到的符号。 */
public interface Menu {
    MenuItem add(int titleId);
    MenuItem add(CharSequence title);
    MenuItem add(int groupId, int itemId, int order, CharSequence title);
    MenuItem add(int groupId, int itemId, int order, int titleId);
    void clear();
    int size();
}
