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
import android.net.Uri;
import android.util.Log;

import com.ouo.pixivmuzei.PAPIExceptions.GetDataFailedException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PixivPublicAPI {
    private static final String LOG_TAG = "PixivPublicAPI";
    private static final String USER_AGENT = "PixivAndroidApp/5.0.64 (Android 6.0)";
    private static final String REFERER = "http://www.pixiv.net";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String CLIENT_ID = "KzEZED7aC0vird8jWyHM38mXjNTY";
    private static final String CLIENT_SECRET = "W9JZoJe00qPvJsiyCGT3CCtC6ZUtdpKpzMbNlUGP";
    private static final String HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c";

    private static JSONObject HTTPRequest(String url, String method, String parameters, String accessToken){
        try {
            URL e = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) e.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            if(parameters != null)
                conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern ( "yyyy-MM-dd'T'HH:mm:ss:SS" )
                    .toFormatter ();
            String localTime = LocalDateTime.now().format(formatter);
            String localTime_hash = md5(localTime + HASH_SECRET);
            conn.setRequestProperty("X-Client-Time", localTime);
            conn.setRequestProperty("X-Client-Hash", localTime_hash);
            if(accessToken != null)
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestMethod(method);
            conn.connect();
            if(parameters != null) {
                OutputStream out = conn.getOutputStream();
                out.write(parameters.getBytes());
                out.flush();
                out.close();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                InputStream is = conn.getInputStream();
                String r = getStringFromInputStream(is);
                conn.disconnect();
                Log.d(LOG_TAG, "Connect succeeded\nResponse: " + r);
                return new JSONObject(r);
            } else {
                Log.e(LOG_TAG, "Connect Failed:\nResponse code: " + responseCode + "\nError: " + getStringFromInputStream(conn.getErrorStream()));
                return null;
            }
        } catch (IOException | JSONException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    static JSONObject login(final String username, final String password) throws GetDataFailedException{
        String parameters = "username=" + username +
                "&password=" + password +
                "&grant_type=password" +
                "&client_id=" + CLIENT_ID +
                "&client_secret=" + CLIENT_SECRET;

        try {
            return HTTPRequest("https://oauth.secure.pixiv.net/auth/token", "POST", parameters, null).getJSONObject("response");
        } catch (JSONException | NullPointerException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Login failed");
        }
    }

    static String getRanking(Context context, String mode, String page, String per_page) throws GetDataFailedException{
        PixivLoginManager plm = new PixivLoginManager(context);
        String url = "https://public-api.secure.pixiv.net/v1/ranking/all?mode=" + mode + "&page=" + page + "&per_page=" + per_page + "&image_sizes=large&include_stats=true";
        try {
            String r = HTTPRequest(url, "GET", null, plm.getAccessToken()).getJSONArray("response").getJSONObject(0).getJSONArray("works").toString();
            Log.i(LOG_TAG,"Get ranking succeeded(page "+page+")");
            return r;
        } catch (JSONException | NullPointerException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Get ranking failed");
        }
    }

    static JSONObject getRanking_personalized(Context context, String mode, String page, String per_page) throws GetDataFailedException{
        PixivLoginManager plm = new PixivLoginManager(context);
        String url = null;
        switch (mode){
            case "userFav":
                url = "https://public-api.secure.pixiv.net/v1/me/favorite_works.json?image_sizes=large&page=" + page + "&publicity=public&per_page=" + per_page;
                break;
            case  "following":
                url = "https://public-api.secure.pixiv.net/v1/me/following/works.json?include_sanity_level=true&per_page=" + per_page + "&page=" + page + "&include_stats=true&image_sizes=large";
                break;
            case "recommend":
                url = "https://app-api.pixiv.net/v1/illust/recommended?image_sizes=large&page=" + page + "&publicity=public&per_page=" + per_page;
                break;
        }

        try {
            JSONObject r = HTTPRequest(url, "GET", null, plm.getAccessToken());
            Log.i(LOG_TAG,"Get ranking succeeded(page "+page+")");
            return r;
        } catch (NullPointerException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Get ranking failed");
        }
    }

    static JSONObject refreshAccessToken(String refreshToken) throws GetDataFailedException {
        String parameters = "refresh_token=" + refreshToken +
                "&grant_type=refresh_token" +
                "&client_id=" + CLIENT_ID +
                "&client_secret=" + CLIENT_SECRET;

        try {
            JSONObject r = HTTPRequest("https://oauth.secure.pixiv.net/auth/token", "POST", parameters, null).getJSONObject("response");
            return r;
        } catch (JSONException | NullPointerException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Refresh accessToken failed");
        }
    }

    static Uri downloadIllust(PixivArtwork work, String path) throws GetDataFailedException{
        String originalImageURL;
        String imageID;
        File outputFile;
       URL ImageURL;
        HttpURLConnection conn;
        try {
            originalImageURL = work.getImage_urls().getString("large");
            imageID = String.valueOf(work.getId());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Download illustration failed");
        }

        //check for cache
        File imageCache = new File(path,imageID);
        if(imageCache.exists()){
            Log.d(LOG_TAG,"cache found");
            return Uri.parse("file://" + imageCache.getAbsolutePath());
        }

        Log.d(LOG_TAG, "original image url: " + originalImageURL);
        outputFile = new File(path, imageID);
        originalImageURL = getImageUrlToShow(originalImageURL);
        Log.d(LOG_TAG, "Image Url To Show: " + originalImageURL);

        try {
            ImageURL = new URL(originalImageURL);
            conn = (HttpURLConnection) ImageURL.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Referer", REFERER);
            conn.setRequestProperty("Content-Type", CONTENT_TYPE);
            //conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoInput(true);
            conn.connect();

            final int status = conn.getResponseCode();

            switch (status) {
                case 200:
                    Log.d(LOG_TAG, "Connect succeeded");
                    break;

                default:
                    throw new GetDataFailedException("Connect Failed: " + status);
            }

            final FileOutputStream fileStream = new FileOutputStream(outputFile);
            final InputStream inputStream = conn.getInputStream();
            final byte[] buffer = new byte[1024 * 50];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                fileStream.write(buffer, 0, read);
            }
            fileStream.close();
            inputStream.close();
        } catch (final IOException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Download cache failed");
        }
        Log.d(LOG_TAG, "File path: " + outputFile.getAbsolutePath());
        return Uri.parse("file://" + outputFile.getAbsolutePath());
    }

    static PixivArtwork getWorkById(Context context, int illustId) throws GetDataFailedException{
        PixivLoginManager plm = new PixivLoginManager(context);
        String accessToken = plm.getAccessToken();
        HttpURLConnection conn;
        String sWorkURL = "https://public-api.secure.pixiv.net/v1/works/"+illustId+".json?image_sizes=large";
        PixivArtwork result;
        int connStatus;
        JSONObject workInfo;
        try {
            URL workURL = new URL(sWorkURL);
            conn = (HttpURLConnection) workURL.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", CONTENT_TYPE);
            conn.setRequestProperty("User-agent", USER_AGENT);
            conn.setRequestProperty("Referer", REFERER);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoInput(true);
            conn.connect();
            connStatus = conn.getResponseCode();
            if (connStatus != 200) {
                throw new GetDataFailedException("Connect Failed: " + connStatus);
            }
            else {
                Log.d(LOG_TAG, "Connect succeeded");

                //inputStream to JSONObject
                InputStream inputStream = conn.getInputStream();
                BufferedReader streamReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseStrBuilder = new StringBuilder();
                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    responseStrBuilder.append(inputStr);
                workInfo = new JSONObject(responseStrBuilder.toString());
                Log.d(LOG_TAG,"response: "+workInfo.getJSONArray("response").getJSONObject(0).toString());
                result = new PixivArtwork(workInfo.getJSONArray("response").getJSONObject(0));
                inputStream.close();
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            throw new GetDataFailedException("Getting work by ID failed");
        }
        return result;
    }

    private static String getImageUrlToShow(String imageUri){
        final Pattern uriPattern = Pattern.compile("^(https?://.+?/)img-original(.+)$");
        final Pattern uriPattern_app = Pattern.compile("^https?://.+c/.+/img-master?(/img.+)_master1200.+$");
        final Matcher matcher = uriPattern.matcher(imageUri);
        final Matcher mather_app = uriPattern_app.matcher(imageUri);
        if (matcher.matches()) {
            final String base = matcher.group(1), path = matcher.group(2);
            String p = path.substring(0, path.length() - 4);
            //Log.d(LOG_TAG, "base:" + base);
            //Log.d(LOG_TAG,"path:"+p);
            return base + "c/1200x1200/img-master" + p +"_master1200.jpg";
        }
        else if(mather_app.matches()){
            Log.d(LOG_TAG, "App form Matched: " + imageUri);
            return imageUri;
        }
        else
            Log.e(LOG_TAG, "Match failed: " + imageUri);
        return null;
    }

    private static String getStringFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        is.close();
        String state = os.toString();
        os.close();
        return state;
    }

    private static String md5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(input.getBytes());
        byte[] digests = md.digest();
        StringBuilder hexStr = new StringBuilder();
        for(byte d: digests){
            hexStr.append(toHex(d));
        }
        return hexStr.toString();
    }

    private static String toHex(byte b){
        return (""+"0123456789abcdef".charAt(0xf&b>>4)+"0123456789abcdef".charAt(b&0xf));
    }
}