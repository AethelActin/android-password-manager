package com.baiyv.passwordmanagementapplication1.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.autofill.AutofillSessionStore;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.network.ApiService;
import com.baiyv.passwordmanagementapplication1.network.RetrofitClient;
import com.baiyv.passwordmanagementapplication1.utils.BackupHelper;
import com.baiyv.passwordmanagementapplication1.utils.Constants;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;
import com.baiyv.passwordmanagementapplication1.utils.TotpUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etPassword;
    private Button btnLogin;
    private Button btnForgotPassword;
    private ImageButton btnBiometric;
    private SharedPrefsManager prefsManager;
    private String masterPassword;
    private boolean pendingDisableUnavailableBiometric;
    private int unlockGeneration;

    @Override protected void onDestroy() { unlockGeneration++; super.onDestroy(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(MyApplication.resolveNoActionBarTheme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefsManager = new SharedPrefsManager(this);
        initViews();
        setupListeners();
        checkBiometricAvailability();
    }

    private void initViews() {
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        btnBiometric = findViewById(R.id.btnBiometric);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> login());

        if (btnForgotPassword != null) {
            btnForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        if (btnBiometric != null) {
            btnBiometric.setOnClickListener(v -> showBiometricPrompt());
        }
    }

    private void login() {
        if (!btnLogin.isEnabled()) return;
        pendingDisableUnavailableBiometric = false;
        String password = etPassword.getText().toString();

        if (password.isEmpty()) {
            etPassword.setError(getString(R.string.error_enter_master_password));
            return;
        }

        final int requestGeneration = ++unlockGeneration;
        btnLogin.setEnabled(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String token=new com.baiyv.passwordmanagementapplication1.utils.VaultManager(this).unlock(password);
                runOnUiThread(()-> { if (isDestroyed() || isFinishing() || requestGeneration != unlockGeneration) return; btnLogin.setEnabled(true);
                    if(token!=null) handlePostPasswordVerified(token);
                    else Toast.makeText(this,R.string.toast_password_incorrect,Toast.LENGTH_SHORT).show(); });
            } catch(Exception e) { runOnUiThread(()-> { if (isDestroyed() || isFinishing() || requestGeneration != unlockGeneration) return; btnLogin.setEnabled(true); Toast.makeText(this,"解锁失败，原数据已保留",Toast.LENGTH_LONG).show(); }); }
        });
    }

    private void handlePostPasswordVerified(String password) {
        boolean hasSession = new AutofillSessionStore(this).getIfNotExpired() != null;

        if (!hasSession && prefsManager.useBiometric()) {
            BiometricManager biometricManager = BiometricManager.from(this);
            int canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
            );
            if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                pendingDisableUnavailableBiometric = true;
                continueAfterPasswordOrBiometric(password, false, false);
                return;
            }
            showBiometricPromptForExpiredUnlock(password);
            return;
        }

        continueAfterPasswordOrBiometric(password, hasSession, false);
    }

    private void continueAfterPasswordOrBiometric(String password, boolean hasSession, boolean unlockedByBiometric) {
        boolean totpEnabled = prefsManager.isTotpEnabled() && prefsManager.getTotpSecretEncrypted() != null;
        boolean skipTotp = unlockedByBiometric && hasSession;

        if (totpEnabled && !skipTotp) {
            showTotpDialog(password);
            return;
        }

        proceedLogin(password);
    }

    private void proceedLogin(String password) {
        if (isDestroyed() || isFinishing()) return;
        if (pendingDisableUnavailableBiometric) {
            new AlertDialog.Builder(this)
                .setTitle("指纹认证不可用")
                .setMessage("主密码及已启用的验证码已验证。是否关闭当前不可用的指纹认证并解锁？")
                .setPositiveButton("关闭指纹并解锁", (dialog, which) -> {
                    prefsManager.setUseBiometric(false);
                    pendingDisableUnavailableBiometric = false;
                    proceedLogin(password);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> pendingDisableUnavailableBiometric = false)
                .show();
            return;
        }
        masterPassword = password;
        MyApplication.getInstance().setMasterPassword(masterPassword);
        prefsManager.setLastPasswordViewAuthTime(System.currentTimeMillis());
        Toast.makeText(this, getString(R.string.toast_login_success), Toast.LENGTH_SHORT).show();
        goToMain();
    }

    private String mapBiometricEnableMessage(int canAuthenticate) {
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

    private void showBiometricPromptForExpiredUnlock(String password) {
        final int requestGeneration = unlockGeneration;
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        if (isDestroyed() || isFinishing() || requestGeneration != unlockGeneration) return;
                        continueAfterPasswordOrBiometric(password, false, true);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(LoginActivity.this, getString(R.string.biometric_auth_failed, String.valueOf(errString)), Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle_2fa))
                .setNegativeButtonText(getString(R.string.biometric_use_password))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showForgotPasswordDialog() {
        String hint = prefsManager.getPasswordHint();

        if (hint != null && !hint.isEmpty()) {
            showPasswordHintDialog(hint);
        } else {
            showResetOptionsDialog();
        }
    }

    private void showPasswordHintDialog(String hint) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.password_hint_title))
                .setMessage(getString(R.string.password_hint_message, hint))
                .setPositiveButton(getString(R.string.password_hint_positive), null)
                .setNegativeButton(getString(R.string.password_hint_negative), (dialog, which) -> showResetOptionsDialog())
                .show();
    }

    private void showResetOptionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.forgot_password_title))
                .setMessage(getString(R.string.reset_options_message))
                .setPositiveButton(getString(R.string.reset_app), (dialog, which) -> showResetConfirmDialog())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showResetConfirmDialog() {
        String email = prefsManager.getBackupEmail();
        if (email == null || email.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.reset_confirm_title))
                    .setMessage(getString(R.string.reset_confirm_message))
                    .setPositiveButton(getString(R.string.confirm_reset), (dialog, which) -> resetApplication())
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reset_confirm_title))
                .setMessage(getString(R.string.reset_confirm_message))
                .setPositiveButton(getString(R.string.backup_then_reset), null)
                .setNeutralButton(getString(R.string.reset_without_backup), (d, w) -> resetApplication())
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);

            startPreResetEmailBackup(email, new PreResetBackupCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(LoginActivity.this, getString(R.string.toast_backup_email_sent), Toast.LENGTH_SHORT).show();
                        resetApplication();
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        Toast.makeText(LoginActivity.this, getString(R.string.toast_backup_email_failed, msg), Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onSkippedNoSessionPassword() {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(LoginActivity.this, getString(R.string.toast_backup_skipped_reset), Toast.LENGTH_LONG).show();
                        // No backup was created: require an explicit destructive choice.
                    });
                }
            });
        }));

        dialog.show();
    }

    private interface PreResetBackupCallback {
        void onSuccess();
        void onError(String msg);
        void onSkippedNoSessionPassword();
    }

    private void startPreResetEmailBackup(String email, PreResetBackupCallback callback) {
        String mp = getCachedMasterPasswordForBackup();
        if (mp == null || mp.isEmpty()) {
            callback.onSkippedNoSessionPassword();
            return;
        }

        Toast.makeText(this, getString(R.string.toast_sending_backup), Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<PasswordEntry> data = AppDatabase.getInstance(this).passwordDao().getAllPasswords();
                String backupText = BackupHelper.exportPasswordsToString(data, mp);

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
                callback.onError(msg);
            }
        });
    }

    private String getCachedMasterPasswordForBackup() {
        String mp = null;
        MyApplication app = MyApplication.getInstance();
        if (app != null) {
            mp = app.getMasterPassword();
        }
        if (mp == null || mp.isEmpty()) {
            mp = new AutofillSessionStore(this).getIfNotExpired();
        }
        return mp;
    }

    private void resetApplication() {
        unlockGeneration++;
        pendingDisableUnavailableBiometric = false;
        masterPassword = null;
        MyApplication.getInstance().clearMasterPassword();
        Toast.makeText(this, getString(R.string.resetting), Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase.getInstance(this).runInTransaction(()-> { AppDatabase.getInstance(this).passwordDao().deleteAll(); AppDatabase.getInstance(this).vaultDao().clear(); });
                MyApplication.getInstance().clearMasterPassword();
                new AutofillSessionStore(this).clear();

                runOnUiThread(() -> {
                    prefsManager.clearAll();
                    Toast.makeText(this, getString(R.string.reset_success), Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(this, RegisterActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.reset_failed), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void checkBiometricAvailability() {
        if (btnBiometric == null) return;

        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canBio = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS;

        boolean hasSession = new AutofillSessionStore(this).getIfNotExpired() != null;

        if (canBio && prefsManager.useBiometric() && hasSession) {
            btnBiometric.setVisibility(android.view.View.VISIBLE);
        } else {
            btnBiometric.setVisibility(android.view.View.GONE);
        }
    }

    private void showBiometricPrompt() {
        final int requestGeneration = unlockGeneration;
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        if (isDestroyed() || isFinishing() || requestGeneration != unlockGeneration) return;
                        String sessionPassword = new AutofillSessionStore(LoginActivity.this).getIfNotExpired();
                        if (sessionPassword == null || sessionPassword.isEmpty()) {
                            Toast.makeText(LoginActivity.this, getString(R.string.biometric_login_once), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        continueAfterPasswordOrBiometric(sessionPassword, true, true);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(LoginActivity.this, getString(R.string.biometric_auth_failed, String.valueOf(errString)), Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_use_password))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showTotpDialog(String password) {
        final int requestGeneration = unlockGeneration;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextInputLayout til = new TextInputLayout(this);
        applyDialogFieldStyle(til);
        til.setHint(getString(R.string.totp_code_6_digits));
        TextInputEditText et = new TextInputEditText(this);
        et.setBackground(null);
        applyDialogEditStyle(et);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        til.addView(et);
        container.addView(til);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.two_factor))
                .setMessage(getString(R.string.totp_prompt_message))
                .setView(container)
                .setPositiveButton(getString(R.string.verify), null)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (isDestroyed() || isFinishing() || requestGeneration != unlockGeneration) { dialog.dismiss(); return; }
            String code = et.getText() != null ? et.getText().toString().trim() : "";
            til.setError(null);
            if (code.isEmpty()) {
                til.setError(getString(R.string.error_enter_totp_code));
                return;
            }

            String enc = prefsManager.getTotpSecretEncrypted();
            String secret = enc == null ? null : CryptoUtils.decrypt(enc, password);
            if (secret == null || secret.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_totp_decrypt_failed), Toast.LENGTH_LONG).show();
                dialog.dismiss();
                return;
            }

            long matched = TotpUtils.verifyCode(secret, code, System.currentTimeMillis(), 1, 6, prefsManager.getTotpLastCounter());
            if (matched < 0) {
                til.setError(getString(R.string.error_totp_incorrect));
                return;
            }

            prefsManager.setTotpLastCounter(matched);
            dialog.dismiss();
            proceedLogin(password);
        }));

        dialog.show();
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

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);

        startActivity(intent);
        new AutofillSessionStore(this).save(masterPassword);
        finish();
    }
}
