package androidx.fragment.app;

/**
 * CI 桩：androidx.fragment.app.FragmentContainer。真实 Fragment 继承 android.content.Context
 * 并实现本接口；桩为保持具体子类可实例化，不继承 Context，本接口也降为空标记。
 */
public interface FragmentContainer {}
