package com.baiyv.passwordmanagementapplication1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SharedPrefsManager {

    private static final String TAG = "SharedPrefsManager";
    private static final String PREFS_NAME = "password_manager_prefs";
    private static final String KEY_MASTER_PASSWORD_HASH = "master_password_hash";
    private static final String KEY_IS_FIRST_RUN = "is_first_run";
    private static final String KEY_USE_BIOMETRIC = "use_biometric";
    private static final String KEY_AUTO_LOCK_TIME = "auto_lock_time";
    private static final String KEY_LAST_ACTIVE_TIME = "last_active_time";
    private static final String KEY_PASSWORD_HINT = "password_hint";

    private static final String KEY_TOTP_ENABLED = "totp_enabled";
    private static final String KEY_TOTP_SECRET_ENC = "totp_secret_enc";
    private static final String KEY_TOTP_LAST_COUNTER = "totp_last_counter";

    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_LANGUAGE_TAG = "language_tag";
    private static final String KEY_THEME_COLOR = "theme_color";

    private static final String KEY_PASSWORD_STYLE = "password_style";

    private static final String KEY_BACKUP_EMAIL = "backup_email";

    private static final String KEY_TIMED_EMAIL_BACKUP_ENABLED = "timed_email_backup_enabled";
    private static final String KEY_TIMED_EMAIL_BACKUP_INTERVAL_DAYS = "timed_email_backup_interval_days";

    private static final String KEY_SECURE_SCREEN = "secure_screen";

    private static final String KEY_PASSWORD_VIEW_AUTH_ENABLED = "password_view_auth_enabled";

    private static final String KEY_LAST_PASSWORD_VIEW_AUTH_TIME = "last_password_view_auth_time";
    private static final long PASSWORD_VIEW_AUTH_TIMEOUT_MS = 3L * 60L * 1000L;

    private final SharedPreferences prefs;
    private final Context context;

    public SharedPrefsManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // 主密码哈希 - 使用 commit() 确保同步保存
    public void setMasterPasswordHash(String hash) {
        prefs.edit().putString(KEY_MASTER_PASSWORD_HASH, hash).commit();
    }

    public void clearLegacyCredentials() { prefs.edit().remove(KEY_MASTER_PASSWORD_HASH).remove(KEY_TOTP_SECRET_ENC).commit(); }

    public String getMasterPasswordHash() {
        return prefs.getString(KEY_MASTER_PASSWORD_HASH, null);
    }

    // 是否首次运行 - 使用 commit() 确保同步保存
    public boolean isFirstRun() {
        boolean exists=VaultThread.run(() -> com.baiyv.passwordmanagementapplication1.data.db.AppDatabase.getInstance(context).vaultDao().get()!=null);
        return !exists && prefs.getBoolean(KEY_IS_FIRST_RUN, true);
    }

    public void setFirstRun(boolean isFirst) {
        prefs.edit().putBoolean(KEY_IS_FIRST_RUN, isFirst).commit();
    }

    // 是否使用生物识别
    public void setUseBiometric(boolean use) {
        prefs.edit().putBoolean(KEY_USE_BIOMETRIC, use).commit();
    }

    public boolean useBiometric() {
        return prefs.getBoolean(KEY_USE_BIOMETRIC, false);
    }

    // 自动锁定时间（分钟）
    public void setAutoLockTime(int minutes) {
        prefs.edit().putInt(KEY_AUTO_LOCK_TIME, minutes).commit();
    }

    public int getAutoLockTime() {
        return prefs.getInt(KEY_AUTO_LOCK_TIME, 1);
    }

    // 最后活动时间
    public void setLastActiveTime(long time) {
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, time).apply();
    }

    public long getLastActiveTime() {
        return prefs.getLong(KEY_LAST_ACTIVE_TIME, 0);
    }

    // 密码提示
    public void setPasswordHint(String hint) {
        prefs.edit().putString(KEY_PASSWORD_HINT, hint).commit();
    }

    public String getPasswordHint() {
        return prefs.getString(KEY_PASSWORD_HINT, null);
    }

    public void setTotpEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TOTP_ENABLED, enabled).commit();
    }

    public boolean isTotpEnabled() {
        return prefs.getBoolean(KEY_TOTP_ENABLED, false);
    }

    public void setTotpSecretEncrypted(String encrypted) {
        com.baiyv.passwordmanagementapplication1.data.db.AppDatabase db=com.baiyv.passwordmanagementapplication1.data.db.AppDatabase.getInstance(context);
        VaultThread.run(() -> { db.runInTransaction(() -> {
            com.baiyv.passwordmanagementapplication1.data.model.VaultMetadata m=db.vaultDao().get();
            if(m!=null) { m.totpSecret=encrypted; db.vaultDao().put(m); }
            else prefs.edit().putString(KEY_TOTP_SECRET_ENC,encrypted).commit();
        }); return null; });
    }

    public String getTotpSecretEncrypted() {
        com.baiyv.passwordmanagementapplication1.data.model.VaultMetadata m=VaultThread.run(() -> com.baiyv.passwordmanagementapplication1.data.db.AppDatabase.getInstance(context).vaultDao().get());
        return m!=null ? m.totpSecret : prefs.getString(KEY_TOTP_SECRET_ENC,null);
    }

    public void setTotpLastCounter(long counter) {
        prefs.edit().putLong(KEY_TOTP_LAST_COUNTER, counter).commit();
    }

    public long getTotpLastCounter() {
        return prefs.getLong(KEY_TOTP_LAST_COUNTER, -1L);
    }

    public void setNightMode(int mode) {
        prefs.edit().putInt(KEY_NIGHT_MODE, mode).commit();
    }

    public int getNightMode() {
        return prefs.getInt(KEY_NIGHT_MODE, -1);
    }

    public void setLanguageTag(String tag) {
        prefs.edit().putString(KEY_LANGUAGE_TAG, tag).commit();
    }

    public String getLanguageTag() {
        return prefs.getString(KEY_LANGUAGE_TAG, "");
    }

    public void setThemeColor(String color) {
        prefs.edit().putString(KEY_THEME_COLOR, color).commit();
    }

    public String getThemeColor() {
        return prefs.getString(KEY_THEME_COLOR, "blue");
    }

    public void setPasswordStyle(int style) {
        prefs.edit().putInt(KEY_PASSWORD_STYLE, style).commit();
    }

    public int getPasswordStyle() {
        return prefs.getInt(KEY_PASSWORD_STYLE, 0);
    }

    public void setBackupEmail(String email) {
        prefs.edit().putString(KEY_BACKUP_EMAIL, email).commit();
    }

    public String getBackupEmail() {
        return prefs.getString(KEY_BACKUP_EMAIL, "");
    }

    public void setTimedEmailBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TIMED_EMAIL_BACKUP_ENABLED, enabled).commit();
    }

    public boolean isTimedEmailBackupEnabled() {
        return prefs.getBoolean(KEY_TIMED_EMAIL_BACKUP_ENABLED, false);
    }

    public void setTimedEmailBackupIntervalDays(int days) {
        prefs.edit().putInt(KEY_TIMED_EMAIL_BACKUP_INTERVAL_DAYS, days).commit();
    }

    public int getTimedEmailBackupIntervalDays() {
        return prefs.getInt(KEY_TIMED_EMAIL_BACKUP_INTERVAL_DAYS, 7);
    }

    public void setSecureScreenEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SECURE_SCREEN, enabled).commit();
    }

    public boolean isSecureScreenEnabled() {
        return prefs.getBoolean(KEY_SECURE_SCREEN, false);
    }

    public void setPasswordViewAuthEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PASSWORD_VIEW_AUTH_ENABLED, enabled).commit();
    }

    public boolean isPasswordViewAuthEnabled() {
        return prefs.getBoolean(KEY_PASSWORD_VIEW_AUTH_ENABLED, true);
    }

    public void setLastPasswordViewAuthTime(long time) {
        prefs.edit().putLong(KEY_LAST_PASSWORD_VIEW_AUTH_TIME, time).commit();
    }

    public long getLastPasswordViewAuthTime() {
        return prefs.getLong(KEY_LAST_PASSWORD_VIEW_AUTH_TIME, 0L);
    }

    public boolean isPasswordViewAuthValid() {
        long t = getLastPasswordViewAuthTime();
        return t > 0L && (System.currentTimeMillis() - t) <= PASSWORD_VIEW_AUTH_TIMEOUT_MS;
    }

    // 清除所有数据
    public void clearAll() {
        prefs.edit().clear().commit();
        Log.d(TAG, "clearAll: done");
    }
}