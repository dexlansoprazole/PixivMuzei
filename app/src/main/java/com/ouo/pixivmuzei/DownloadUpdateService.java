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

import android.Manifest;
import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.ouo.pixivmuzei.settings.SettingsFragment.getStringFromInputStream;

public class DownloadUpdateService extends Service {
    private static final String LOG_TAG = "DownloadUpdateService";
    private BroadcastReceiver completeReceiver;

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplication().unregisterReceiver(completeReceiver);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        completeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long completeDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                DownloadManager downloadManager = (DownloadManager) getApplication().getSystemService(Context.DOWNLOAD_SERVICE);
                Cursor q = downloadManager.query(new DownloadManager.Query().setFilterById(completeDownloadId));
                if(q==null){
                    showToast(getString(R.string.toast_downloadFail));
                    return;
                }
                q.moveToFirst();
                String path = "file://"+q.getString(q.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                String fType = path.substring(path.length()-3);
                if(fType.equals("apk")){
                    Uri uri = Uri.parse(path);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri,downloadManager.getMimeTypeForDownloadedFile(completeDownloadId));
                    startActivity(install);
                }
            }
        };
        getApplication().registerReceiver(completeReceiver,new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        downloadUpdate();
        return super.onStartCommand(intent, flags, startId);
    }

    private void downloadUpdate(){
        //Permission checker for API23+
        int permissionCheck = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck != PermissionChecker.PERMISSION_GRANTED){
            Log.w(LOG_TAG, "Permission: Denied");
            Intent requestPermission = new Intent();
            requestPermission.setClass(this, RequsetPermissionsActivity.class);
            requestPermission.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(requestPermission);
            return;
        }
        else{
            Log.i(LOG_TAG, "Permission: Granted");
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String versionName = null;
                PackageInfo pInfo = null;
                try {
                    pInfo = getApplication().getPackageManager().getPackageInfo(getApplication().getPackageName(), 0);
                } catch (PackageManager.NameNotFoundException e) {
                    pInfo.versionName="unknown";
                    pInfo.versionCode=0;
                }
                int version = pInfo.versionCode;
                try{
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
                        Log.d(LOG_TAG,"Connect Success");
                        InputStream is = conn.getInputStream();
                        String strResult = getStringFromInputStream(is);
                        JSONObject response = new JSONObject(strResult);

                        switch (response.getString("stat")){
                            case "error":
                                Log.e(LOG_TAG,"Download update: Server error");
                                showToast(getString(R.string.toast_connectFailed));
                                return;
                            case "latest":
                                Log.d(LOG_TAG,"Download update: latest");
                                showToast(getString(R.string.toast_isNewest));
                                return;
                            case "outdated":
                                Log.d(LOG_TAG,"Download update: outdated");
                                versionName = response.getJSONObject("version_info_latest").getString("name");
                                File f = new File(Environment.getExternalStorageDirectory().getPath() + "/Download/PixivMuzei_"+versionName+".apk");
                                if (f.exists())
                                    f.delete();

                                DownloadManager downloadManager = (DownloadManager) getApplication().getSystemService(Context.DOWNLOAD_SERVICE);
                                String apkUrl = "http://chino-chan.ddns.net/chino_chan/Releases/PixivMuzei/PixivMuzei_"+versionName+".apk";
                                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
                                request.allowScanningByMediaScanner();
                                request.setVisibleInDownloadsUi(true);
                                request.setDestinationInExternalPublicDir("/Download/", "PixivMuzei_"+versionName+".apk");
                                //request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                downloadManager.enqueue(request);
                                showToast(getString(R.string.toast_downloading));
                                break;
                        }
                    }
                    else{
                        Log.d(LOG_TAG,"Connect Failed code: "+responseCode);
                        showToast(getString(R.string.toast_connectFailed));
                    }
                }catch (IOException | JSONException e) {
                    showToast(getString(R.string.toast_connectFailed));
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showToast(final String message){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
