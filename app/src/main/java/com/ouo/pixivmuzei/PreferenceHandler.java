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

package com.ouo.pixivmuzei;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceHandler {
    private static final String LOG_TAG = "PreferenceHandler";
    private Context mContext;
    private SharedPreferences mPreferences;

    public PreferenceHandler(Context context){
        mContext = context;

        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public int getConfChangeInterval() {
        final int defaultValue = mContext.getResources().getInteger(R.integer.pref_changeInterval_default);
        final Integer result = mPreferences.getInt("pref_changeInterval", defaultValue);
        return result;
    }

    public void setConfChangeInterval(int changeInterval){
        mPreferences.edit()
                .putInt("pref_changeInterval", changeInterval)
                .apply();
    }

    public String getConfSourceMode() {
        final String defaultValue = mContext.getString(R.string.pref_sourceMode_default);
        final String result = mPreferences.getString("pref_sourceMode", defaultValue);
        return result;
    }

    public void setConfSourceMode(String sourceMode){
        mPreferences.edit()
                .putString("pref_sourceMode", sourceMode)
                .apply();
    }

    public int getConfLoadAmount() {
        final int defaultValue = mContext.getResources().getInteger(R.integer.pref_loadAmount_default);
        final int result = mPreferences.getInt("pref_loadAmount", defaultValue);
        return result;
    }

    public boolean getConfIsOnlyUpdateOnWifi() {
        final boolean defaultValue = false;
        final boolean result = mPreferences.getBoolean("pref_onlyWifi", defaultValue);
        return result;
    }

    public boolean getConfIsNoManga() {
        final boolean defaultValue = false;
        final boolean result = mPreferences.getBoolean("pref_noManga", defaultValue);
        return result;
    }

    public boolean getConfIsNoR18() {
        final boolean defaultValue = false;
        final boolean result = mPreferences.getBoolean("pref_noR18", defaultValue);
        return result;
    }

    public boolean getConfIsAutoCheckUpdate() {
        final boolean defaultValue = true;
        final boolean result = mPreferences.getBoolean("pref_autoCheckUpdate", defaultValue);
        return result;
    }

    public void setUpdateInfo(String lastUpdate, String contents){
        mPreferences.edit()
                .putString("lastUpdate", lastUpdate)
                .putString("ja_contents", contents)
                .apply();
    }

    public String getLastUpdate() {
        final String result = mPreferences.getString("lastUpdate", "");
        return result;
    }

    public void setIsSourceUpToDate(boolean isSourceUpToDate) {
        mPreferences.edit()
                .putBoolean("isSourceUpToDate", isSourceUpToDate)
                .apply();
    }

    public Boolean getIsSourceUpToDate() {
        final Boolean result = mPreferences.getBoolean("isSourceUpToDate", false);
        return result;
    }

    public String getJa_contents() {
        final String result = mPreferences.getString("ja_contents", "");
        return result;
    }
}
