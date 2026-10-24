package android.widget;
public class SeekBar extends ProgressBar {
    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);
        void onStartTrackingTouch(SeekBar seekBar);
        void onStopTrackingTouch(SeekBar seekBar);
    }
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {}
}
