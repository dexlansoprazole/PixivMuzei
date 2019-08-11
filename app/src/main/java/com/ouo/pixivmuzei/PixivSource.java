/*
 * Copyright 2014 Hong Minhee, modified by Guo Zheng-Yan in 2018/08/30
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
 */
package com.ouo.pixivmuzei;

import android.Manifest;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.android.apps.muzei.api.UserCommand;
import com.ouo.pixivmuzei.PAPIExceptions.GetDataFailedException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import static com.ouo.pixivmuzei.settings.SettingsFragment.getStringFromInputStream;

public class PixivSource extends RemoteMuzeiArtSource  {
    private static final String LOG_TAG = "PixivSource";
    private static final String SOURCE_NAME = "PixivSource";
    private static final int COMMAND_ID_DOWNLOAD = 1;
    public static final int AUTO_CHECK_UPDATE_INTERVAL = 3600;
    public static final int MINUTE_TO_MS = 60000;
    private static Artwork currentArtwork = null;

    public static Context mContext = null;
    public static PreferenceHandler mPreferenceHandler = null;
    public static PixivLoginManager mPixivLoginManager = null;
    public PixivSource() {super(SOURCE_NAME);}
    public static final String D_USERNAME = "user_pyda3858";
    public static final String D_PASSWORD = "pixivmuzeiservice0";

    private static PixivPublicAPI PPAPI;
    private static PixivAppAPI PAPPAPI;

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d(LOG_TAG, "onStart");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        mContext = getApplicationContext();
        mPreferenceHandler = new PreferenceHandler(mContext);
        mPixivLoginManager = new PixivLoginManager(mContext);
        PAPPAPI = new PixivAppAPI(mContext);
        PPAPI = new PixivPublicAPI(mContext);

