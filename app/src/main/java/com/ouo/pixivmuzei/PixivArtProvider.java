/*
 * Copyright (C) 2019  Guo Zheng-Yan
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
 *
 */
package com.ouo.pixivmuzei;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;
import com.ouo.pixivmuzei.PAPIExceptions.PixivAPIException;
import com.ouo.pixivmuzei.PAPIExceptions.PixivLoginException;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PixivArtProvider extends MuzeiArtProvider {
    private static final String LOG_TAG = "PixivArtProvider";
    private static final String D_USERNAME = "user_pyda3858";
    private static final String D_PASSWORD = "pixivmuzeiservice0";
    private static final int COMMAND_ID_DOWNLOAD = 1;

    @Override
    protected void onLoadRequested(boolean initial) {
        PixivWorker.enqueueLoad();
    }

    @NonNull
    @Override
    protected List<UserCommand> getCommands(@NonNull Artwork artwork) {
        List<UserCommand> commands = new ArrayList<>();
        commands.add(new UserCommand(COMMAND_ID_DOWNLOAD,getContext().getString(R.string.action_download)));
        return commands;
    }

    @Override
    protected void onCommand(@NonNull Artwork artwork, int id) {
        switch (id) {
            case 1: //Download
                if(DownloadIllust(getContext(), artwork)){
                    showToast(getContext().getString(R.string.toast_downloadFail));
                    return;
                }
                break;
        }
    }

    private boolean DownloadIllust(Context context, Artwork artwork){
        PixivLoginManager plm = new PixivLoginManager(context);
        int permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck != PermissionChecker.PERMISSION_GRANTED){
            Log.w(LOG_TAG, "Permission: Denied");
            Intent requestPermission = new Intent();
            requestPermission.setClass(context, RequsetPermissionsActivity.class);
            requestPermission.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(requestPermission);
            //TODO: Start download after permission granted
            return false;
        }
        else{
            Log.i(LOG_TAG, "Permission: Granted");
        }

        if(!isNetConnected()){
            Log.w(LOG_TAG, "No network connection");
            showToast(context.getString(R.string.toast_noNet));
            return true;
        }

        if(plm.loginStatus() == PixivLoginManager.LOGIN_STATUS_OUT) {
            Log.i(LOG_TAG, "Login by pixmuzei...");
            try {
                plm.login(D_USERNAME, D_PASSWORD);
            } catch (PixivLoginException e) {
                Log.e(LOG_TAG, "Download illust failed");
                e.printStackTrace();
                return true;
            }
        }

        PixivArtwork paw;
        String originalImageURL;
        String fileType;
        String imageID;
        File file;

        try {
            paw = PixivAPI.getWorkById(context, Integer.parseInt(Objects.requireNonNull(artwork.getToken(), "No illustId")));
        } catch (PixivAPIException e) {
            Log.e(LOG_TAG, "Download illust failed");
            e.printStackTrace();
            return true;
        }

        try {
            originalImageURL = paw.getImage_urls().getString("large");
            imageID = String.valueOf(paw.getId());
        } catch (JSONException | NullPointerException e) {
            Log.e(LOG_TAG,"Download illust failed");
            e.printStackTrace();
            return true;
        }

        fileType = originalImageURL.substring(originalImageURL.length()-4);
        Log.d(LOG_TAG, "original image url: " + originalImageURL);

        file = new File(Environment.getExternalStorageDirectory() + "/pixivmuzei/", "pixiv"+imageID+fileType);
        if(file.exists()){
            Log.d(LOG_TAG, "File exists");
            if(!file.delete()){
                Log.w(LOG_TAG, "Delete duplicate failed");
            }
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(originalImageURL));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.addRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        request.addRequestHeader("Referer", "http://www.pixiv.net");
        request.addRequestHeader("User-Agent", "PixivAndroidApp/5.0.64 (Android 6.0)");
        request.setDestinationInExternalPublicDir("/pixivmuzei/", "pixiv" + imageID + fileType);
        downloadManager.enqueue(request);
        showToast(context.getString(R.string.toast_downloading));
        return false;
    }

    private boolean isNetConnected(){
        ConnectivityManager connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void showToast(final String message){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}