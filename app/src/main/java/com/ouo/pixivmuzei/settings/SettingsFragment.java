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
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import android.widget.ScrollView;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.apps.muzei.api.MuzeiArtSource;
import com.ouo.pixivmuzei.DownloadUpdateService;
import com.ouo.pixivmuzei.PAPIExceptions.GetDataFailedException;
import com.ouo.pixivmuzei.PixivLoginManager;
import com.ouo.pixivmuzei.PixivSource;
import com.ouo.pixivmuzei.PixivPublicAPI;
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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private Activity activity;
    private BroadcastReceiver completeReceiver;
    public static boolean isLoadAmountChanged = false;
    public static boolean isSourceModeChanged = false;
    private boolean isBoundToService = false;

    public PreferenceHandler mPreferenceHandler = null;
    public PixivLoginManager mPixivLoginManager = null;

    private PixivPublicAPI PPAPI;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");

        addPreferencesFromResource(R.xml.preferences);
        PPAPI = new PixivPublicAPI(activity);
        pref_changeInterval = (PreferenceScreen) findPreference("pref_changeInterval");
        mHandler = new Handler();
        setLocale();

        if(PixivSource.mContext == null){
            Log.d(LOG_TAG, "Bind PixivSource service");
            Intent intent = new Intent(activity, PixivSource.class);
            activity.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
        else{
            isBoundToService = false;
            mPreferenceHandler = new PreferenceHandler(PixivSource.mContext);
            mPixivLoginManager = new PixivLoginManager(PixivSource.mContext);
            updateUI();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");
        if(isBoundToService)
            activity.unbindService(mServiceConnection);
    }

    @Override
    public View onCreateView(LayoutInflater inflater2, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView");
        inflater = activity.getLayoutInflater();
        View view = super.onCreateView(inflater2, container, savedInstanceState);
        CreateLoadingDialog();

        return view;
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(LOG_TAG, "onServiceConnected");
            isBoundToService = true;

            mPreferenceHandler = new PreferenceHandler(PixivSource.mContext);
            mPixivLoginManager = new PixivLoginManager(PixivSource.mContext);
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(LOG_TAG, "onServiceDisconnected");
            isBoundToService = false;
        }
    };

    private void setLocale(){
        Locale locale = getLocaleFromPref();
        Resources res = getResources();
        Configuration conf = res.getConfiguration();
        conf.locale = locale;
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(conf, dm);
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

                if (PixivSource.isLogin() == 1) {
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
                ChangeLog changeLog = new ChangeLog(activity);
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




    public void updateUI(){
        PreferenceScreen logIO = (PreferenceScreen)findPreference("logIO");
        PreferenceScreen pref_license = (PreferenceScreen)findPreference("pref_license");
        if(PixivSource.isLogin() == 1)
            logIO.setTitle(getString(R.string.labelLogout));
        else
            logIO.setTitle(getString(R.string.labelLogin));
        int changeInterval = mPreferenceHandler.getConfChangeInterval();
        String s = String.valueOf(changeInterval/1440)+getString(R.string.txtDays)+" "+String.valueOf((changeInterval%1440)/60)+getString(R.string.txtHours)+" "+String.valueOf(changeInterval%1440%60)+getString(R.string.txtMinutes);
        pref_changeInterval.setSummary(s);

        s = mPreferenceHandler.getConfSourceMode();
        if(s.matches("^.*r18.*$"))
            getPreferenceScreen().findPreference("pref_noR18").setEnabled(false);
        else
            getPreferenceScreen().findPreference("pref_noR18").setEnabled(true);

        //Get application name
        ApplicationInfo applicationInfo = activity.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        String appName = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : activity.getString(stringId);

        //Get version name
        String versionName = null;
        try {
            versionName= activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
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

                JSONObject loginResponse = null;
                JSONObject user = null;
                String accessToken = null;
                String refreshToken = null;
                String expires = getExpires(1);
                try {
                    loginResponse = PPAPI.login(username, password);
                    accessToken = loginResponse.getString("access_token");
                    refreshToken = loginResponse.getString("refresh_token");
                    user = loginResponse.getJSONObject("user");
                } catch (GetDataFailedException | JSONException e) {
                    Log.e(LOG_TAG, e.getMessage());
                    e.printStackTrace();
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

                mPixivLoginManager.setAccountInfo(username, password);
                mPixivLoginManager.setLastAccountInfo(username, password);
                mPixivLoginManager.setLoginInfo(accessToken, refreshToken, expires, user.toString());

                Log.i(LOG_TAG,"Login succeeded");
                Log.d(LOG_TAG, "Preferences wrote:"+
                        "\nusername: " + username+
                        "\npassword: " + password+
                        "\nexpires: " + expires+
                        "\naccessToken: " + mPixivLoginManager.getAccessToken()+
                        "\nrefreshToken: " + mPixivLoginManager.getRefreshToken()+
                        "\njo_user: " + mPixivLoginManager.getJo_user()
                );

                if (activity != null && !activity.isDestroyed()) {
                    loadingDialog.cancel();
                    showToast(getString(R.string.toast_loginSuccess));
                    ((SettingsActivity) activity).restartFragment();
                }
            }
        });
        loginThread.start();
        mPreferenceHandler.setIsSourceUpToDate(false);
        //loginThread.join();
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
        ((SettingsActivity) activity).restartFragment();
    }

    private static String getExpires(int addHoour){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date dt=new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(dt);
        c.add(Calendar.HOUR, addHoour);
        Date d = c.getTime();
        return sdf.format(d);
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
                        if (PixivSource.isLogin() < 1) {
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
                            r = activity.getResources().getInteger(R.integer.pref_changeInterval_default);
                        }
                        mPreferenceHandler.setConfChangeInterval(r);
                        updateUI();
                    }
                });
                timePickerDialog = timePickerBuilder.create();
                break;
            case "deleteCaches":
                AlertDialog.Builder deleteCachesDialogBuilder = new AlertDialog.Builder(activity);
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
                AlertDialog.Builder licenseDialogBuilder = new AlertDialog.Builder(activity);
                final View licenseView = inflater.inflate(R.layout.license, null);
                final LinearLayout licenseLayout = (LinearLayout) licenseView.findViewById(R.id.licenseView);

                TextView txtTitle = new TextView(activity);
                txtTitle.setText(getString(R.string.pref_license_title));
                txtTitle.setTextSize(30);
                txtTitle.setBackgroundColor(Color.WHITE);
                txtTitle.setTextColor(Color.BLACK);
                TextView txtLicense = new TextView(activity);
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
                AlertDialog.Builder openSourceLicensesDialogBuilder = new AlertDialog.Builder(activity);
                final View licensesView = inflater.inflate(R.layout.license, null);
                final LinearLayout licensesLayout = (LinearLayout) licensesView.findViewById(R.id.licenseView);

                List<String> licenses = Arrays.asList(getResources().getStringArray(R.array.licenses));
                List<String> licenses_filename = Arrays.asList(getResources().getStringArray(R.array.licenses_filename));
                for (int i = 0; i < licenses.size(); i++) {
                    TextView txtTitle = new TextView(activity);
                    txtTitle.setText(licenses.get(i));
                    txtTitle.setTextSize(30);
                    txtTitle.setBackgroundColor(Color.WHITE);
                    txtTitle.setTextColor(Color.BLACK);
                    TextView txtLicense = new TextView(activity);
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
                Intent downloadUpdateService = new Intent(activity, DownloadUpdateService.class);
                activity.startService(downloadUpdateService);
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
        AlertDialog.Builder loadingDialogBuilder = new AlertDialog.Builder(activity);
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
                    ((SettingsActivity) activity).restartFragment();
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
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
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
                    pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
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
        final String path = activity.getExternalCacheDir().toString();
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
        final String path = activity.getExternalCacheDir().toString();
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

    @Override
    public void onResume() {
        PreferenceManager.getDefaultSharedPreferences(activity).registerOnSharedPreferenceChangeListener(mListener);
        super.onResume();
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(activity).unregisterOnSharedPreferenceChangeListener(mListener);
        super.onPause();
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