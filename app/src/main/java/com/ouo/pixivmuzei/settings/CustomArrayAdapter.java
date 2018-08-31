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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;

import com.ouo.pixivmuzei.PixivSource;
import com.ouo.pixivmuzei.R;

public class CustomArrayAdapter extends ArrayAdapter<CharSequence> {
    private static int selectedPosition;
    private static Object callback;

    public CustomArrayAdapter(Context context, CharSequence[] sourceModes, Object callback) {
        super(context, 0, sourceModes);
        this.callback = callback;
    }

    public interface OnSourceModeSelectedListener {
        void onItemSelected(int which);
    }

    public void setSelectedPosition(int position){
        selectedPosition = position;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        OnSourceModeSelectedListener smsl = (OnSourceModeSelectedListener) callback;
        smsl.onItemSelected(selectedPosition);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        CharSequence sourceMode = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.listitem, parent, false);
        }
        RadioButton rbItem = (RadioButton)convertView.findViewById(R.id.rbItem);
        rbItem.setTag(position);
        rbItem.setText(sourceMode);
        if(position >= 13 && PixivSource.isLogin() < 1){
            rbItem.setEnabled(false);
        }
        else
            rbItem.setEnabled(true);
        rbItem.setChecked(position == selectedPosition);
        rbItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedPosition = (int) view.getTag();
                notifyDataSetChanged();
            }
        });

        return convertView;
    }
}