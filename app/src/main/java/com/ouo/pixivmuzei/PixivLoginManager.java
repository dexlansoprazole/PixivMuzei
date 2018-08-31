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
import android.content.pm.PackageManager;
import android.util.Log;

import com.securepreferences.SecurePreferences;

import java.io.File;

import static android.content.Context.MODE_PRIVATE;

public class PixivLoginManager {
    private static final String LOG_TAG = "PixivLoginManager";
    private SharedPreferences mPreferences;
    private Context mContext;

    public PixivLoginManager(Context context){
        mContext = context;
        mPreferences = new SecurePreferences(mContext, (String) null, "login_info");
        deleteUnsafeData();
    }

    public void setAccountInfo(String username, String password){
        mPreferences.edit()
                .putString("username",username)
                .putString("password",password)
                .apply();
    }

    public void setLastAccountInfo(String username, String password){
        mPreferences.edit()
                .putString("last_username",username)
                .putString("last_password",password)
                .apply();
    }

    public void setLoginInfo( String accessToken, String refreshToken, String expires, String jo_user){
        mPreferences.edit()
                .putString("accessToken",accessToken)
                .putString("refreshToken",refreshToken)
                .putString("expires",expires)
                .putString("jo_user",jo_user)
                .apply();
    }

    public void deleteLoginInfo(){
        mPreferences.edit().remove("accessToken").remove("refreshToken").remove("expires").remove("jo_user").apply();
    }

    public void deleteUnsafeData(){
        File file= new File(mContext.getFilesDir(), "../shared_prefs/LoginInfo.xml");
        if(file.exists())
            file.delete();
    }

    public String getUsername(){
        final String result = mPreferences.getString("username", null);
        return result;
    }

    public String getPassword(){
        final String result = mPreferences.getString("password", null);
        return result;
    }

    public String getLastUsername(){
        final String result = mPreferences.getString("last_username", null);
        return result;
    }

    public String getLastPassword(){
        final String result = mPreferences.getString("last_password", null);
        return result;
    }

    public String getAccessToken(){
        final String result = mPreferences.getString("accessToken", null);
        return result;
    }

    public String getRefreshToken(){
        final String result = mPreferences.getString("refreshToken", null);
        return result;
    }

    public String getExpires() {
        final String result = mPreferences.getString("expires", null);
        return result;
    }

    public String getJo_user() {
        final String result = mPreferences.getString("jo_user", null);
        return result;
    }
}
