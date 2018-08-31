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

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class SummariedListPreference extends ListPreference implements CustomArrayAdapter.OnSourceModeSelectedListener{
    public SummariedListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SummariedListPreference(Context context) {
        super(context);
    }

    CustomArrayAdapter listAdapter;
    private int currentIndex = 0;
    private String currentValue;
    private static final String DEFAULT_VALUE = "recommend";
    private static final int DEFAULT_INDEX = 0;

    @Override
    public CharSequence getSummary() {
        return getEntries()[currentIndex];
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return super.onGetDefaultValue(a, index);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if(restorePersistedValue){
            currentValue = this.getPersistedString(DEFAULT_VALUE);
        }
        else{
            currentValue = (String) defaultValue;
            this.persistString(currentValue);
        }
        currentIndex = findIndexOfValue(currentValue);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        listAdapter = new CustomArrayAdapter(getContext(), getEntries(), this);
        listAdapter.setSelectedPosition(currentIndex);
        builder.setAdapter(listAdapter, this);
    }

    @Override
    public void onItemSelected(int which) {
        getDialog().dismiss();
        currentIndex = which;
        currentValue = getEntryValues()[currentIndex].toString();
        this.persistString(currentValue);
        this.setSummary(getEntries()[currentIndex]);
    }
}
