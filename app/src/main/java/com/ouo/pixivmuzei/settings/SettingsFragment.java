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

package com.ouo.pixivmuzei.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.FontResourcesParserCompat;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderClient;
import com.google.android.apps.muzei.api.provider.ProviderContract;
import com.ouo.pixivmuzei.DownloadUpdateService;
import com.ouo.pixivmuzei.PixivLoginManager;
import com.ouo.pixivmuzei.PixivWorker;
import com.ouo.pixivmuzei.PreferenceHandler;
import com.ouo.pixivmuzei.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsFragment extends PreferenceFragment{
    private static final String LOG_TAG = "SettingsFragment";
    private LayoutInflater inflater;
    private EditText txtUsername;
    private EditText txtPassword;
    private PreferenceScreen pref_changeInterval;
    private AlertDialog loginDialog;
    private AlertDialog logoutDialog;
    private AlertDialog timePickerDialog;
    private AlertDialog updateDialog;
    private AlertDialog loadingDialog;
    private AlertDialog deleteCachesDialog;
    private AlertDialog licenseDialog;
    private AlertDialog openSourceLicensesDialog;
    private AlertDialog loadAmountDialog;
    private Handler mHandler;
    private Context mContext;
    private BroadcastReceiver completeReceiver;
    public static boolean isLoadAmountChanged = false;
    public static boolean isSourceModeChanged = false;
    private static PreferenceHandler mPreferenceHandler = null;
    private static PixivLoginManager mPixivLoginManager = null;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        pref_changeInterval = (PreferenceScreen) findPreference("pref_changeInterval");
        mHandler = new Handler();
        mPreferenceHandler = new PreferenceHandler(mContext);
        mPixivLoginManager = new PixivLoginManager(mContext);
        setLocale();
        updateUI();
    }


    @Override
    public View onCreateView(LayoutInflater inflater2, ViewGroup container, Bundle savedInstanceState) {
        inflater = ((Activity)mContext).getLayoutInflater();
        View view = super.onCreateView(inflater2, container, savedInstanceState);
        CreateLoadingDialog();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(!mPreferenceHandler.getIsSourceUpToDate()) {
            ProviderClient pc = ProviderContract.getProviderClient(mContext, mContext.getPackageName());
            Artwork currentArtwork = getCurrentArtwork();
            if (currentArtwork != null)
                pc.setArtwork(currentArtwork);
            Log.i(LOG_TAG, "Cache refreshed");
        }
    }

    @Override
    public void onResume() {
        PreferenceManager.getDefaultSharedPreferences(mContext).registerOnSharedPreferenceChangeListener(mListener);
        super.onResume();
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(mContext).unregisterOnSharedPreferenceChangeListener(mListener);
        super.onPause();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case "logIO":
                ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
                if(netInfo == null || netInfo.getState() != NetworkInfo.State.CONNECTED){
                    Log.i(LOG_TAG, "No network connection");
                    showToast(getString(R.string.toast_noNet));
                    break;
                }

                if (mPixivLoginManager.loginStatus() == PixivLoginManager.LOGIN_STATUS_PERSONAL) {
                    createDialog("logout");
                    logoutDialog.show();
                }
                else{
                    createDialog("login");
                    loginDialog.show();
                }
                break;
            case "pref_changelog":
                AlertDialog changeLogDialog;
                ChangeLog changeLog = new ChangeLog(mContext);
                changeLogDialog = changeLog.getLogDialog();
                changeLogDialog.show();
                break;
            case "pref_changeInterval":
                createDialog("time");
                timePickerDialog.show();
                break;
            case "pref_checkUpdate":
                checkUpdate();
                break;
            case "pref_deleteCaches":
                createDialog("deleteCaches");
                deleteCachesDialog.show();
                break;
            case "pref_license":
                createDialog("license");
                licenseDialog.show();
                break;
            case "pref_openSourceLicenses":
                createDialog("openSourceLicenses");
                openSourceLicensesDialog.show();
                break;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private Artwork getCurrentArtwork(){
        Artwork result = null;
        String[] mProjection = {"_id", "_data", "title", "byline", "token", "persistent_uri", "web_uri"};
        String mSelectionClause = null;
        String[] mSelectionArgs = null;
        String mSortOrder = "_id desc";
        Cursor mCursor = mContext.getContentResolver().query(ProviderContract.getContentUri(mContext.getPackageName()), mProjection, mSelectionClause, mSelectionArgs, mSortOrder);
        Map<String, String> map = new HashMap<String, String>();
        if (mCursor != null) {
            if(mCursor.getCount() >= 2) {
                mCursor.move(2);
                for (int i = 0; i < mCursor.getColumnCount(); i++)
                    map.put(mCursor.getColumnName(i), mCursor.getString(i));
                result = new Artwork.Builder()
                        .title(map.get("title"))
                        .byline(map.get("byline"))
                        .token(map.get("token"))
                        .persistentUri(Uri.parse(map.get("persistent_uri")))
                        .webUri(Uri.parse(map.get("web_uri")))
                        .build();
            }
            mCursor.close();
        }
        return result;
    }

    private void setLocale(){
        Locale locale = getLocaleFromPref();
        Resources res = getResources();
        Configuration conf = res.getConfiguration();
        conf.setLocale(locale);
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(conf, dm);
        //TODO: updateConfiguration replacement
    }

    private Locale getLocaleFromPref(){
        final Activity activity = getActivity();
        final SharedPreferences languagePre = PreferenceManager.getDefaultSharedPreferences(activity);
        String lang = languagePre.getString("pref_language", getString(R.string.pref_language_default));
        Locale locale;
        switch (lang) {
            case "en-rUS":
                locale = new Locale("en", "US");
                break;
            case "zh-rTW":
                locale = new Locale("zh", "TW");
                break;
            default:
                locale = new Locale(Locale.getDefault().getLanguage(), Locale.getDefault().getCountry());
                break;
        }
        return locale;
    }

    public void updateUI(){
        PreferenceScreen logIO = (PreferenceScreen)findPreference("logIO");
        PreferenceScreen pref_license = (PreferenceScreen)findPreference("pref_license");
        if(mPixivLoginManager.loginStatus() == PixivLoginManager.LOGIN_STATUS_PERSONAL)
            logIO.setTitle(getString(R.string.labelLogout));
        else
            logIO.setTitle(getString(R.string.pref_login));
        int changeInterval = mPreferenceHandler.getConfChangeInterval();
        String s = String.valueOf(changeInterval/1440)+getString(R.string.txtDays)+" "+String.valueOf((changeInterval%1440)/60)+getString(R.string.txtHours)+" "+String.valueOf(changeInterval%1440%60)+getString(R.string.txtMinutes);
        pref_changeInterval.setSummary(s);

        s = mPreferenceHandler.getConfSourceMode();
        if(s.matches("^.*r18.*$"))
            getPreferenceScreen().findPreference("pref_noR18").setEnabled(false);
        else
            getPreferenceScreen().findPreference("pref_noR18").setEnabled(true);

        //Get application name
        ApplicationInfo applicationInfo = mContext.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        String appName = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : mContext.getString(stringId);

        //Get version name
        String versionName = null;
        try {
            versionName= mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Get version name failed");
            e.printStackTrace();
            return;
        }
        pref_license.setTitle(appName + " v" + versionName);
    }

    public void login() {
        Thread loginThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingDialog.show();
                    }
                });
                String username = txtUsername.getText().toString();
                String password = txtPassword.getText().toString();
                if(mPixivLoginManager.login(username, password)){
                    showToast(getString(R.string.toast_loginFail));
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadingDialog.cancel();
                            loginDialog.show();
                        }
                    });
                    return;
                }

                if (mContext != null && !((Activity)mContext).isDestroyed()) {
                    loadingDialog.cancel();
                    showToast(getString(R.string.toast_loginSuccess));
                    ((SettingsActivity) mContext).restartFragment();
                }
            }
        });
        loginThread.start();
        mPreferenceHandler.setIsSourceUpToDate(false);
    }

    private void logout(){
        mPixivLoginManager.deleteLoginInfo();
        Log.d(LOG_TAG, "Logged Out");
        Log.d(LOG_TAG,"accessToken: " + mPixivLoginManager.getAccessToken());
        Log.d(LOG_TAG,"refreshToken: " + mPixivLoginManager.getRefreshToken());
        Log.d(LOG_TAG,"expires: "+mPixivLoginManager.getExpires());

        String sourceMode = mPreferenceHandler.getConfSourceMode();
        if(sourceMode.equals("userFav") || sourceMode.equals("following"))
            mPreferenceHandler.setConfSourceMode(getString(R.string.pref_sourceMode_default));

        mPreferenceHandler.setIsSourceUpToDate(false);
        showToast(getString(R.string.toast_logedOut));
        updateUI();
        ((SettingsActivity) mContext).restartFragment();
    }

    private void showToast(final String message){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createDialog(String type){
        switch (type){
            case "login":
                AlertDialog.Builder loginDialogBuilder = new AlertDialog.Builder(getActivity());
                final View loginView;
                loginView = inflater.inflate(R.layout.login, null);
                //progressBar = (ProgressBar) loginView.findViewById(R.id.progressBar);
                txtUsername = (EditText) loginView.findViewById(R.id.username);
                txtPassword = (EditText) loginView.findViewById(R.id.password);

                if (mPixivLoginManager.getLastUsername() != null && mPixivLoginManager.getLastPassword() != null) {
                    txtUsername.setText(mPixivLoginManager.getLastUsername());
                    txtPassword.setText(mPixivLoginManager.getLastPassword());
                }
                loginDialogBuilder.setTitle(getString(R.string.labelLogin));
                loginDialogBuilder.setView(loginView);
                loginDialogBuilder.setPositiveButton(getString(R.string.btnTxtLogin), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (mPixivLoginManager.loginStatus() != PixivLoginManager.LOGIN_STATUS_PERSONAL) {
                            login();
                        }
                    }
                });
                loginDialogBuilder.setNeutralButton(getString(R.string.btnTxtCancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                loginDialog = loginDialogBuilder.create();
                break;
            case "logout":
                //logoutDialog
                AlertDialog.Builder logoutDialogBuilder = new AlertDialog.Builder(getActivity());
                final View logoutView;
                logoutView = inflater.inflate(R.layout.logout, null);
                TextView txtusername = (TextView) logoutView.findViewById(R.id.txtLogout);
                txtusername.setText(mPixivLoginManager.getUsername());
                logoutDialogBuilder.setTitle(getString(R.string.labelLogout));
                logoutDialogBuilder.setView(logoutView);
                logoutDialogBuilder.setPositiveButton(getString(R.string.btnTxtLogout), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        logout();
                    }
                });
                logoutDialogBuilder.setNeutralButton(getString(R.string.btnTxtCancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
                logoutDialog = logoutDialogBuilder.create();
                break;
            case "time":
                //timePickerDialog
                AlertDialog.Builder timePickerBuilder = new AlertDialog.Builder(getActivity());
                int changeInterval = mPreferenceHandler.getConfChangeInterval();
                View timePickerView = inflater.inflate(R.layout.timepicker, null);
                final NumberPicker numPicker01 = (NumberPicker) timePickerView.findViewById(R.id.numPicker01);
                final NumberPicker numPicker02 = (NumberPicker) timePickerView.findViewById(R.id.numPicker02);
                final NumberPicker numPicker03 = (NumberPicker) timePickerView.findViewById(R.id.numPicker03);
                numPicker01.setMinValue(0);
                numPicker01.setMaxValue(31);
                numPicker02.setMinValue(0);
                numPicker02.setMaxValue(23);
                numPicker03.setMinValue(0);
                numPicker03.setMaxValue(59);
                numPicker01.setValue(changeInterval/1440);
                numPicker02.setValue((changeInterval%1440)/60);
                numPicker03.setValue(changeInterval%1440%60);
                timePickerBuilder.setTitle(getString(R.string.pref_changeInterval));
                timePickerBuilder.setView(timePickerView);
                timePickerBuilder.setNeutralButton(getString(R.string.btnTxtCancel),new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                timePickerBuilder.setPositiveButton(getString(R.string.btnTxtConfirm), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(LOG_TAG,numPicker01.getValue()+" : "+numPicker02.getValue()+" : "+numPicker03.getValue());
                        int r=numPicker01.getValue()*1440+numPicker02.getValue()*60+numPicker03.getValue();
                        if(numPicker01.getValue()==0&&numPicker02.getValue()==0&&numPicker03.getValue()==0){
                            showToast(getString(R.string.toast_interval_is_zero));
                            r = mContext.getResources().getInteger(R.integer.pref_changeInterval_default);
                        }
                        mPreferenceHandler.setConfChangeInterval(r);
                        updateUI();
                    }
                });
                timePickerDialog = timePickerBuilder.create();
                break;
            case "deleteCaches":
                AlertDialog.Builder deleteCachesDialogBuilder = new AlertDialog.Builder(mContext);
                final View deleteCachesView = inflater.inflate(R.layout.delete_caches_confirm, null);
                TextView txtCacheSize = (TextView) deleteCachesView.findViewById(R.id.txtCacheSize);
                TextView valueCacheSize = (TextView) deleteCachesView.findViewById(R.id.valueCacheSize);
                txtCacheSize.setText(getString(R.string.txt_cacheSize));
                valueCacheSize.setText(cacheSize() + " MB");
                deleteCachesDialogBuilder.setTitle(getString(R.string.pref_deleteCaches));
                deleteCachesDialogBuilder.setView(deleteCachesView);
                deleteCachesDialogBuilder.setNeutralButton(getString(R.string.btnTxtCancel),new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                deleteCachesDialogBuilder.setPositiveButton(getString(R.string.btn_delete), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteCaches();
                    }
                });
                deleteCachesDialog = deleteCachesDialogBuilder.create();
                break;
            case "license": {
                AlertDialog.Builder licenseDialogBuilder = new AlertDialog.Builder(mContext);
                final View licenseView = inflater.inflate(R.layout.license, null);
                final LinearLayout licenseLayout = (LinearLayout) licenseView.findViewById(R.id.licenseView);

                TextView txtTitle = new TextView(mContext);
                txtTitle.setText(getString(R.string.pref_license_title));
                txtTitle.setTextSize(30);
                txtTitle.setBackgroundColor(Color.WHITE);
                txtTitle.setTextColor(Color.BLACK);
                TextView txtLicense = new TextView(mContext);
                txtLicense.setBackgroundColor(Color.WHITE);
                txtLicense.setTextColor(Color.BLACK);

                StringBuilder text = new StringBuilder();
                try {
                    InputStream inputStream = getResources().openRawResource(R.raw.license_pixivmuzei);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                    reader.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Read license failed");
                    e.printStackTrace();
                }
                txtLicense.setText(text);
                licenseLayout.addView(txtTitle);
                licenseLayout.addView(txtLicense);


                licenseDialogBuilder.setTitle(getString(R.string.pref_openSourceLicenses_title));
                licenseDialogBuilder.setView(licenseView);
                licenseDialogBuilder.setPositiveButton(getString(R.string.btnTxtConfirm), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                licenseDialog = licenseDialogBuilder.create();
                break;
            }
            case "openSourceLicenses": {
                AlertDialog.Builder openSourceLicensesDialogBuilder = new AlertDialog.Builder(mContext);
                final View licensesView = inflater.inflate(R.layout.license, null);
                final LinearLayout licensesLayout = (LinearLayout) licensesView.findViewById(R.id.licenseView);

                List<String> licenses = Arrays.asList(getResources().getStringArray(R.array.licenses));
                List<String> licenses_filename = Arrays.asList(getResources().getStringArray(R.array.licenses_filename));
                for (int i = 0; i < licenses.size(); i++) {
                    TextView txtTitle = new TextView(mContext);
                    txtTitle.setText(licenses.get(i));
                    txtTitle.setTextSize(30);
                    txtTitle.setBackgroundColor(Color.WHITE);
                    txtTitle.setTextColor(Color.BLACK);
                    TextView txtLicense = new TextView(mContext);
                    txtLicense.setBackgroundColor(Color.WHITE);
                    txtLicense.setTextColor(Color.BLACK);

                    StringBuilder text = new StringBuilder();
                    try {
                        InputStream inputStream = getResources().openRawResource(getResources().getIdentifier(licenses_filename.get(i), "raw", "com.ouo.pixivmuzei"));
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String line;

                        while ((line = reader.readLine()) != null) {
                            text.append(line);
                            text.append('\n');
                        }
                        reader.close();
                    }
                    catch (IOException e) {
                        Log.e(LOG_TAG, "Read license failed");
                        e.printStackTrace();
                    }
                    txtLicense.setText(text);
                    licensesLayout.addView(txtTitle);
                    licensesLayout.addView(txtLicense);
                }

                openSourceLicensesDialogBuilder.setTitle(getString(R.string.pref_openSourceLicenses_title));
                openSourceLicensesDialogBuilder.setView(licensesView);
                openSourceLicensesDialogBuilder.setPositiveButton(getString(R.string.btnTxtConfirm), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                openSourceLicensesDialog = openSourceLicensesDialogBuilder.create();
                break;
            }
        }
    }

    private void CreateUpdateDialog(final String versionName, final String changelog){
        final View updateView;
        final AlertDialog.Builder updateDialogBuilder = new AlertDialog.Builder(getActivity());
        updateView = inflater.inflate(R.layout.new_version, null);
        TextView txtNewVersion = (TextView) updateView.findViewById(R.id.txtVersion);
        TextView txtVersionName = (TextView) updateView.findViewById(R.id.txtVersionName);
        TextView txtChangelog = (TextView) updateView.findViewById(R.id.txtChangelog);
        txtNewVersion.setText(getString(R.string.txtVersion));
        txtVersionName.setText(versionName);
        txtChangelog.setText(changelog);
        updateDialogBuilder.setTitle(getString(R.string.txtNewVersion));
        updateDialogBuilder.setView(updateView);
        updateDialogBuilder.setPositiveButton(getString(R.string.btnDownloadUpdate), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent downloadUpdateService = new Intent(mContext, DownloadUpdateService.class);
                mContext.startService(downloadUpdateService);
            }
        });
        updateDialogBuilder.setNeutralButton(getString(R.string.btnTxtCancel),new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        updateDialog = updateDialogBuilder.create();
    }

    private void CreateLoadingDialog(){
        AlertDialog.Builder loadingDialogBuilder = new AlertDialog.Builder(mContext);
        final View loadingView = inflater.inflate(R.layout.loading, null);
        loadingDialogBuilder.setView(loadingView);
        loadingDialogBuilder.setCancelable(false);
        loadingDialog = loadingDialogBuilder.create();
        loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            //dLog.i(LOG_TAG, "instance variable key=" + key);
            switch (key) {
                case "pref_language":
                    setLocale();
                    ((SettingsActivity) mContext).restartFragment();
                    break;
                case "pref_sourceMode":
                    Log.d(LOG_TAG,"PreferenceChanged : pref_sourceMode");
                    String s = mPreferenceHandler.getConfSourceMode();
                    if(s.matches("^.*r18.*$"))
                        getPreferenceScreen().findPreference("pref_noR18").setEnabled(false);
                    else
                        getPreferenceScreen().findPreference("pref_noR18").setEnabled(true);


                    mPreferenceHandler.setIsSourceUpToDate(false);
                    break;
                case "pref_loadAmount":
                    Log.d(LOG_TAG,"PreferenceChanged : pref_loadAmount");
                    mPreferenceHandler.setIsSourceUpToDate(false);
                    break;
                case "pref_autoCheckUpdate":
                    Log.d(LOG_TAG,"PreferenceChanged : pref_autoCheckUpdate");
                    break;
            }
        }
    };

    private void checkUpdate(){
        //Check network status
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        if(netInfo == null || !netInfo.isConnected()){
            showToast(getString(R.string.toast_noNet));
            return;
        }

        //Check update thread
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
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadingDialog.show();
                        }
                    });
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
                        loadingDialog.cancel();
                        Log.d(LOG_TAG,"Connect Success");
                        InputStream is = conn.getInputStream();
                        final String strResult = getStringFromInputStream(is);
                        Log.d(LOG_TAG,"Response: "+strResult);
                        final JSONObject response = new JSONObject(strResult);

                        switch (response.getString("stat")){
                            case "error":
                                showToast(getString(R.string.toast_connectFailed));
                                return;
                            case "latest":
                                showToast(getString(R.string.toast_isNewest));
                                break;
                            case "outdated":
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            CreateUpdateDialog(response.getJSONObject("version_info_latest").getString("name"), response.getJSONObject("version_info_latest").getString("changelog"));
                                        } catch (JSONException e1) {
                                            e1.printStackTrace();
                                        }

                                        updateDialog.show();
                                    }
                                });
                                break;
                        }
                    }
                    else{
                        loadingDialog.cancel();
                        Log.d(LOG_TAG,"Connect Failed  code: "+responseCode);
                        showToast(getString(R.string.toast_connectFailed));
                    }
                } catch (IOException | JSONException e) {
                    loadingDialog.cancel();
                    showToast(getString(R.string.toast_connectFailed));
                    e.printStackTrace();
                    Log.e(LOG_TAG,"Check update failed");
                }
            }
        });
        updateThread.start();
    }

    private void deleteCaches(){
        final String path = mContext.getExternalCacheDir().toString();
        File cacheDir = new File(path);
        if(cacheDir.isDirectory()){
            String[] children = cacheDir.list();
            if(children.length==0){
                showToast(getString(R.string.toast_noCahcesExist));
                return;
            }
            for (String aChildren : children) {
                new File(cacheDir, aChildren).delete();
            }
        }
        showToast(getString(R.string.toast_deleteCachesSucceed));
    }

    private double cacheSize(){
        final String path = mContext.getExternalCacheDir().toString();
        File cacheDir = new File(path);
        double result;
        long sByte = 0;
        if(cacheDir.exists()){
            File[] fileList = cacheDir.listFiles();
            for (File aFileList : fileList) {
                sByte += aFileList.length();
            }
        }
        result = sByte/1024d/1024d;
        return new BigDecimal(result).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static String getStringFromInputStream(InputStream is) throws IOException {
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
}