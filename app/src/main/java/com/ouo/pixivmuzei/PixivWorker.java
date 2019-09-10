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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderContract;
import com.ouo.pixivmuzei.PAPIExceptions.PixivAPIException;
import com.ouo.pixivmuzei.PAPIExceptions.PixivLoginException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.TimeZone;

public class PixivWorker extends Worker {
    private static final String LOG_TAG = "PixivWorker";
    private static Context appContext;
    private static PixivLoginManager mPixivLoginManager;
    private static PreferenceHandler mPreferenceHandler;
    private static Artwork currentArtwork = null;

    public PixivWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        appContext = context;
        mPixivLoginManager = new PixivLoginManager(appContext);
        mPreferenceHandler = new PreferenceHandler(appContext);
    }

    @NonNull
    @Override
    public Result doWork() {
        final String sourceMode = mPreferenceHandler.getConfSourceMode();
        final int loadAmount = mPreferenceHandler.getConfLoadAmount();
        JSONArray contents = null;

        //Try login
        try {
            mPixivLoginManager.login();
        } catch (PixivLoginException e) {
            Log.e(LOG_TAG, "Advance failed");
            e.printStackTrace();
            return Result.retry();
        }

        checkIsSourceUpToDate();
        boolean isSourceUpToDate = mPreferenceHandler.getIsSourceUpToDate();

        if(!isSourceUpToDate){
            Log.i(LOG_TAG, "Updating source data...");

            try{
                contents = PixivAPI.getSource(appContext, sourceMode, loadAmount);
            } catch (PixivAPIException e) {
                Log.e(LOG_TAG, "Advance failed");
                e.printStackTrace();
                return Result.retry();
            }

            mPreferenceHandler.setIsSourceUpToDate(true);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
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

        final Random random = new Random();
        PixivArtwork paw, paw_PPAPI;
        while (true) {
            try {
                final JSONObject content = contents.getJSONObject(random.nextInt(contents.length()));
                paw = new PixivArtwork(content);
                paw_PPAPI = PixivAPI.getWorkById(appContext, paw.getId());
            } catch (IndexOutOfBoundsException | NullPointerException | JSONException | PixivAPIException e) {
                Log.e(LOG_TAG, "Advance failed");
                e.printStackTrace();
                return Result.retry();
            }

            if(mPreferenceHandler.getConfIsNoManga() && paw.getType().equals("manga")) {
                Log.i(LOG_TAG,"It's Manga!!    ID:"+paw.getId());
                continue;
            }

            if(mPreferenceHandler.getConfIsNoR18() && paw.getSanityLevel() >= 6 && !sourceMode.matches("^.*r18.*$")) {
                Log.i(LOG_TAG,"It's R-18!!    ID:"+paw.getId());
                continue;
            }

            final int workId = paw.getId();
            final String illustType = paw.getType();
            if (workId < 0 || illustType == null) {
                continue;
            }

            final String token = String.valueOf(workId);

            final String workUri = "http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + workId;
            final Uri fileUri;
            try {
                Log.i(LOG_TAG,"Downloading illustration...");
                fileUri = PixivAPI.downloadIllust(paw_PPAPI, Objects.requireNonNull(appContext.getExternalCacheDir(), "Can't get ExternalCacheDir").toString());
                Log.i(LOG_TAG,"Download illustration succeeded");
            } catch (PixivAPIException | NullPointerException e) {
                Log.e(LOG_TAG, "Advance failed");
                e.printStackTrace();
                return Result.retry();
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
                    .token(token)
                    .persistentUri(fileUri)
                    .webUri(Uri.parse(workUri))
                    .build();
            ProviderContract.getProviderClient(appContext, appContext.getPackageName()).addArtwork(currentArtwork);
            Log.d(LOG_TAG, "Illust ID: " + currentArtwork.getToken());
            return Result.success();
        }
    }

    private static void checkIsSourceUpToDate(){
        Boolean isSourceUpToDate = mPreferenceHandler.getIsSourceUpToDate();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        Calendar c = Calendar.getInstance();
        Date d = c.getTime();
        mPreferenceHandler.setIsSourceUpToDate(isSourceUpToDate && mPreferenceHandler.getLastUpdate().equals(sdf.format(d)));
    }

    static void enqueueLoad() {
        WorkManager workManager = WorkManager.getInstance(appContext);
        workManager.enqueue(new OneTimeWorkRequest.Builder(PixivWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
}
