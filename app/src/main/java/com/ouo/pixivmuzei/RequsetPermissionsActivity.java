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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class RequsetPermissionsActivity extends Activity{
    private static final String LOG_TAG = "RequsetPermissions";
    private static final int REQUEST_EXTERNAL_STORAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //RequsetPermissionsActivityPermissionsDispatcher.RequsetPermissionsWithCheck();
        showPermissionDialog(new PermissionRequest() {
            @Override
            public void proceed() {
                Log.d(LOG_TAG, "proceed");
            }

            @Override
            public void cancel() {
                Log.d(LOG_TAG, "cancel");
            }
        });
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void RequsetPermissions(){

    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void showPermissionDialog(final PermissionRequest request) {
//        ActivityCompat.requestPermissions(
//                this,
//                new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
//                REQUEST_EXTERNAL_STORAGE
//        );

        new AlertDialog.Builder(this)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.proceed();
                        finish();
                    }
                })
                .setTitle("Permission Denied")
                .setCancelable(false)
                .setMessage("External storage accessing permission required")
                .show();
    }
}
