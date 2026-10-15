package androidx.fragment.app;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

/**
 * CI 桩：androidx.fragment.app.Fragment。仅保留热点入口类（ContainersFragment /
 * GalgameHomeFragment）引用到的符号，签名与真实 Fragment 对齐。
 * 真实 Fragment 继承 android.content.Context；桩不做该继承（避免被迫实现 Context 的
 * 抽象方法），但保留同名访问器，对门禁编译无影响。getActivity() 返回
 * android.app.Activity（真实为 FragmentActivity），向下转型到 AppCompatActivity /
 * GalgameMainActivity 在桩与真实 SDK 下均合法。
 */
public abstract class Fragment implements FragmentContainer {
    public void onCreate(Bundle savedInstanceState) {}
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { return null; }
    public void onViewCreated(View view, Bundle savedInstanceState) {}
    public void onResume() {}
    public void onPause() {}
    public void onDestroyView() {}
    public void onDestroy() {}
    public void setHasOptionsMenu(boolean hasMenu) {}
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {}
    public boolean onOptionsItemSelected(MenuItem item) { return false; }
    public Activity getActivity() { return null; }
    public Context getContext() { return null; }
    public View getView() { return null; }
    public View requireView() { return null; }
    public Activity requireActivity() { return null; }
    public Context requireContext() { return null; }
    public String getString(int resId) { return ""; }
    public String getString(int resId, Object... formatArgs) { return ""; }
    public void startActivity(Intent intent) {}
    public void startActivityForResult(Intent intent, int requestCode) {}
}