        List<UserCommand> commands = new ArrayList();
        commands.add(new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK));
        commands.add(new UserCommand(COMMAND_ID_DOWNLOAD,getString(R.string.action_download)));
        //commands.add(new UserCommand(2,"test"));
        setUserCommands(commands);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //Log.d(LOG_TAG, "onDestroy");
    }

    @Override
    protected void onUpdate(int reason) {
        super.onUpdate(reason);
        String sReason = null;

        if(mPreferenceHandler.getConfIsAutoCheckUpdate() && isNetConnected()){
            checkUpdate();
        }

        switch (reason){
            case 0:
                sReason = "Other";
                break;
            case 1:
                sReason = "Initial";
                break;
            case 2:
                sReason = "User";
                break;
            case 3:
                sReason = "Scheduled";
                break;
        }
        Log.i(LOG_TAG, "Update reason: " + sReason);
    }

    @Override
    protected void onCustomCommand(int id) {
        super.onCustomCommand(id);
        switch (id) {
            case 1: //Download
                if(DownloadIllust() < 0){
                    showToast(getString(R.string.toast_downloadFail));
                    return;
                }
                break;

            case 2:    //test
                Log.i(LOG_TAG, "User Command: Test");
                Log.d(LOG_TAG, "Wifi: " + isWifiConnected());
                Log.i(LOG_TAG,"login...");
                login(D_USERNAME,D_PASSWORD);
                break;
        }
    }

    @Override
    protected void onTryUpdate(int i) throws RetryException {
        final Artwork prevArtwork = getCurrentArtwork();
        final String prevToken = prevArtwork != null ? prevArtwork.getToken() : null;
        final String sourceMode = mPreferenceHandler.getConfSourceMode();
        final Integer loadAmount = mPreferenceHandler.getConfLoadAmount();
        JSONArray contents = null;

        mPreferenceHandler = new PreferenceHandler(mContext);
        mPixivLoginManager = new PixivLoginManager(mContext);
        if (mPreferenceHandler.getConfIsOnlyUpdateOnWifi() && !isWifiConnected()) {
            Log.w(LOG_TAG, "No WIFI");
            showToast(getString(R.string.toast_noWIFI));
            scheduleUpdate();
            return;
        }
        if(isLogin() >= 0 && isExpired()){
            Log.i(LOG_TAG,"Refresh accessToken...");
            if(isLogin() == 0){
                if(!login(D_USERNAME, D_PASSWORD)){
                    showToast(getString(R.string.toast_updateFail));
                    scheduleUpdate();
                    return;
                }
            }
            else if(isLogin() == 1){
                if(!login(mPixivLoginManager.getUsername(), mPixivLoginManager.getPassword())){
                    showToast(getString(R.string.toast_updateFail));
                    scheduleUpdate();
                    return;
                }
            }
        }
        if(isLogin() < 0) {
            Log.i(LOG_TAG, "Login with 'pixmuzei'...");
            if(!login(D_USERNAME, D_PASSWORD)){
                showToast(getString(R.string.toast_updateFail));
                scheduleUpdate();
                return;
            }
        }

        checkIsSourceUpToDate();

        boolean isSourceUpToDate = mPreferenceHandler.getIsSourceUpToDate();

        if(!isSourceUpToDate){
            Log.i(LOG_TAG, "Updating source data...");

            try{
                if (sourceMode.equals("userFav") || sourceMode.equals("following") || sourceMode.equals("recommend"))
                    contents = PAPPAPI.getSource(sourceMode, loadAmount);
                else
                    contents = PAPPAPI.getRanking(sourceMode, loadAmount);
            } catch (GetDataFailedException e){
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
                Log.e(LOG_TAG, "Update source data failed");
                showToast(getString(R.string.toast_updateFail));
                scheduleUpdate();
                return;
            }

            mPreferenceHandler.setIsSourceUpToDate(true);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            Calendar c = Calendar.getInstance();
            Date d = c.getTime();
            mPreferenceHandler.setUpdateInfo(sdf.format(d), contents.toString());
            Log.i(LOG_TAG, "Update source data succeeded");
        }

        try {
            contents = new JSONArray(mPreferenceHandler.getJa_contents());
        } catch (JSONException e) {
            Log.w(LOG_TAG, "Missing preference 'ja_contents'");
        }
        Log.d(LOG_TAG, "The number of Contents: " + contents.length());

        PixivArtwork paw, paw_PPAPI;
        final Random random = new Random();
        while (true) {
            try {
                i = random.nextInt(contents.length());
                final JSONObject content;
                content = contents.getJSONObject(i);
                paw = new PixivArtwork(content);
                paw_PPAPI = PPAPI.getWorkById(paw.getId());
            } catch (IndexOutOfBoundsException | NullPointerException | JSONException | GetDataFailedException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
                showToast(getString(R.string.toast_updateFail));
                scheduleUpdate();
                return;
            }

            if(mPreferenceHandler.getConfIsNoManga() && paw.getType().equals("manga")) {
                Log.i(LOG_TAG,"Is Manga!!    ID:"+paw.getId());
                continue;
            }

            if(mPreferenceHandler.getConfIsNoR18() && paw.getSanityLevel() >= 6 && !sourceMode.matches("^.*r18.*$")) {
                Log.i(LOG_TAG,"Is R-18!!    ID:"+paw.getId());
                continue;
            }

            final int workId = paw.getId();
            final String illustType = paw.getType();
            if (workId < 0 || illustType == null) {
                continue;
            }

            final String token = String.valueOf(workId);
            if (prevToken != null && prevToken.equals(token)) {
                continue;
            }

            final String workUri = "http://www.pixiv.net/member_illust.php" +
                    "?mode=medium&illust_id=" + workId;
            final Uri fileUri;
            try {
                Log.i(LOG_TAG,"Downloading illustration...");
                fileUri = PPAPI.downloadIllust(paw_PPAPI, getApplication().getExternalCacheDir().toString());
                Log.i(LOG_TAG,"Download illustration succeeded");
            } catch (GetDataFailedException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
                showToast(getString(R.string.toast_updateFail));
                scheduleUpdate();
                return;
            }

            String username;
            try {
                username = paw.getUser().getString("name");
            } catch (JSONException e) {
                Log.e(LOG_TAG,"Username missed");
                e.printStackTrace();
                username = "unknown";
            }
            currentArtwork = new Artwork.Builder()
                        .title(paw.getTitle())
                        .byline(username)
                        .imageUri(fileUri)
                        .token(token)
                        .viewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(workUri)))
                        .build();
            publishArtwork(currentArtwork);
            Log.d(LOG_TAG, "Illust ID: " + getCurrentArtwork().getToken());
            scheduleUpdate();

            break;
        }
    }

    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED && netInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private boolean isNetConnected(){
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    public static int isLogin(){
        try {
            String accessToken = mPixivLoginManager.getAccessToken();
            String username = mPixivLoginManager.getUsername();
            if (accessToken != null) {
                if (username.equals(D_USERNAME))
                    return 0;
                else
                    return 1;
            }
        }
        catch (NullPointerException e){
            Log.e(LOG_TAG, "Context missing");
            e.printStackTrace();
        }
        return -1;
    }

    private void scheduleUpdate() {
        final long changeInterval = mPreferenceHandler.getConfChangeInterval();
        if (changeInterval > 0) {
            scheduleUpdate(System.currentTimeMillis() + changeInterval * MINUTE_TO_MS);
        }
    }

    private void showToast(final String message){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean login(String username, String password){
        if(!isNetConnected()){
            Log.e(LOG_TAG, "Login failed: No network connection");
            //showToast(getString(R.string.toast_noNet));
            return false;
        }

        mPixivLoginManager = new PixivLoginManager(mContext);
        JSONObject loginResponse = null;
        JSONObject user;
        String accessToken;
        String refreshToken;
        String expires = getExpires(1);

        if(isLogin() >= 0){
            //Refresh accessToken
            try {
                loginResponse = PPAPI.refreshAccessToken(mPixivLoginManager.getRefreshToken());
                accessToken = loginResponse.getString("access_token");
                refreshToken = loginResponse.getString("refresh_token");
                user = loginResponse.getJSONObject("user");
                mPixivLoginManager.setAccountInfo(username, password);
                mPixivLoginManager.setLoginInfo(accessToken, refreshToken, expires, user.toString());
                Log.i(LOG_TAG,"Refresh accessToken succeeded");
            } catch (GetDataFailedException | JSONException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
        }

        if(isLogin() < 0 || loginResponse == null){
            //Login by password
            try {
                loginResponse = PPAPI.login(username, password);
                accessToken = loginResponse.getString("access_token");
                refreshToken = loginResponse.getString("refresh_token");
                user = loginResponse.getJSONObject("user");
                mPixivLoginManager.setAccountInfo(username, password);
                mPixivLoginManager.setLoginInfo(accessToken, refreshToken, expires, user.toString());
                Log.i(LOG_TAG,"Login succeeded");
            } catch (GetDataFailedException | JSONException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
                return false;
            }

        }

        Log.d(LOG_TAG, "username: " + username +
                "\npassword: " + password +
                "\nexpires: " + expires +
                "\naccessToken: " + mPixivLoginManager.getAccessToken() +
                "\nrefreshToken: " + mPixivLoginManager.getRefreshToken() +
                "\njo_user: " + mPixivLoginManager.getJo_user()
        );
        return true;
    }

    private boolean isExpired(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String expires = mPixivLoginManager.getExpires();
        Calendar c = Calendar.getInstance();
        Calendar ce = Calendar.getInstance();
        try {
            ce.setTime(sdf.parse(expires));
        } catch (ParseException e) {
            Log.e(LOG_TAG,"isExpired");
            e.printStackTrace();
        }
//        Log.d(LOG_TAG,"Time now: "+c.getTime());
//        Log.d(LOG_TAG,"Time expires: "+ce.getTime());
        int result = c.compareTo(ce);
        return result >= 0;
    }

    private static String getExpires(int addHour){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date dt=new Date();
        sdf.format(dt);
        Calendar c = Calendar.getInstance();
        c.setTime(dt);
        c.add(Calendar.HOUR, addHour);
        Date d = c.getTime();
        return sdf.format(d);
    }

    private static void checkIsSourceUpToDate(){
        Boolean isSourceUpToDate = mPreferenceHandler.getIsSourceUpToDate();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        Calendar c = Calendar.getInstance();
        Date d = c.getTime();

        mPreferenceHandler.setIsSourceUpToDate(isSourceUpToDate && mPreferenceHandler.getLastUpdate().equals(sdf.format(d)));
    }

    private static void requestIgnoreBatteryOptimizations(){
        Intent intent = new Intent();
        String packageName = mContext.getPackageName();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(packageName)){
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
        }
        mContext.startActivity(intent);
    }

    private int DownloadIllust(){
        //Permission checker for API23+
        int permissionCheck = ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck != PermissionChecker.PERMISSION_GRANTED){
            Log.w(LOG_TAG, "Permission: Denied");
            Intent requestPermission = new Intent();
            requestPermission.setClass(this, RequsetPermissionsActivity_API23.class);
            requestPermission.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(requestPermission);
            return 0;
        }
        else{
            Log.i(LOG_TAG, "Permission: Granted");
        }

        if(!isNetConnected()){
            Log.i(LOG_TAG, "No network connection");
            showToast(getString(R.string.toast_noNet));
            return -1;
        }

        if(isLogin() < 0){
            Log.i(LOG_TAG, "Logging... 'pixmuzei'");
            login(D_USERNAME, D_PASSWORD);
        }

        if (isExpired()){
            Log.i(LOG_TAG, "reLogin...");
            login(mPixivLoginManager.getUsername(), mPixivLoginManager.getPassword());
        }

        int illustId;
        PixivArtwork paw = null;
        String originalImageURL;
        String fileType;
        String imageID;
        File file;

        try {
            illustId = Integer.parseInt(getCurrentArtwork().getToken());
            paw = PPAPI.getWorkById(illustId);
        }catch (NullPointerException e){
            Log.e(LOG_TAG, "Current illustId missed");
            return -1;
        }catch (GetDataFailedException e){
            return -1;
        }

        try {
            originalImageURL = paw.getImage_urls().getString("large");
            imageID = String.valueOf(paw.getId());
        } catch (JSONException | NullPointerException e) {
            Log.e(LOG_TAG,"Download illust failed");
            e.printStackTrace();
            return -1;
        }

        fileType = originalImageURL.substring(originalImageURL.length()-4);
        Log.d(LOG_TAG, "original image url: " + originalImageURL);

        file = new File(Environment.getExternalStorageDirectory() + "/pixivmuzei/", "pixiv"+imageID+fileType);
        if(file.exists()){
            Log.d(LOG_TAG, "File exists");
            file.delete();
        }

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(originalImageURL));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.addRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        request.addRequestHeader("Referer", "http://www.pixiv.net");
        request.addRequestHeader("User-Agent", "PixivIOSApp/5.1.1");
        request.setDestinationInExternalPublicDir("/pixivmuzei/", "pixiv" + imageID + fileType);
        downloadManager.enqueue(request);
        showToast(getString(R.string.toast_downloading));

        return 0;
    }

    private void checkUpdate(){
        Thread updateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                PackageInfo pInfo = null;
                try {
                    pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                } catch (PackageManager.NameNotFoundException e) {
                    pInfo.versionName="unknown";
                    pInfo.versionCode=0;
                }
                int version = pInfo.versionCode;
                try {
                    URL e = new URL("http://chino-chan.ddns.net/chino_chan/PixivMuzeiUpdate.php");
                    HttpURLConnection conn;
                    conn = (HttpURLConnection) e.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("POST");
                    conn.connect();
                    String data = "data=" + Integer.toString(version);
                    OutputStream out = conn.getOutputStream();
                    out.write(data.getBytes());
                    out.flush();
                    out.close();
                    int responseCode = conn.getResponseCode();
                    if(responseCode==200){
                        Log.d(LOG_TAG,"Auto check update: Connect Success");
                        InputStream is = conn.getInputStream();
                        final String strResult = getStringFromInputStream(is);
                        Log.d(LOG_TAG,"Auto check update: Response: " + strResult);
                        JSONObject response = new JSONObject(strResult);

                        switch (response.getString("stat")){
                            case "error":
                                Log.e(LOG_TAG,"Auto check update: Server error");
                                return;
                            case "latest":
                                Log.d(LOG_TAG,"Auto check update: latest");
                                break;
                            case "outdated":
                                Log.d(LOG_TAG,"Auto check update: outdated");
                                NotificationManager nfm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                NotificationCompat.Builder nb = new NotificationCompat.Builder(mContext);
                                nb.setSmallIcon(R.drawable.ic_silhouette);
                                nb.setContentTitle(getString(R.string.nf_update_title));
                                nb.setContentText(getString(R.string.nf_update_text) + " " + response.getJSONObject("version_info_latest").getString("name"));
                                nb.setStyle(new NotificationCompat.BigTextStyle().bigText("PixivMuzei " + response.getJSONObject("version_info_latest").getString("name") + "\r\n" + response.getJSONObject("version_info_latest").getString("changelog")));
                                PendingIntent contentIntent = PendingIntent.getService(mContext, 0, new Intent(mContext, DownloadUpdateService.class), PendingIntent.FLAG_UPDATE_CURRENT);
                                nb.setContentIntent(contentIntent);
                                nb.setAutoCancel(true);
                                nb.setVibrate(new long[]{0, 300, 200, 300});
                                nb.setPriority(Notification.PRIORITY_HIGH);
                                Notification nf = nb.build();
                                nfm.notify(0, nf);
                                break;
                        }
                    }
                    else{
                        Log.e(LOG_TAG,"Auto check update: Connect Failed code: "+responseCode);
                    }
                } catch (IOException | JSONException e) {
                    Log.e(LOG_TAG,"Check update failed");
                    e.printStackTrace();
                }
            }
        });
        updateThread.start();
    }

    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return new Binder();
    }
}