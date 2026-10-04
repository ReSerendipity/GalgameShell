package org.renpy.android;

import androidx.core.content.FileProvider;

import com.winlator.R;

public class RenPyFileProvider extends FileProvider {
    public RenPyFileProvider() {
        super(R.xml.file_paths);
    }
}
