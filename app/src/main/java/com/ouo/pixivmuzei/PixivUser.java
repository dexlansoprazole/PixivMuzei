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
 *
 */

package com.ouo.pixivmuzei;

import org.json.JSONException;
import org.json.JSONObject;

public class PixivUser {
    public JSONObject userData;

    public PixivUser(JSONObject userData){
        this.userData = userData;
    }

    public int getUID(){
        try {
            return userData.getInt("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
