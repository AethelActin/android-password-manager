package com.baiyv.passwordmanagementapplication1.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.ui.adapter.PasswordAdapter;
import com.baiyv.passwordmanagementapplication1.utils.Constants;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.VaultManager;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;
import com.baiyv.passwordmanagementapplication1.utils.TotpUtils;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PasswordAdapter.OnPasswordClickListener {

    private RecyclerView rvPasswords;
    private TextView tvEmpty;
    private TextInputEditText etSearch;
    private FloatingActionButton fabAdd;

    private PasswordAdapter adapter;
    private List<PasswordEntry> passwordList = new ArrayList<>();
    private AppDatabase database;
    private SharedPrefsManager prefsManager;
    private String masterPassword;
    private int appliedThemeResId;
    private int appliedPasswordStyle;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int queryVersion;
    private String currentQuery = "";

    private boolean refreshSession() {
        masterPassword = MyApplication.getInstance().getMasterPassword();
        if (masterPassword != null && !masterPassword.isEmpty()) return true;
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(login);
        finish();
        return false;
    }

    private void postIfActive(Runnable action) {
        mainHandler.post(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    @Override
    protected void onDestroy() {
        queryVersion++;
        mainHandler.removeCallbacksAndMessages(null);
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = MyApplication.resolveNoActionBarTheme(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!refreshSession()) return;

        database = AppDatabase.getInstance(this);
        prefsManager = new SharedPrefsManager(this);
        appliedPasswordStyle = prefsManager.getPasswordStyle();

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();
        loadPasswords();
    }

    @Override
    protected void onPause() {
        if (adapter != null) {
            adapter.hideAllPasswords();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!refreshSession()) return;
        int currentTheme = MyApplication.resolveNoActionBarTheme(this);
        if (currentTheme != appliedThemeResId) {
            appliedThemeResId = currentTheme;
            recreate();
            return;
        }

        int currentStyle = prefsManager.getPasswordStyle();
        if (currentStyle != appliedPasswordStyle) {
            appliedPasswordStyle = currentStyle;
            recreate();
            return;
        }

        loadPasswords();
    }

    private void initViews() {
        rvPasswords = findViewById(R.id.rvPasswords);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        fabAdd = findViewById(R.id.fabAdd);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.app_name);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new PasswordAdapter(passwordList, this, masterPassword, appliedPasswordStyle);
        rvPasswords.setLayoutManager(new LinearLayoutManager(this));
        rvPasswords.setAdapter(adapter);
    }

    private void setupListeners() {
        fabAdd.setOnClickListener(v -> {
            if (!refreshSession()) return;
            Intent intent = new Intent(this, AddEditPasswordActivity.class);
            startActivityForResult(intent, Constants.REQUEST_ADD_PASSWORD);
        });

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    searchPasswords(s.toString());
                }
            });
        }
    }

    private void loadPasswords() {
        searchPasswords(currentQuery);
    }

    private void searchPasswords(String keyword) {
        currentQuery = keyword;
        final int version = ++queryVersion;
        databaseExecutor.execute(() -> {
            try {
                List<PasswordEntry> passwords = keyword.isEmpty()
                        ? database.passwordDao().getAllPasswords()
                        : database.passwordDao().searchPasswords(keyword);
                postIfActive(() -> {
                    if (version != queryVersion) return;
                    passwordList.clear();
                    passwordList.addAll(passwords);
                    adapter.notifyDataSetChanged();
                    updateEmptyView();
                });
            } catch (Exception e) {
                postIfActive(() -> {
                    if (version == queryVersion) {
                        Toast.makeText(this, R.string.toast_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateEmptyView() {
        if (passwordList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvPasswords.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvPasswords.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void ensurePasswordViewAuth(Runnable onAuthed) {
        if (!refreshSession()) return;
        Runnable checkedAction = () -> {
            if (!isFinishing() && !isDestroyed() && refreshSession()) onAuthed.run();
        };

        if (!prefsManager.isPasswordViewAuthEnabled()) {
            checkedAction.run();
            return;
        }

        if (prefsManager.isPasswordViewAuthValid()) {
            checkedAction.run();
            return;
        }

        boolean totpEnabled = prefsManager.isTotpEnabled() && prefsManager.getTotpSecretEncrypted() != null;
        Runnable fallback = () -> {
            if (totpEnabled) {
                showPasswordViewTotpDialog(checkedAction);
            } else {
                showPasswordViewMasterPasswordDialog(checkedAction);
            }
        };

        if (prefsManager.useBiometric()) {
            BiometricManager biometricManager = BiometricManager.from(this);
            int canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
            );
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                showPasswordViewBiometricPrompt(checkedAction, fallback);
                return;
            }
            Toast.makeText(this, mapBiometricEnableMessage(canAuthenticate), Toast.LENGTH_SHORT).show();
        }

        fallback.run();
    }

    private void showPasswordViewBiometricPrompt(Runnable onAuthed, Runnable onFallback) {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        prefsManager.setLastPasswordViewAuthTime(System.currentTimeMillis());
                        onAuthed.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && onFallback != null) {
                            onFallback.run();
                            return;
                        }
                        Toast.makeText(MainActivity.this, getString(R.string.biometric_auth_failed, String.valueOf(errString)), Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_use_password))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showPasswordViewTotpDialog(Runnable onAuthed) {
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
            String code = et.getText() != null ? et.getText().toString().trim() : "";
            til.setError(null);
            if (code.isEmpty()) {
                til.setError(getString(R.string.error_enter_totp_code));
                return;
            }

            String enc = prefsManager.getTotpSecretEncrypted();
            String secret = enc == null ? null : CryptoUtils.decrypt(enc, masterPassword);
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
            prefsManager.setLastPasswordViewAuthTime(System.currentTimeMillis());
            dialog.dismiss();
            onAuthed.run();
        }));

        dialog.show();
    }

    private void showPasswordViewMasterPasswordDialog(Runnable onAuthed) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);

        TextInputLayout til = new TextInputLayout(this);
        applyDialogFieldStyle(til);
        til.setHint(getString(R.string.master_password));
        TextInputEditText et = new TextInputEditText(this);
        et.setBackground(null);
        applyDialogEditStyle(et);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        container.addView(til);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.master_password))
                .setMessage(getString(R.string.login_subtitle))
                .setView(container)
                .setPositiveButton(getString(R.string.verify), null)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = et.getText() != null ? et.getText().toString() : "";
            til.setError(null);
            if (input.isEmpty()) {
                til.setError(getString(R.string.error_enter_master_password));
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            databaseExecutor.execute(() -> {
                boolean verified;
                try { verified = new VaultManager(getApplicationContext()).verifyPassword(input); }
                catch (Exception failure) { verified = false; }
                final boolean valid = verified;
                postIfActive(() -> {
                    if (!dialog.isShowing()) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (valid && refreshSession()) {
                        prefsManager.setLastPasswordViewAuthTime(System.currentTimeMillis());
                        dialog.dismiss();
                        onAuthed.run();
                    } else if (!valid) {
                        til.setError(getString(R.string.toast_password_incorrect));
                    }
                });
            });
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

    @Override
    public void onPasswordClick(PasswordEntry password) {
        if (!refreshSession()) return;
        Intent intent = new Intent(this, AddEditPasswordActivity.class);
        intent.putExtra(Constants.EXTRA_PASSWORD_ID, password.getId());
        startActivityForResult(intent, Constants.REQUEST_EDIT_PASSWORD);
    }

    @Override
    public void onCopyAccountClick(PasswordEntry password) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("account", password.getUsername() == null ? "" : password.getUsername());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCopyPasswordClick(PasswordEntry password) {
        ensurePasswordViewAuth(() -> {
            String value = CryptoUtils.decrypt(password.getEncryptedPassword(), masterPassword);
            if (value == null) {
                Toast.makeText(this, R.string.toast_decrypt_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            copySensitiveText("password", value);
        });
    }

    private void copySensitiveText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        // Unique label distinguishes this copy from later copies, even with identical text.
        String token = label + ":" + java.util.UUID.randomUUID();
        ClipData clip = ClipData.newPlainText(token, value);
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.toast_password_copied_auto_clear, Toast.LENGTH_SHORT).show();
        // Application-scoped task intentionally outlives this screen; it captures no Activity.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription() != null
                    && token.contentEquals(clipboard.getPrimaryClipDescription().getLabel())) {
                if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip();
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        }, Constants.CLIPBOARD_CLEAR_DELAY);
    }

    @Override
    public void onCopyAllClick(PasswordEntry password) {
        ensurePasswordViewAuth(() -> {
            String decryptedPassword = CryptoUtils.decrypt(password.getEncryptedPassword(), masterPassword);
            if (decryptedPassword == null) {
                Toast.makeText(this, getString(R.string.toast_decrypt_failed), Toast.LENGTH_SHORT).show();
                return;
            }

            String title = password.getTitle() == null ? "" : password.getTitle();
            String username = password.getUsername() == null ? "" : password.getUsername();
            String category = password.getCategory() == null ? "" : password.getCategory();
            String website = formatTargetLabel(password.getWebsite());
            String notes = password.getNotes() == null ? "" : password.getNotes();

            String all = title
                    + "\n" + getString(R.string.detail_account_label) + " " + username
                    + "\n" + getString(R.string.detail_password_label) + " " + decryptedPassword
                    + "\n" + getString(R.string.detail_tag_label) + " " + category
                    + "\n" + getString(R.string.detail_website_label) + " " + website
                    + "\n" + getString(R.string.detail_notes_label) + " " + notes;

            copySensitiveText("all", all);
        });
    }

    @Override
    public void onCopyClick(PasswordEntry password) {
        onCopyPasswordClick(password);
    }

    @Override
    public void onOpenTargetClick(PasswordEntry password) {
        String raw = password != null ? password.getWebsite() : null;
        if (raw == null || raw.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_open_target_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        String target = raw.trim();
        String pkg = null;

        if (target.startsWith(Constants.TARGET_PREFIX_APP)) {
            pkg = target.substring(Constants.TARGET_PREFIX_APP.length()).trim();
        } else if (target.startsWith("pkg:")) {
            pkg = target.substring("pkg:".length()).trim();
        }

        if (pkg != null) {
            if (!pkg.isEmpty()) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    startActivity(launch);
                    return;
                }
            }
            Toast.makeText(this, getString(R.string.toast_open_target_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        String url = target;
        if (!url.contains("://")) {
            url = "https://" + url;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_open_target_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTargetLabel(String raw) {
        if (raw == null) return "";
        String target = raw.trim();
        if (!target.startsWith(Constants.TARGET_PREFIX_APP)) return target;

        String pkg = target.substring(Constants.TARGET_PREFIX_APP.length()).trim();
        if (pkg.isEmpty()) return "";

        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            return pkg;
        }
    }


    @Override
    public void onDeleteClick(PasswordEntry password) {
        if (!refreshSession()) return;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_title))
                .setMessage(getString(R.string.dialog_delete_message, password.getTitle()))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    if (!refreshSession()) return;
                    databaseExecutor.execute(() -> {
                        try {
                            int affected = database.passwordDao().delete(password);
                            postIfActive(() -> {
                                loadPasswords();
                                Toast.makeText(this, affected == 1 ? getString(R.string.toast_deleted)
                                        : "记录已不存在", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            postIfActive(() -> Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_generator) {
            startActivity(new Intent(this, PasswordGeneratorActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            if (!refreshSession()) return true;
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadPasswords();
        }
    }

}
