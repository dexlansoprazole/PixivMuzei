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
import android.util.Log;

import com.ouo.pixivmuzei.PAPIExceptions.PixivAPIException;
import com.ouo.pixivmuzei.PAPIExceptions.PixivLoginException;
import com.securepreferences.SecurePreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PixivLoginManager {
    private static final String LOG_TAG = "PixivLoginManager";
    public static final int LOGIN_STATUS_OUT = 0;
    public static final int LOGIN_STATUS_PUBLIC = 1;
    public static final int LOGIN_STATUS_PERSONAL = 2;
    private static final String D_USERNAME = "user_pyda3858";
    private static final String D_PASSWORD = "pixivmuzeiservice0";
    private SharedPreferences mPreferences;
    private Context mContext;

    public PixivLoginManager(Context context){
        this.mContext = context;
        this.mPreferences = new SecurePreferences(mContext, (String) null, "login_info");
        deleteUnsafeData();
    }

    private void setAccountInfo(String username, String password) {
        mPreferences.edit()
                .putString("username",username)
                .putString("password",password)
                .apply();
    }

    private void setLastAccountInfo(String username, String password) {
        mPreferences.edit()
                .putString("last_username",username)
                .putString("last_password",password)
                .apply();
    }

    private void setLoginInfo(String accessToken, String refreshToken, String expires, String jo_user) {
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

    private void deleteUnsafeData() {
        File file= new File(mContext.getFilesDir(), "../shared_prefs/LoginInfo.xml");
        if(file.exists())
            file.delete();
    }

    public String getUsername(){
        return mPreferences.getString("username", null);
    }

    public String getPassword(){
        return mPreferences.getString("password", null);
    }

    public String getLastUsername(){
        return mPreferences.getString("last_username", null);
    }

    public String getLastPassword(){
        return mPreferences.getString("last_password", null);
    }

    public String getAccessToken(){
        return mPreferences.getString("accessToken", null);
    }

    public String getRefreshToken(){
        return mPreferences.getString("refreshToken", null);
    }

    public String getExpires() {
        return mPreferences.getString("expires", null);
    }

    public String getJo_user() {
        return mPreferences.getString("jo_user", null);
    }

    public int loginStatus(){
        if (getAccessToken() != null){
            if(getUsername().equals(D_USERNAME) && getPassword().equals(D_PASSWORD))
                return LOGIN_STATUS_PUBLIC;
            else
                return LOGIN_STATUS_PERSONAL;
        }
        return LOGIN_STATUS_OUT;
    }

    private String getExpires(int addHour){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        Date dt=new Date();
        sdf.format(dt);
        Calendar c = Calendar.getInstance();
        c.setTime(dt);
        c.add(Calendar.HOUR, addHour);
        Date d = c.getTime();
        return sdf.format(d);
    }

    private boolean isExpired() throws PixivLoginException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        String expires = getExpires();
        Calendar c = Calendar.getInstance();
        Calendar ce = Calendar.getInstance();
        try {
            ce.setTime(sdf.parse(expires));
        } catch (ParseException | NullPointerException e) {
            throw new PixivLoginException("Get isExpired failed", e);
        }
        int result = c.compareTo(ce);
        return result >= 0;
    }

    public void login(String username, String password) throws PixivLoginException {
        JSONObject loginResponse;
        JSONObject user;
        String accessToken;
        String refreshToken;
        String expires = getExpires(1);
        if (loginStatus() != PixivLoginManager.LOGIN_STATUS_OUT) {
            try {
                refreshAccessToken();
            } catch (PixivLoginException e) {
                e.printStackTrace();
            }
        }

        if(loginStatus() != PixivLoginManager.LOGIN_STATUS_PERSONAL){
            if(loginStatus() == PixivLoginManager.LOGIN_STATUS_PUBLIC && username.equals(D_USERNAME) && password.equals(D_PASSWORD))
                return;
            Log.i(LOG_TAG, "Login with '" + username + "'...");
            //Login by password
            try {
                loginResponse = PixivAPI.login(username, password);
                accessToken = loginResponse.getString("access_token");
                refreshToken = loginResponse.getString("refresh_token");
                user = loginResponse.getJSONObject("user");
                setAccountInfo(username, password);
                setLoginInfo(accessToken, refreshToken, expires, user.toString());
                if(!(username.equals(D_USERNAME) || password.equals(D_PASSWORD)))
                    setLastAccountInfo(username, password);
                Log.i(LOG_TAG,"Login succeeded");
            } catch (PixivAPIException | JSONException e) {
                throw new PixivLoginException("Login failed", e);
            }
            Log.d(LOG_TAG, "username: " + getUsername() +
                    "\npassword: *****" +
                    "\nexpires: " + expires +
                    "\naccessToken: " + getAccessToken() +
                    "\nrefreshToken: " + getRefreshToken() +
                    "\njo_user: " + getJo_user()
            );
        }
    }

    void login() throws PixivLoginException {
        login(D_USERNAME, D_PASSWORD);
    }

    private void refreshAccessToken() throws PixivLoginException {
        String expires = getExpires(1);
        if(isExpired()){
            Log.i(LOG_TAG,"Refresh accessToken...");
            //Refresh accessToken
            try {
                JSONObject loginResponse = PixivAPI.refreshAccessToken(getRefreshToken());
                String accessToken = loginResponse.getString("access_toke");
                String refreshToken = loginResponse.getString("refresh_token");
                JSONObject user = loginResponse.getJSONObject("user");
                setAccountInfo(getUsername(), getPassword());
                setLoginInfo(accessToken, refreshToken, expires, user.toString());
                Log.i(LOG_TAG,"Refresh accessToken succeeded");
            } catch (PixivAPIException | JSONException e) {
                throw new PixivLoginException("Refresh accessToken failed", e);
            }
        }
    }
}
