package com.baiyv.passwordmanagementapplication1.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.autofill.AutofillSessionStore;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.data.repository.PasswordRepository;
import com.baiyv.passwordmanagementapplication1.network.ApiService;
import com.baiyv.passwordmanagementapplication1.network.RetrofitClient;
import com.baiyv.passwordmanagementapplication1.utils.BackupHelper;
import com.baiyv.passwordmanagementapplication1.utils.Constants;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;
import com.baiyv.passwordmanagementapplication1.utils.TotpUtils;
import com.baiyv.passwordmanagementapplication1.utils.VaultManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchBiometric;
    private SwitchMaterial switchTwoFactor;
    private SwitchMaterial switchSecureScreen;
    private SwitchMaterial switchPasswordViewAuth;

    private boolean changingBiometric;
    private BiometricPrompt biometricTogglePrompt;
    private boolean biometricToggleInProgress;
    private boolean changingTwoFactor;
    private SharedPrefsManager prefsManager;
    private PasswordRepository passwordRepository;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private String masterPassword;
    private android.widget.TextView tvThemeModeValue;
    private android.widget.TextView tvThemeColorValue;
    private android.widget.TextView tvLanguageValue;
    private android.widget.TextView tvPasswordStyleValue;
    private android.widget.TextView tvBackupEmailValue;
    private android.widget.TextView tvTimedEmailBackupValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(MyApplication.resolveNoActionBarTheme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        prefsManager = new SharedPrefsManager(this);
        passwordRepository = new PasswordRepository(this);

        masterPassword = MyApplication.getInstance().getMasterPassword();
        if (masterPassword == null) {
            masterPassword = MyApplication.getInstance().getMasterPassword();
        }
        if (masterPassword == null) {
            masterPassword = new AutofillSessionStore(this).getIfNotExpired();
        }

        prefsManager.setTimedEmailBackupEnabled(false);
        cancelTimedEmailBackup();
        initLaunchers();
        initViews();
        loadSettings();
        for (int id : new int[]{R.id.layoutEmailBackup, R.id.layoutSelectiveEmailBackup, R.id.layoutTimedEmailBackup}) {
            View row = findViewById(id);
            if (row != null) row.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (biometricTogglePrompt != null && biometricToggleInProgress) {
            biometricTogglePrompt.cancelAuthentication();
        }
    }

    private void initLaunchers() {
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        exportData(uri);
                    }
                });

        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        importData(uri);
                    }
                });
    }

    private void initViews() {
        switchBiometric = findViewById(R.id.switchBiometric);
        switchTwoFactor = findViewById(R.id.switchTwoFactor);
        switchSecureScreen = findViewById(R.id.switchSecureScreen);
        switchPasswordViewAuth = findViewById(R.id.switchPasswordViewAuth);

        if (switchSecureScreen != null) {
            switchSecureScreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefsManager.setSecureScreenEnabled(isChecked);
                if (isChecked) {
                    getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                } else {
                    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                }
            });
        }

        // 生物识别开关（开启/关闭都需要先验证一次指纹）
        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (changingBiometric) return;

            boolean desired = isChecked;
            boolean previous = !desired;

            setBiometricSwitchChecked(previous);
            startBiometricToggleAuth(desired);
        });

        if (switchTwoFactor != null) {
            switchTwoFactor.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (changingTwoFactor) return;
                if (isChecked) {
                    startEnableTwoFactor();
                } else {
                    startDisableTwoFactor();
                }
            });
        }

        if (switchPasswordViewAuth != null) {
            switchPasswordViewAuth.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefsManager.setPasswordViewAuthEnabled(isChecked);
            });
        }

        View layoutThemeMode = findViewById(R.id.layoutThemeMode);
        View layoutThemeColor = findViewById(R.id.layoutThemeColor);
        View layoutLanguage = findViewById(R.id.layoutLanguage);
        View layoutPasswordStyle = findViewById(R.id.layoutPasswordStyle);
        tvThemeModeValue = findViewById(R.id.tvThemeModeValue);
        tvThemeColorValue = findViewById(R.id.tvThemeColorValue);
        tvLanguageValue = findViewById(R.id.tvLanguageValue);
        tvPasswordStyleValue = findViewById(R.id.tvPasswordStyleValue);
        tvBackupEmailValue = findViewById(R.id.tvBackupEmailValue);
        tvTimedEmailBackupValue = findViewById(R.id.tvTimedEmailBackupValue);

        if (layoutThemeMode != null) {
            layoutThemeMode.setOnClickListener(v -> showThemeModeDialog());
        }
        if (layoutThemeColor != null) {
            layoutThemeColor.setOnClickListener(v -> showThemeColorDialog());
        }
        if (layoutLanguage != null) {
            layoutLanguage.setOnClickListener(v -> showLanguageDialog());
        }
        if (layoutPasswordStyle != null) {
            layoutPasswordStyle.setOnClickListener(v -> showPasswordStyleDialog());
        }

        // 修改主密码
        findViewById(R.id.layoutChangePassword).setOnClickListener(v -> showChangePasswordDialog());

        // Source rules are retained and tested; the service stays off pending system-flow validation.
        findViewById(R.id.layoutAutofill).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("自动填充暂未开放").setMessage("来源匹配已收紧，系统填充流程仍待设备验证；当前版本请手动复制密码。")
                .setPositiveButton(R.string.confirm, null).show());

        // 自动锁定时间
        findViewById(R.id.layoutAutoLock).setOnClickListener(v -> showAutoLockDialog());

        // 导出数据
        findViewById(R.id.layoutExport).setOnClickListener(v -> {
            createDocumentLauncher.launch("password_manager_backup.json");
        });

        View layoutEmailBackup = findViewById(R.id.layoutEmailBackup);
        if (layoutEmailBackup != null) {
            layoutEmailBackup.setOnClickListener(v -> showEmailBackupDialog());
        }

        View layoutSelectiveEmailBackup = findViewById(R.id.layoutSelectiveEmailBackup);
        if (layoutSelectiveEmailBackup != null) {
            layoutSelectiveEmailBackup.setOnClickListener(v -> showSelectiveEmailBackupDialog());
        }

        View layoutTimedEmailBackup = findViewById(R.id.layoutTimedEmailBackup);
        if (layoutTimedEmailBackup != null) {
            layoutTimedEmailBackup.setOnClickListener(v -> showTimedEmailBackupDialog());
        }

        // 导入数据
        findViewById(R.id.layoutImport).setOnClickListener(v -> {
            openDocumentLauncher.launch(new String[]{"application/json", "application/octet-stream"});
        });


        // 清除所有数据
        findViewById(R.id.layoutClearData).setOnClickListener(v -> authorizeSensitive(this::showClearDataDialog));

        // 关于
        findViewById(R.id.layoutAbout).setOnClickListener(v -> showAboutDialog());
    }

    private void setBiometricSwitchChecked(boolean checked) {
        changingBiometric = true;
        switchBiometric.setChecked(checked);
        changingBiometric = false;
    }

    private void startBiometricToggleAuth(boolean enable) {
        if (biometricToggleInProgress) return;

        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, biometricSupportMessage(canAuthenticate), Toast.LENGTH_SHORT).show();
            return;
        }

        biometricToggleInProgress = true;

        Executor executor = ContextCompat.getMainExecutor(this);
        biometricTogglePrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        biometricToggleInProgress = false;
                        prefsManager.setUseBiometric(enable);
                        setBiometricSwitchChecked(enable);
                        Toast.makeText(SettingsActivity.this,
                                getString(enable ? R.string.toast_biometric_enabled : R.string.toast_biometric_disabled),
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        biometricToggleInProgress = false;

                        if (errorCode == BiometricPrompt.ERROR_CANCELED
                                || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_TIMEOUT
                                || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            return;
                        }

                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.biometric_auth_failed, String.valueOf(errString)),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        biometricTogglePrompt.authenticate(promptInfo);
    }

    private String biometricSupportMessage(int canAuthenticate) {
        switch (canAuthenticate) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return getString(R.string.biometric_no_hardware);
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return getString(R.string.biometric_hw_unavailable);
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return getString(R.string.biometric_not_enrolled);
            default:
                return getString(R.string.biometric_enable_failed);
        }
    }

    private void exportData(Uri uri) {
        authorizeSensitive(() -> exportUnlockedData(uri));
    }

    private boolean requireSession() {
        masterPassword = MyApplication.getInstance().getMasterPassword();
        if (masterPassword != null) return true;
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish(); return false;
    }

    private void authorizeSensitive(Runnable action) {
        if (!requireSession()) return;
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("当前主密码");
        AlertDialog auth = new AlertDialog.Builder(this).setTitle("验证后继续")
                .setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, null).create();
        auth.setOnShowListener(d -> auth.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pass = input.getText() == null ? "" : input.getText().toString();
            auth.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(() -> {
                boolean valid = new VaultManager(this).verifyPassword(pass);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !auth.isShowing()) return;
                    auth.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (!valid) { input.setError("主密码错误"); return; }
                    auth.dismiss(); if (requireSession()) action.run();
                });
            }).start();
        }));
        auth.show();
    }

    private void exportUnlockedData(Uri uri) {
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("独立备份口令（至少 8 位，请妥善保存）");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("加密导出")
                .setView(input).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pass = input.getText() == null ? "" : input.getText().toString();
            if (pass.length() < 8) { input.setError("备份口令至少 8 位"); return; }
            String token = MyApplication.getInstance().getMasterPassword();
            if (token == null) { input.setError("请重新解锁"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            passwordRepository.getAllPasswords(new PasswordRepository.DataCallback<List<PasswordEntry>>() {
                public void onSuccess(List<PasswordEntry> data) {
                    try {
                        BackupHelper.exportPasswords(SettingsActivity.this, uri, data, token, pass);
                        runOnUiThread(() -> { dialog.dismiss(); Toast.makeText(SettingsActivity.this, R.string.toast_export_success, Toast.LENGTH_LONG).show(); });
                    } catch (Exception e) { onError(e); }
                }
                public void onError(Exception e) {
                    runOnUiThread(() -> { input.setError("导出失败，未删除任何记录"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); });
                }
            });
        }));
        dialog.show();
    }

    private void importData(Uri uri) {
        showImportPasswordDialog(uri);
    }

    private void saveImportedPasswords(List<PasswordEntry> passwords, String expectedToken) {
        if (expectedToken == null || !expectedToken.equals(MyApplication.getInstance().getMasterPassword())) {
            Toast.makeText(this, "解锁已过期，未导入任何记录", Toast.LENGTH_LONG).show(); return;
        }
        if (passwords != null && !passwords.isEmpty()) {
            passwordRepository.insertAllForSession(passwords, expectedToken, new PasswordRepository.DataCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_import_success_count, passwords.size()), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_save_data_failed, mapErrorMessage(String.valueOf(e.getMessage()))), Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_file_no_data), Toast.LENGTH_SHORT).show());
        }
    }

    private String mapErrorMessage(String msg) {
        if (BackupHelper.ERR_INVALID_BACKUP.equals(msg)) return "备份格式或内容无效";
        if (BackupHelper.ERR_UNSUPPORTED_VERSION.equals(msg)) return "不支持此备份版本";
        if (BackupHelper.ERR_BACKUP_TOO_LARGE.equals(msg)) return "备份超过 10 MiB 或 10000 条记录限制";
        if (BackupHelper.ERR_IMPORT_NEEDS_MASTER_PASSWORD.equals(msg)) return "解锁已过期，请重新登录并输入备份口令";
        if (msg == null) return "";
        if (BackupHelper.ERR_MASTER_PASSWORD_EMPTY.equals(msg)) {
            return getString(R.string.error_enter_master_password);
        }
        if (BackupHelper.ERR_ENCRYPT_FAILED.equals(msg)) {
            return getString(R.string.encrypt_failed);
        }
        if (BackupHelper.ERR_OPEN_OUTPUT_STREAM_FAILED.equals(msg)) {
            return getString(R.string.output_stream_open_failed);
        }
        if (BackupHelper.ERR_IMPORT_MASTER_PASSWORD_INCORRECT_OR_CORRUPT.equals(msg)) {
            return getString(R.string.error_backup_master_password_incorrect_or_corrupt);
        }
        return msg;
    }

    private void showEmailBackupDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextInputLayout tilEmail = new TextInputLayout(this);
        applyDialogFieldStyle(tilEmail);
        tilEmail.setHint(getString(R.string.backup_email));

        TextInputEditText etEmail = new TextInputEditText(this);
        etEmail.setBackground(null);
        applyDialogEditStyle(etEmail);
        etEmail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        String current = prefsManager.getBackupEmail();
        if (current != null && !current.isEmpty()) {
            etEmail.setText(current);
        }

        tilEmail.addView(etEmail);
        container.addView(tilEmail);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.email_backup))
                .setView(container)
                .setPositiveButton(getString(R.string.email_backup_send), null)
                .setNeutralButton(getString(R.string.email_backup_save), null)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                tilEmail.setError(null);
                if (email.isEmpty()) {
                    tilEmail.setError(getString(R.string.toast_enter_backup_email));
                    return;
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilEmail.setError(getString(R.string.toast_invalid_email));
                    return;
                }

                prefsManager.setBackupEmail(email);
                updateBackupEmailSummary();
                ensureTimedEmailBackupScheduled();
                Toast.makeText(this, getString(R.string.toast_backup_email_saved), Toast.LENGTH_SHORT).show();
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                tilEmail.setError(null);
                if (email.isEmpty()) {
                    tilEmail.setError(getString(R.string.toast_enter_backup_email));
                    return;
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilEmail.setError(getString(R.string.toast_invalid_email));
                    return;
                }
                if (masterPassword == null || masterPassword.isEmpty()) {
                    Toast.makeText(this, getString(R.string.toast_not_unlocked_cannot_export), Toast.LENGTH_SHORT).show();
                    return;
                }

                prefsManager.setBackupEmail(email);
                updateBackupEmailSummary();
                ensureTimedEmailBackupScheduled();

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);

                passwordRepository.getAllPasswords(new PasswordRepository.DataCallback<List<PasswordEntry>>() {
                    @Override
                    public void onSuccess(List<PasswordEntry> data) {
                        try {
                            String backupText = BackupHelper.exportPasswordsToString(data, masterPassword);
                            ApiService api = RetrofitClient.getInstance().getApiService();
                            api.sendBackupEmail(new ApiService.BackupEmailRequest(email, backupText))
                                    .enqueue(new Callback<ApiService.ApiResponse<Void>>() {
                                        @Override
                                        public void onResponse(Call<ApiService.ApiResponse<Void>> call, Response<ApiService.ApiResponse<Void>> response) {
                                            runOnUiThread(() -> {
                                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                                                ApiService.ApiResponse<Void> body = response.body();
                                                if (response.isSuccessful() && body != null && body.isSuccess()) {
                                                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_sent), Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                    return;
                                                }

                                                String msg;
                                                if (body != null && body.getMessage() != null) {
                                                    msg = body.getMessage();
                                                } else {
                                                    msg = "HTTP " + response.code();
                                                }
                                                Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_failed, msg), Toast.LENGTH_LONG).show();
                                            });
                                        }

                                        @Override
                                        public void onFailure(Call<ApiService.ApiResponse<Void>> call, Throwable t) {
                                            runOnUiThread(() -> {
                                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                                                String msg = t != null && t.getMessage() != null ? t.getMessage() : "";
                                                Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_failed, msg), Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                                String msg = e.getMessage() != null ? e.getMessage() : "";
                                Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_failed, mapErrorMessage(msg)), Toast.LENGTH_LONG).show();
                            });
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                            Toast.makeText(SettingsActivity.this, getString(R.string.toast_get_data_failed, mapErrorMessage(String.valueOf(e.getMessage()))), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    private void showSelectiveEmailBackupDialog() {
        String email = prefsManager.getBackupEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_set_backup_email_first), Toast.LENGTH_SHORT).show();
            showEmailBackupDialog();
            return;
        }
        if (masterPassword == null || masterPassword.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_not_unlocked_cannot_export), Toast.LENGTH_SHORT).show();
            return;
        }

        passwordRepository.getAllPasswords(new PasswordRepository.DataCallback<List<PasswordEntry>>() {
            @Override
            public void onSuccess(List<PasswordEntry> data) {
                runOnUiThread(() -> {
                    if (data == null || data.isEmpty()) {
                        Toast.makeText(SettingsActivity.this, getString(R.string.toast_no_passwords_to_backup), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CharSequence[] items = new CharSequence[data.size()];
                    boolean[] checked = new boolean[data.size()];

                    for (int i = 0; i < data.size(); i++) {
                        PasswordEntry entry = data.get(i);
                        String title = entry != null && entry.getTitle() != null ? entry.getTitle() : "";
                        String username = entry != null && entry.getUsername() != null ? entry.getUsername() : "";
                        if (!username.isEmpty()) {
                            items[i] = title + " (" + username + ")";
                        } else {
                            items[i] = title;
                        }
                    }

                    AlertDialog dialog = new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle(getString(R.string.share_password))
                            .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                            .setPositiveButton(getString(R.string.email_backup_send), null)
                            .setNeutralButton(getString(R.string.select_all), null)
                            .setNegativeButton(getString(R.string.cancel), null)
                            .create();

                    dialog.setOnShowListener(d -> {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                            boolean shouldSelectAll = false;
                            for (boolean b : checked) {
                                if (!b) {
                                    shouldSelectAll = true;
                                    break;
                                }
                            }

                            android.widget.ListView lv = dialog.getListView();
                            for (int i = 0; i < checked.length; i++) {
                                checked[i] = shouldSelectAll;
                                if (lv != null) {
                                    lv.setItemChecked(i, shouldSelectAll);
                                }
                            }
                        });

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                            ArrayList<PasswordEntry> selected = new ArrayList<>();
                            for (int i = 0; i < checked.length; i++) {
                                if (checked[i]) {
                                    selected.add(data.get(i));
                                }
                            }

                            if (selected.isEmpty()) {
                                Toast.makeText(SettingsActivity.this, getString(R.string.toast_select_at_least_one), Toast.LENGTH_SHORT).show();
                                return;
                            }

                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                            Toast.makeText(SettingsActivity.this, getString(R.string.toast_sending_backup), Toast.LENGTH_SHORT).show();

                            try {
                                String backupText = BackupHelper.exportPasswordsToString(selected, masterPassword);
                                ApiService api = RetrofitClient.getInstance().getApiService();
                                api.sendBackupEmail(new ApiService.BackupEmailRequest(email, backupText))
                                        .enqueue(new Callback<ApiService.ApiResponse<Void>>() {
                                            @Override
                                            public void onResponse(Call<ApiService.ApiResponse<Void>> call, Response<ApiService.ApiResponse<Void>> response) {
                                                runOnUiThread(() -> {
                                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                                                    ApiService.ApiResponse<Void> body = response.body();
                                                    if (response.isSuccessful() && body != null && body.isSuccess()) {
                                                        Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_sent), Toast.LENGTH_SHORT).show();
                                                        dialog.dismiss();
                                                        return;
                                                    }

                                                    String msg;
                                                    if (body != null && body.getMessage() != null) {
                                                        msg = body.getMessage();
                                                    } else {
                                                        msg = "HTTP " + response.code();
                                                    }
                                                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_failed, msg), Toast.LENGTH_LONG).show();
                                                });
                                            }

                                            @Override
                                            public void onFailure(Call<ApiService.ApiResponse<Void>> call, Throwable t) {
                                                runOnUiThread(() -> {
                                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                                                    String msg = t != null && t.getMessage() != null ? t.getMessage() : "";
                                                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_failed, msg), Toast.LENGTH_LONG).show();
                                                });
                                            }
                                        });
                            } catch (Exception e) {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);

                                String msg = e.getMessage() != null ? e.getMessage() : "";
                                Toast.makeText(SettingsActivity.this, getString(R.string.toast_backup_email_failed, mapErrorMessage(msg)), Toast.LENGTH_LONG).show();
                            }
                        });
                    });

                    dialog.show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_get_data_failed, mapErrorMessage(String.valueOf(e.getMessage()))), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showImportPasswordDialog(Uri uri) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextInputLayout til = new TextInputLayout(this);
        applyDialogFieldStyle(til);
        til.setHint("备份口令（旧版备份使用当时的主密码）");

        TextInputEditText et = new TextInputEditText(this);
        et.setBackground(null);
        applyDialogEditStyle(et);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);

        container.addView(til);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_encrypted_backup))
                .setView(container)
                .setPositiveButton(getString(R.string.import_action), null)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = et.getText() != null ? et.getText().toString() : "";
            til.setError(null);
            if (password.isEmpty()) {
                til.setError(getString(R.string.error_enter_backup_master_password));
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final String expectedToken = MyApplication.getInstance().getMasterPassword();
            new Thread(() -> {
                try {
                    List<PasswordEntry> passwords = BackupHelper.importPasswords(SettingsActivity.this, uri, password, expectedToken);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        new AlertDialog.Builder(this).setTitle("确认导入")
                                .setMessage("将追加 " + passwords.size() + " 条记录。重复导入会产生副本，现有记录不会覆盖。")
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.import_action, (d2, w2) -> saveImportedPasswords(passwords, expectedToken)).show();
                    });
                } catch (Exception ex) {
                    String msg = ex.getMessage() != null ? ex.getMessage() : getString(R.string.import_failed);
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, mapErrorMessage(msg), Toast.LENGTH_LONG).show();
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    });
                }
            }).start();
        }));

        dialog.show();
    }

    private void clearAllData() {
        passwordRepository.deleteAll(new PasswordRepository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_all_data_cleared), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_clear_failed, String.valueOf(e.getMessage())), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openAutofillSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = getSystemService(AutofillManager.class);
            if (autofillManager != null) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.autofill_enable_title))
                        .setMessage(getString(R.string.autofill_enable_message))
                        .setPositiveButton(getString(R.string.go_settings), (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            try {
                                startActivity(intent);
                            } catch (Exception e) {
                                try {
                                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                                    Toast.makeText(this, getString(R.string.autofill_search_hint), Toast.LENGTH_LONG).show();
                                } catch (Exception ex) {
                                    Toast.makeText(this, getString(R.string.cannot_open_settings), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
                return;
            }
        }
        Toast.makeText(this, getString(R.string.autofill_requires_android8), Toast.LENGTH_SHORT).show();
    }

    private void loadSettings() {
        if (switchSecureScreen != null) {
            switchSecureScreen.setChecked(prefsManager.isSecureScreenEnabled());
        }
        if (switchPasswordViewAuth != null) {
            switchPasswordViewAuth.setChecked(prefsManager.isPasswordViewAuthEnabled());
        }
        setBiometricSwitchChecked(prefsManager.useBiometric());
        if (switchTwoFactor != null) {
            boolean enabled = prefsManager.isTotpEnabled() && prefsManager.getTotpSecretEncrypted() != null;
            setTwoFactorChecked(enabled);
        }
        updateAppearanceSummary();
        updateBackupEmailSummary();
        updateTimedEmailBackupSummary();
        ensureTimedEmailBackupScheduled();
    }

    private void updateAppearanceSummary() {
        if (tvThemeModeValue != null) {
            int mode = prefsManager.getNightMode();
            String text;
            if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
                text = getString(R.string.light_mode);
            } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
                text = getString(R.string.dark_mode);
            } else {
                text = getString(R.string.follow_system);
            }
            tvThemeModeValue.setText(text);
        }

        if (tvThemeColorValue != null) {
            String color = prefsManager.getThemeColor();
            String text;
            if ("green".equals(color)) text = getString(R.string.theme_color_green);
            else if ("purple".equals(color)) text = getString(R.string.theme_color_purple);
            else if ("red".equals(color)) text = getString(R.string.theme_color_red);
            else text = getString(R.string.theme_color_blue);
            tvThemeColorValue.setText(text);
        }

        if (tvLanguageValue != null) {
            String tag = prefsManager.getLanguageTag();
            String text;
            if (tag == null || tag.isEmpty()) {
                text = getString(R.string.follow_system);
            } else if (tag.startsWith("zh")) {
                text = getString(R.string.language_chinese);
            } else if (tag.startsWith("en")) {
                text = getString(R.string.language_english);
            } else {
                text = tag;
            }
            tvLanguageValue.setText(text);
        }

        if (tvPasswordStyleValue != null) {
            int style = prefsManager.getPasswordStyle();
            tvPasswordStyleValue.setText(style == 1 ? getString(R.string.password_style_practical) : getString(R.string.password_style_simple));
        }
    }

    private void updateBackupEmailSummary() {
        if (tvBackupEmailValue == null) return;
        String email = prefsManager.getBackupEmail();
        if (email == null || email.isEmpty()) {
            tvBackupEmailValue.setText(getString(R.string.not_set));
        } else {
            tvBackupEmailValue.setText(email);
        }
    }

    private void updateTimedEmailBackupSummary() {
        if (tvTimedEmailBackupValue == null) return;

        if (!prefsManager.isTimedEmailBackupEnabled()) {
            tvTimedEmailBackupValue.setText(getString(R.string.timed_email_backup_off));
            return;
        }

        int days = prefsManager.getTimedEmailBackupIntervalDays();
        if (days <= 1) {
            tvTimedEmailBackupValue.setText(getString(R.string.timed_email_backup_daily));
        } else {
            tvTimedEmailBackupValue.setText(getString(R.string.timed_email_backup_weekly));
        }
    }

    private void ensureTimedEmailBackupScheduled() {
        prefsManager.setTimedEmailBackupEnabled(false);
        cancelTimedEmailBackup();
    }

    private void scheduleTimedEmailBackup(int intervalDays) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                com.baiyv.passwordmanagementapplication1.work.EmailBackupWorker.class,
                intervalDays,
                TimeUnit.DAYS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                com.baiyv.passwordmanagementapplication1.work.EmailBackupWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    private void cancelTimedEmailBackup() {
        WorkManager.getInstance(this)
                .cancelUniqueWork(com.baiyv.passwordmanagementapplication1.work.EmailBackupWorker.UNIQUE_WORK_NAME);
    }

    private void showTimedEmailBackupDialog() {
        String[] items = {
                getString(R.string.timed_email_backup_off),
                getString(R.string.timed_email_backup_daily),
                getString(R.string.timed_email_backup_weekly)
        };

        int checked;
        if (!prefsManager.isTimedEmailBackupEnabled()) {
            checked = 0;
        } else {
            int days = prefsManager.getTimedEmailBackupIntervalDays();
            checked = days <= 1 ? 1 : 2;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.timed_email_backup))
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) {
                        prefsManager.setTimedEmailBackupEnabled(false);
                        cancelTimedEmailBackup();
                        updateTimedEmailBackupSummary();
                        Toast.makeText(this, getString(R.string.toast_timed_email_backup_disabled), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return;
                    }

                    String email = prefsManager.getBackupEmail();
                    if (email == null || email.isEmpty()) {
                        prefsManager.setTimedEmailBackupEnabled(false);
                        cancelTimedEmailBackup();
                        updateTimedEmailBackupSummary();
                        Toast.makeText(this, getString(R.string.toast_set_backup_email_first), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        showEmailBackupDialog();
                        return;
                    }

                    int intervalDays = which == 1 ? 1 : 7;
                    prefsManager.setTimedEmailBackupIntervalDays(intervalDays);
                    prefsManager.setTimedEmailBackupEnabled(true);
                    scheduleTimedEmailBackup(intervalDays);
                    updateTimedEmailBackupSummary();
                    Toast.makeText(this, getString(R.string.toast_timed_email_backup_enabled, items[which]), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showThemeModeDialog() {
        String[] items = {getString(R.string.follow_system), getString(R.string.light_mode), getString(R.string.dark_mode)};
        int current = 0;
        int mode = prefsManager.getNightMode();
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) current = 1;
        else if (mode == AppCompatDelegate.MODE_NIGHT_YES) current = 2;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.theme_mode))
                .setSingleChoiceItems(items, current, (dialog, which) -> {
                    int selected;
                    if (which == 1) selected = AppCompatDelegate.MODE_NIGHT_NO;
                    else if (which == 2) selected = AppCompatDelegate.MODE_NIGHT_YES;
                    else selected = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                    prefsManager.setNightMode(selected);
                    AppCompatDelegate.setDefaultNightMode(selected);
                    updateAppearanceSummary();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showThemeColorDialog() {
        String[] items = {
                getString(R.string.theme_color_blue),
                getString(R.string.theme_color_green),
                getString(R.string.theme_color_purple),
                getString(R.string.theme_color_red)
        };

        String current = prefsManager.getThemeColor();
        int selected = 0;
        if ("green".equals(current)) selected = 1;
        else if ("purple".equals(current)) selected = 2;
        else if ("red".equals(current)) selected = 3;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.theme_color))
                .setSingleChoiceItems(items, selected, (dialog, which) -> {
                    String color;
                    if (which == 1) color = "green";
                    else if (which == 2) color = "purple";
                    else if (which == 3) color = "red";
                    else color = "blue";

                    prefsManager.setThemeColor(color);
                    recreate();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showLanguageDialog() {
        String[] items = {getString(R.string.follow_system), getString(R.string.language_chinese), getString(R.string.language_english)};
        int current = 0;
        String tag = prefsManager.getLanguageTag();
        if (tag != null) {
            if (tag.startsWith("zh")) current = 1;
            else if (tag.startsWith("en")) current = 2;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.language))
                .setSingleChoiceItems(items, current, (dialog, which) -> {
                    String selectedTag;
                    if (which == 1) selectedTag = "zh";
                    else if (which == 2) selectedTag = "en";
                    else selectedTag = "";

                    prefsManager.setLanguageTag(selectedTag);
                    if (selectedTag.isEmpty()) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                    } else {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedTag));
                    }
                    updateAppearanceSummary();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showPasswordStyleDialog() {
        String[] items = {getString(R.string.password_style_simple), getString(R.string.password_style_practical)};
        int current = prefsManager.getPasswordStyle() == 1 ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.password_style))
                .setSingleChoiceItems(items, current, (dialog, which) -> {
                    int style = which == 1 ? 1 : 0;
                    prefsManager.setPasswordStyle(style);
                    updateAppearanceSummary();
                    Toast.makeText(this, getString(R.string.toast_password_style_set, items[which]), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showChangePasswordDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextInputLayout tilCurrent = new TextInputLayout(this);
        applyDialogFieldStyle(tilCurrent);
        tilCurrent.setHint(getString(R.string.current_master_password));
        TextInputEditText etCurrent = new TextInputEditText(this);
        etCurrent.setBackground(null);
        applyDialogEditStyle(etCurrent);
        etCurrent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilCurrent.addView(etCurrent);

        TextInputLayout tilNew = new TextInputLayout(this);
        applyDialogFieldStyle(tilNew);
        tilNew.setHint(getString(R.string.new_master_password_min6));
        TextInputEditText etNew = new TextInputEditText(this);
        etNew.setBackground(null);
        applyDialogEditStyle(etNew);
        etNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilNew.addView(etNew);

        TextInputLayout tilConfirm = new TextInputLayout(this);
        applyDialogFieldStyle(tilConfirm);
        tilConfirm.setHint(getString(R.string.confirm_new_master_password));
        TextInputEditText etConfirm = new TextInputEditText(this);
        etConfirm.setBackground(null);
        applyDialogEditStyle(etConfirm);
        etConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilConfirm.addView(etConfirm);

        TextInputLayout tilHint = new TextInputLayout(this);
        applyDialogFieldStyle(tilHint);
        tilHint.setHint(getString(R.string.new_password_hint_optional_label));
        TextInputEditText etHint = new TextInputEditText(this);
        etHint.setBackground(null);
        applyDialogEditStyle(etHint);
        tilHint.addView(etHint);

        container.addView(tilCurrent);
        container.addView(tilNew);
        container.addView(tilConfirm);
        container.addView(tilHint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.change_master_password))
                .setView(container)
                .setPositiveButton(getString(R.string.confirm), null)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String current = etCurrent.getText() != null ? etCurrent.getText().toString() : "";
            String newPassword = etNew.getText() != null ? etNew.getText().toString() : "";
            String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";
            String newHint = etHint.getText() != null ? etHint.getText().toString().trim() : "";

            tilCurrent.setError(null);
            tilNew.setError(null);
            tilConfirm.setError(null);

            if (current.isEmpty()) {
                tilCurrent.setError(getString(R.string.error_enter_current_master_password));
                return;
            }
            if (newPassword.isEmpty()) {
                tilNew.setError(getString(R.string.error_enter_new_master_password));
                return;
            }
            if (newPassword.length() < 6) {
                tilNew.setError(getString(R.string.error_new_master_password_min6));
                return;
            }
            if (!newPassword.equals(confirm)) {
                tilConfirm.setError(getString(R.string.error_password_mismatch));
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            changeMasterPassword(current, newPassword, newHint, dialog);
        }));

        dialog.show();
    }

    private void changeMasterPassword(String currentPassword, String newPassword, String newHint, AlertDialog dialog) {
        new Thread(() -> {
            try {
                String token = new VaultManager(this).changePassword(currentPassword, newPassword);
                masterPassword = token;
                new AutofillSessionStore(this).clear();
                if (!newHint.isEmpty()) prefsManager.setPasswordHint(newHint);
                runOnUiThread(() -> { dialog.dismiss(); Toast.makeText(this, R.string.toast_master_password_updated, Toast.LENGTH_LONG).show(); Intent login = new Intent(this, LoginActivity.class); login.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(login); finish(); });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(this, "改密失败：原数据保持不变，请检查当前密码", Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void startEnableTwoFactor() {
        if (!requireSession()) return;
        if (masterPassword == null || masterPassword.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_not_unlocked_cannot_enable_two_factor), Toast.LENGTH_SHORT).show();
            setTwoFactorChecked(false);
            return;
        }

        String secret = TotpUtils.generateSecretBase32(20);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextView tvInfo = new TextView(this);
        tvInfo.setText(getString(R.string.two_factor_setup_message));

        TextView tvSecret = new TextView(this);
        tvSecret.setText(secret);
        tvSecret.setTextIsSelectable(true);
        tvSecret.setPadding(0, dp(8), 0, dp(8));

        TextInputLayout tilCode = new TextInputLayout(this);
        applyDialogFieldStyle(tilCode);
        tilCode.setHint(getString(R.string.totp_code_6_digits));
        TextInputEditText etCode = new TextInputEditText(this);
        etCode.setBackground(null);
        applyDialogEditStyle(etCode);
        etCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        tilCode.addView(etCode);

        container.addView(tvInfo);
        container.addView(tvSecret);
        container.addView(tilCode);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.two_factor_enable_title))
                .setView(container)
                .setPositiveButton(getString(R.string.enable), null)
                .setNegativeButton(getString(R.string.cancel), (d, w) -> setTwoFactorChecked(false))
                .setNeutralButton(getString(R.string.copy_secret), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("TOTP Secret", secret));
                    Toast.makeText(this, getString(R.string.toast_secret_copied), Toast.LENGTH_SHORT).show();
                }
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!dialog.isShowing() || isFinishing() || isDestroyed() || !requireSession()) return;
                String code = etCode.getText() != null ? etCode.getText().toString().trim() : "";
                tilCode.setError(null);

                if (code.isEmpty()) {
                tilCode.setError(getString(R.string.error_enter_totp_code));
                return;
            }

                long matchedCounter = TotpUtils.verifyCode(secret, code, System.currentTimeMillis(), 1, 6, -1L);
                if (matchedCounter < 0) {
                    tilCode.setError(getString(R.string.error_totp_incorrect));
                    return;
                }

                String enc = CryptoUtils.encrypt(secret, masterPassword);
                if (enc == null) {
                    Toast.makeText(this, getString(R.string.toast_two_factor_enable_encrypt_failed), Toast.LENGTH_LONG).show();
                    setTwoFactorChecked(false);
                    dialog.dismiss();
                    return;
                }

                prefsManager.setTotpSecretEncrypted(enc);
                prefsManager.setTotpLastCounter(matchedCounter);
                prefsManager.setTotpEnabled(true);
                Toast.makeText(this, getString(R.string.toast_two_factor_enabled), Toast.LENGTH_SHORT).show();
                setTwoFactorChecked(true);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void startDisableTwoFactor() {
        if (!requireSession()) return;
        if (!prefsManager.isTotpEnabled()) {
            setTwoFactorChecked(false);
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextInputLayout tilPassword = new TextInputLayout(this);
        applyDialogFieldStyle(tilPassword);
        tilPassword.setHint(getString(R.string.current_master_password));
        TextInputEditText etPassword = new TextInputEditText(this);
        etPassword.setBackground(null);
        applyDialogEditStyle(etPassword);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilPassword.addView(etPassword);

        TextInputLayout tilCode = new TextInputLayout(this);
        applyDialogFieldStyle(tilCode);
        tilCode.setHint(getString(R.string.totp_code_6_digits));
        TextInputEditText etCode = new TextInputEditText(this);
        etCode.setBackground(null);
        applyDialogEditStyle(etCode);
        etCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        tilCode.addView(etCode);

        container.addView(tilPassword);
        container.addView(tilCode);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.two_factor_disable_title))
                .setView(container)
                .setPositiveButton(getString(R.string.disable), null)
                .setNegativeButton(getString(R.string.cancel), (d, w) -> setTwoFactorChecked(true))
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            String code = etCode.getText() != null ? etCode.getText().toString().trim() : "";

            tilPassword.setError(null);
            tilCode.setError(null);

            if (password.isEmpty()) {
                tilPassword.setError(getString(R.string.error_enter_master_password));
                return;
            }
            if (code.isEmpty()) {
                tilCode.setError("请输入验证码");
                return;
            }

            if (!requireSession()) return;
            final String sessionToken = MyApplication.getInstance().getMasterPassword();
            if (sessionToken == null) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(() -> {
                int verification;
                try { verification = new VaultManager(getApplicationContext()).verifyPassword(password) ? 1 : 0; }
                catch (Exception failure) { verification = -1; }
                final int result = verification;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                    if (!sessionToken.equals(MyApplication.getInstance().getMasterPassword())) {
                        dialog.dismiss();
                        requireSession();
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (result < 0) {
                        Toast.makeText(this, "验证失败，请重试", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (result == 0) {
                        tilPassword.setError(getString(R.string.error_current_master_password_incorrect));
                        return;
                    }
                    String enc = prefsManager.getTotpSecretEncrypted();
                    if (enc != null && !enc.isEmpty()) {
                        String secret = CryptoUtils.decrypt(enc, sessionToken);
                        if (secret == null || secret.isEmpty()) {
                            Toast.makeText(this, R.string.toast_two_factor_disable_decrypt_failed, Toast.LENGTH_LONG).show();
                            setTwoFactorChecked(true);
                            return;
                        }
                        long matched = TotpUtils.verifyCode(secret, code, System.currentTimeMillis(),
                                1, 6, prefsManager.getTotpLastCounter());
                        if (matched < 0) {
                            tilCode.setError(getString(R.string.error_totp_incorrect));
                            return;
                        }
                    }
                    // Recheck immediately before mutating authentication settings.
                    if (!sessionToken.equals(MyApplication.getInstance().getMasterPassword())) {
                        dialog.dismiss();
                        requireSession();
                        return;
                    }
                    prefsManager.setTotpEnabled(false);
                    prefsManager.setTotpSecretEncrypted(null);
                    prefsManager.setTotpLastCounter(-1L);
                    Toast.makeText(this, R.string.toast_two_factor_disabled, Toast.LENGTH_SHORT).show();
                    setTwoFactorChecked(false);
                    dialog.dismiss();
                });
            }, "verify-disable-totp").start();
        }));

        dialog.show();
    }

    private void setTwoFactorChecked(boolean checked) {
        if (switchTwoFactor == null) return;
        changingTwoFactor = true;
        switchTwoFactor.setChecked(checked);
        changingTwoFactor = false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyDialogFieldStyle(TextInputLayout til) {
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxBackgroundColorResource(R.color.card_background);
        til.setBoxStrokeWidth(dp(1));
        til.setBoxStrokeWidthFocused(dp(2));
        til.setBoxStrokeColor(getResources().getColor(R.color.text_secondary));
        float radius = dp(10);
        til.setBoxCornerRadii(radius, radius, radius, radius);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(10);
        til.setLayoutParams(lp);
    }

    private void applyDialogEditStyle(TextInputEditText et) {
        int horizontal = dp(14);
        int vertical = dp(12);
        et.setPaddingRelative(horizontal, vertical, horizontal, vertical);
    }

    private void showAutoLockDialog() {
        String[] options = {
                getString(R.string.auto_lock_1m),
                getString(R.string.auto_lock_5m),
                getString(R.string.auto_lock_15m),
                getString(R.string.auto_lock_30m),
                getString(R.string.auto_lock_1h)
        };
        int currentSelection = getAutoLockIndex(prefsManager.getAutoLockTime());

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.auto_lock_time_title))
                .setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
                    prefsManager.setAutoLockTime(Constants.AUTO_LOCK_OPTIONS[which]);
                    Toast.makeText(this, getString(R.string.toast_set_to, options[which]), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private int getAutoLockIndex(int minutes) {
        for (int i = 0; i < Constants.AUTO_LOCK_OPTIONS.length; i++) {
            if (Constants.AUTO_LOCK_OPTIONS[i] == minutes) {
                return i;
            }
        }
        return 0;
    }

    private interface BackupSendCallback {
        void onSuccess();
        void onError(String msg);
    }

    private void sendBackupEmail(String email, String password, BackupSendCallback callback) {
        passwordRepository.getAllPasswords(new PasswordRepository.DataCallback<List<PasswordEntry>>() {
            @Override
            public void onSuccess(List<PasswordEntry> data) {
                try {
                    String backupText = BackupHelper.exportPasswordsToString(data, password);
                    ApiService api = RetrofitClient.getInstance().getApiService();
                    api.sendBackupEmail(new ApiService.BackupEmailRequest(email, backupText))
                            .enqueue(new Callback<ApiService.ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiService.ApiResponse<Void>> call, Response<ApiService.ApiResponse<Void>> response) {
                                    ApiService.ApiResponse<Void> body = response.body();
                                    if (response.isSuccessful() && body != null && body.isSuccess()) {
                                        callback.onSuccess();
                                        return;
                                    }

                                    String msg;
                                    if (body != null && body.getMessage() != null) {
                                        msg = body.getMessage();
                                    } else {
                                        msg = "HTTP " + response.code();
                                    }
                                    callback.onError(msg);
                                }

                                @Override
                                public void onFailure(Call<ApiService.ApiResponse<Void>> call, Throwable t) {
                                    String msg = t != null && t.getMessage() != null ? t.getMessage() : "";
                                    callback.onError(msg);
                                }
                            });
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    callback.onError(mapErrorMessage(msg));
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(mapErrorMessage(String.valueOf(e.getMessage())));
            }
        });
    }

    private void showClearDataDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.clear_all_data)
                .setMessage("此操作删除全部密码记录，无法撤销。请先通过本地导出保存并验证备份。")
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton("先导出（不删除）", (d, w) -> createDocumentLauncher.launch("password_manager_backup.json"))
                .setPositiveButton(R.string.delete_without_backup, (d, w) -> clearAllData()).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.about))
                .setMessage(getString(R.string.about_message))
                .setPositiveButton(getString(R.string.confirm), null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
