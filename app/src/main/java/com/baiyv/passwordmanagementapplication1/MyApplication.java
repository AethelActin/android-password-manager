package com.baiyv.passwordmanagementapplication1;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.baiyv.passwordmanagementapplication1.autofill.AutofillAuthActivity;
import com.baiyv.passwordmanagementapplication1.autofill.AutofillSessionStore;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.ui.LoginActivity;
import com.baiyv.passwordmanagementapplication1.ui.RegisterActivity;
import com.baiyv.passwordmanagementapplication1.ui.SplashActivity;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;

public class MyApplication extends Application {

    private static MyApplication instance;
    private AppDatabase database;
    private String masterPassword;
    private long unlockedAt;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        new SharedPrefsManager(this).setTimedEmailBackupEnabled(false);
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("timed_email_backup");
        applyAppearancePreferences();
        database = AppDatabase.getInstance(this);
        registerAutoLockCallbacks();
        androidx.core.content.ContextCompat.registerReceiver(this,
                new android.content.BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) clearMasterPassword();
                    }
                }, new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public static MyApplication getInstance() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public synchronized String getMasterPassword() {
        int minutes=new SharedPrefsManager(this).getAutoLockTime();
        if(masterPassword!=null && minutes>0 && android.os.SystemClock.elapsedRealtime()-unlockedAt>minutes*60000L) clearMasterPassword();
        return masterPassword;
    }

    public synchronized void setMasterPassword(String masterPassword) {
        this.masterPassword = masterPassword;
        unlockedAt=android.os.SystemClock.elapsedRealtime();
    }

    public synchronized void clearMasterPassword() {
        this.masterPassword = null;
        new SharedPrefsManager(this).setLastPasswordViewAuthTime(0);
    }

    public static int resolveNoActionBarTheme(Context context) {
        String color = new SharedPrefsManager(context).getThemeColor();
        if ("green".equals(color)) return R.style.Theme_PasswordManager_NoActionBar_Green;
        if ("purple".equals(color)) return R.style.Theme_PasswordManager_NoActionBar_Purple;
        if ("red".equals(color)) return R.style.Theme_PasswordManager_NoActionBar_Red;
        return R.style.Theme_PasswordManager_NoActionBar;
    }

    public static int resolveSplashTheme(Context context) {
        String color = new SharedPrefsManager(context).getThemeColor();
        if ("green".equals(color)) return R.style.Theme_PasswordManager_Splash_Green;
        if ("purple".equals(color)) return R.style.Theme_PasswordManager_Splash_Purple;
        if ("red".equals(color)) return R.style.Theme_PasswordManager_Splash_Red;
        return R.style.Theme_PasswordManager_Splash;
    }

    private void applyAppearancePreferences() {
        SharedPrefsManager prefs = new SharedPrefsManager(this);
        AppCompatDelegate.setDefaultNightMode(prefs.getNightMode());

        String tag = prefs.getLanguageTag();
        if (tag == null || tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
    }

    private void registerAutoLockCallbacks() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                applySecureFlag(activity);
                if (isAuthActivity(activity)) return;

                SharedPrefsManager prefs = new SharedPrefsManager(activity);
                int minutes = prefs.getAutoLockTime();
                long last = prefs.getLastActiveTime();
                long now = System.currentTimeMillis();

                if (minutes > 0 && last > 0 && now - last > minutes * 60_000L) {
                    clearMasterPassword();
                    new AutofillSessionStore(activity).clear();
                }

                if (getMasterPassword() == null || getMasterPassword().isEmpty()) {
                    String session = new AutofillSessionStore(activity).getIfNotExpired();
                    if (session != null && !session.isEmpty()) {
                        setMasterPassword(session);
                        return;
                    }

                    Intent intent;
                    if (prefs.isFirstRun()) {
                        intent = new Intent(activity, RegisterActivity.class);
                    } else {
                        intent = new Intent(activity, LoginActivity.class);
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                String mp = getMasterPassword();
                if (mp != null && !mp.isEmpty()) {
                    new SharedPrefsManager(activity).setLastActiveTime(System.currentTimeMillis());
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private void applySecureFlag(Activity activity) {
        SharedPrefsManager prefs = new SharedPrefsManager(activity);
        if (prefs.isSecureScreenEnabled()) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private boolean isAuthActivity(Activity activity) {
        return activity instanceof SplashActivity
                || activity instanceof LoginActivity
                || activity instanceof RegisterActivity
                || activity instanceof AutofillAuthActivity;
    }
}