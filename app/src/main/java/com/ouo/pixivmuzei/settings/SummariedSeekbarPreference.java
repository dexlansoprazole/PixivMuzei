/*
 * Copyright (C) 2018  Guo Zheng-Yan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Email: prtd.lambo@gmail.com
 */

package com.ouo.pixivmuzei.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ouo.pixivmuzei.R;


public class SummariedSeekbarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener{

    private SeekBar seekbar;
    private TextView seekbarValue;
    private int currentValue;
    private static final int DEFAULT_VALUE = 30;

    public SummariedSeekbarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setDialogLayoutResource(R.layout.seekbar_prefs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        this.setSummary(Integer.toString(currentValue));
        return super.onCreateView(parent);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, DEFAULT_VALUE);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if(restorePersistedValue)
            currentValue = this.getPersistedInt(DEFAULT_VALUE);
        else{
            currentValue = (int) defaultValue;
            this.persistInt(currentValue);
        }
        this.setSummary(Integer.toString(currentValue));
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        seekbar = view.findViewById(R.id.seekBar);
        seekbar.setMax(600);
        seekbar.setProgress(currentValue);
        seekbar.setOnSeekBarChangeListener(this);
        seekbarValue = view.findViewById(R.id.seekBarValue);
        seekbarValue.setText(Integer.toString(currentValue));
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            currentValue = seekbar.getProgress();
            this.setSummary(Integer.toString(currentValue));
            this.persistInt(currentValue);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
        progress = progress / 30;
        progress = progress * 30;
        seekbar.setProgress(progress);
        seekbarValue.setText(Integer.toString(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
