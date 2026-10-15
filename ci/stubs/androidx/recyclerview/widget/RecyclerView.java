package androidx.recyclerview.widget;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * CI 桩：androidx.recyclerview.widget.RecyclerView。仅保留热点入口类
 * （ContainersFragment 及其内部 ContainersAdapter）引用到的符号。
 */
public class RecyclerView extends android.widget.FrameLayout {
    public RecyclerView(Context context) { super(context); }
    public void setLayoutManager(LayoutManager layoutManager) {}
    public void setAdapter(Adapter adapter) {}
    public Context getContext() { return null; }

    public abstract static class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);
        public abstract void onBindViewHolder(VH holder, int position);
        public abstract int getItemCount();
    }

    public abstract static class ViewHolder {
        public final View itemView;
        public ViewHolder(View itemView) { this.itemView = itemView; }
        public int getAdapterPosition() { return 0; }
    }

    public abstract static class LayoutManager { }
}
