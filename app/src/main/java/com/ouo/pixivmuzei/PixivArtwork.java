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

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PixivArtwork {
    private static final String LOG_TAG = "PixivArtwork";
    public JSONObject content;
    public JSONObject work;

    PixivArtwork(JSONObject content){
        try {
            if(content.has("work"))
                this.work = content.getJSONObject("work");
            else
                this.work = content;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public int getRank() {
        try {
            return content.getInt("rank");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getPreviousRank(){
        try {
            return content.getInt("previous_rank");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getId(){
        try {
            return work.getInt("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getWidth(){
        try {
            return work.getInt("width");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getHeight(){
        try {
            return work.getInt("height");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getPublicity(){
        try {
            return work.getInt("publicity");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getPage_count(){
        try {
            return work.getInt("page_count");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public String getTitle(){
        try {
            return work.getString("title");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getCaption(){
        try {
            return work.getString("caption");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getTools(){
        try {
            return work.getString("tools");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getAge_limit(){
        try {
            return work.getString("age_limit");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getCreated_time(){
        try {
            return work.getString("created_time");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getReuploaded_time(){
        try {
            return work.getString("reuploaded_time");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getType(){
        try {
            return work.getString("type");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public JSONArray getTags(){
        try {
            return work.getJSONArray("tags");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public JSONObject getImage_urls(){
        try {
            return work.getJSONObject("image_urls");
        } catch (JSONException e) {
            Log.w(LOG_TAG,e.toString());
            try {
                work.getJSONObject("matadata").getJSONArray("pages").getJSONObject(0);
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }
        return null;
    }

    public JSONObject getUser(){
        try {
            return work.getJSONObject("user");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean getIsManga(){
        try {
            if(!(work.getString("is_manga") ==null)) {
                return work.getBoolean("is_manga");
            }
            else
                return false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getSanityLevel(){
        try {
            return work.getInt("sanity_level");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
