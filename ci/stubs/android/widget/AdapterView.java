package android.widget;
import android.view.View;
import android.view.ViewGroup;
public class AdapterView<T> extends ViewGroup {
    public interface OnItemClickListener {
        void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }
    public void setAdapter(android.widget.ListAdapter adapter) {}
    public void setOnItemClickListener(OnItemClickListener listener) {}
}
