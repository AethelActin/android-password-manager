package com.baiyv.passwordmanagementapplication1.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.utils.Constants;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.VaultManager;
import com.baiyv.passwordmanagementapplication1.utils.PasswordGenerator;
import com.baiyv.passwordmanagementapplication1.utils.PasswordStrengthChecker;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;
import com.baiyv.passwordmanagementapplication1.utils.TotpUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEditPasswordActivity extends AppCompatActivity {

    private static final int TARGET_TYPE_WEBSITE = 0;
    private static final int TARGET_TYPE_APP = 1;

    private NestedScrollView scrollContainer;
    private TextInputEditText etTitle, etUsername, etPassword, etWebsite, etNotes, etAppName;
    private TextInputLayout tilPassword, tilWebsite, tilAppName;
    private AutoCompleteTextView actvCategory, actvTargetType;
    private View strengthBar1, strengthBar2, strengthBar3, strengthBar4;
    private TextView tvStrength;
    private Button btnGenerate, btnSave, btnPickApp;
    private View layoutAppPick;

    private int targetType = TARGET_TYPE_WEBSITE;
    private String selectedAppPackage;

    private AppDatabase database;
    private SharedPrefsManager prefsManager;
    private String masterPassword;
    private int passwordId = -1;
    private int appliedThemeResId;
    private boolean isEditMode = false;
    private boolean saving;
    private PasswordEntry loadedEntry;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable autoHidePasswordRunnable;

    private String[] categories;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = MyApplication.resolveNoActionBarTheme(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_password);

        database = AppDatabase.getInstance(this);
        if (!refreshSession()) return;
        passwordId = getIntent().getIntExtra(Constants.EXTRA_PASSWORD_ID, -1);
        isEditMode = passwordId != -1;

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? R.string.edit_password : R.string.add_password);
        }

        initViews();
        setupImePadding();
        prefsManager = new SharedPrefsManager(this);
        setupPasswordVisibilityGuard();
        setupTargetTypeDropdown();
        setupListeners();
        setupCategoryDropdown();

        if (isEditMode) {
            btnSave.setEnabled(false);
            ensurePasswordViewAuth(this::loadPassword);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!refreshSession()) return;
        int currentTheme = MyApplication.resolveNoActionBarTheme(this);
        if (currentTheme != appliedThemeResId) {
            appliedThemeResId = currentTheme;
            recreate();
        }
    }

    private void initViews() {
        scrollContainer = findViewById(R.id.scrollContainer);
        etTitle = findViewById(R.id.etTitle);
        etUsername = findViewById(R.id.etUsername);
        tilPassword = findViewById(R.id.tilPassword);
        etPassword = findViewById(R.id.etPassword);
        tilWebsite = findViewById(R.id.tilWebsite);
        etWebsite = findViewById(R.id.etWebsite);
        actvTargetType = findViewById(R.id.actvTargetType);
        layoutAppPick = findViewById(R.id.layoutAppPick);
        tilAppName = findViewById(R.id.tilAppName);
        etAppName = findViewById(R.id.etAppName);
        btnPickApp = findViewById(R.id.btnPickApp);
        etNotes = findViewById(R.id.etNotes);
        actvCategory = findViewById(R.id.actvCategory);
        strengthBar1 = findViewById(R.id.strengthBar1);
        strengthBar2 = findViewById(R.id.strengthBar2);
        strengthBar3 = findViewById(R.id.strengthBar3);
        strengthBar4 = findViewById(R.id.strengthBar4);
        tvStrength = findViewById(R.id.tvStrength);
        btnGenerate = findViewById(R.id.btnGenerate);
        btnSave = findViewById(R.id.btnSave);
    }

    private void setupImePadding() {
        if (scrollContainer == null) return;

        int baseLeft = scrollContainer.getPaddingLeft();
        int baseTop = scrollContainer.getPaddingTop();
        int baseRight = scrollContainer.getPaddingRight();
        int baseBottom = scrollContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(scrollContainer, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(baseLeft, baseTop, baseRight, baseBottom + imeBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(scrollContainer);
    }

    private void setupListeners() {
        TextView.OnEditorActionListener doneListener = (v, actionId, event) -> {
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                v.clearFocus();
                hideKeyboard(v);
                return true;
            }
            return false;
        };

        if (etTitle != null) etTitle.setOnEditorActionListener(doneListener);
        if (etUsername != null) etUsername.setOnEditorActionListener(doneListener);
        if (etPassword != null) etPassword.setOnEditorActionListener(doneListener);
        if (etWebsite != null) etWebsite.setOnEditorActionListener(doneListener);

        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateStrengthIndicator(s.toString());
            }
        });

        btnGenerate.setOnClickListener(v -> {
            String password = PasswordGenerator.generateStrongPassword();
            etPassword.setText(password);
        });

        btnSave.setOnClickListener(v -> savePassword());

        if (btnPickApp != null) {
            btnPickApp.setOnClickListener(v -> showAppPickerDialog());
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupCategoryDropdown() {
        categories = new String[]{
                getString(R.string.category_bank),
                getString(R.string.category_social),
                getString(R.string.category_shopping),
                getString(R.string.category_email),
                getString(R.string.category_work),
                getString(R.string.category_game),
                getString(R.string.category_other)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categories);
        actvCategory.setAdapter(adapter);
        actvCategory.setText(getString(R.string.category_other), false);
    }

    private void setupTargetTypeDropdown() {
        if (actvTargetType == null) return;

        String[] items = new String[]{
                getString(R.string.target_type_website),
                getString(R.string.target_type_app)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, items);
        actvTargetType.setAdapter(adapter);
        actvTargetType.setText(items[0], false);
        targetType = TARGET_TYPE_WEBSITE;
        applyTargetTypeUi(false);

        actvTargetType.setOnItemClickListener((parent, view, position, id) -> {
            targetType = position == 1 ? TARGET_TYPE_APP : TARGET_TYPE_WEBSITE;
            applyTargetTypeUi(true);
        });
    }

    private void applyTargetTypeUi(boolean clearErrors) {
        boolean isApp = targetType == TARGET_TYPE_APP;
        if (layoutAppPick != null) layoutAppPick.setVisibility(isApp ? View.VISIBLE : View.GONE);
        if (tilWebsite != null) tilWebsite.setVisibility(isApp ? View.GONE : View.VISIBLE);

        if (clearErrors) {
            if (tilAppName != null) tilAppName.setError(null);
            if (tilWebsite != null) tilWebsite.setError(null);
        }
    }

    private void showAppPickerDialog() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        if (apps == null || apps.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_open_target_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        Collections.sort(apps, (a, b) -> {
            CharSequence la = a.loadLabel(pm);
            CharSequence lb = b.loadLabel(pm);
            String sa = la == null ? "" : la.toString();
            String sb = lb == null ? "" : lb.toString();
            return sa.compareToIgnoreCase(sb);
        });

        CharSequence[] labels = new CharSequence[apps.size()];
        String[] packages = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo ri = apps.get(i);
            labels[i] = ri.loadLabel(pm);
            packages[i] = ri.activityInfo != null ? ri.activityInfo.packageName : "";
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.pick_app))
                .setItems(labels, (dialog, which) -> {
                    selectedAppPackage = packages[which] == null ? "" : packages[which].trim();
                    if (etAppName != null) {
                        CharSequence label = labels[which];
                        etAppName.setText(label == null ? "" : label.toString());
                    }
                    if (tilAppName != null) tilAppName.setError(null);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private String resolveAppLabel(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        String pkg = packageName.trim();
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyLoadedTarget(String raw) {
        String target = raw == null ? "" : raw.trim();
        if (target.startsWith(Constants.TARGET_PREFIX_APP)) {
            targetType = TARGET_TYPE_APP;
            if (actvTargetType != null) {
                actvTargetType.setText(getString(R.string.target_type_app), false);
            }
            applyTargetTypeUi(false);

            selectedAppPackage = target.substring(Constants.TARGET_PREFIX_APP.length()).trim();
            String label = resolveAppLabel(selectedAppPackage);
            if (etAppName != null) {
                etAppName.setText(label != null ? label : selectedAppPackage);
            }
            return;
        }

        targetType = TARGET_TYPE_WEBSITE;
        if (actvTargetType != null) {
            actvTargetType.setText(getString(R.string.target_type_website), false);
        }
        applyTargetTypeUi(false);
        if (etWebsite != null) etWebsite.setText(target);
    }

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

    private void loadPassword() {
        databaseExecutor.execute(() -> {
            try {
                PasswordEntry entry = database.passwordDao().getPasswordById(passwordId);
                if (entry == null) throw new IllegalStateException("Entry missing");
                String decrypted = CryptoUtils.decrypt(entry.getEncryptedPassword(), masterPassword);
                if (decrypted == null) throw new IllegalStateException("Cannot decrypt");
                postIfActive(() -> {
                    loadedEntry = entry;
                    etTitle.setText(entry.getTitle());
                    etUsername.setText(entry.getUsername());
                    etPassword.setText(decrypted);
                    applyLoadedTarget(entry.getWebsite());
                    etNotes.setText(entry.getNotes());
                    actvCategory.setText(entry.getCategory(), false);
                    btnSave.setEnabled(true);
                });
            } catch (Exception e) {
                postIfActive(() -> Toast.makeText(this, R.string.toast_load_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private void setupPasswordVisibilityGuard() {
        if (tilPassword == null || etPassword == null) return;

        tilPassword.setEndIconDrawable(R.drawable.ic_visibility_off);
        tilPassword.setEndIconOnClickListener(v -> {
            boolean isVisible = !(etPassword.getTransformationMethod() instanceof PasswordTransformationMethod);
            if (isVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                tilPassword.setEndIconDrawable(R.drawable.ic_visibility_off);
                if (etPassword.getText() != null) {
                    etPassword.setSelection(etPassword.getText().length());
                }
                return;
            }

            Runnable showPassword = () -> {
                etPassword.setTransformationMethod(null);
                tilPassword.setEndIconDrawable(R.drawable.ic_visibility);
                if (etPassword.getText() != null) {
                    etPassword.setSelection(etPassword.getText().length());
                }
                scheduleAutoHidePassword();
            };

            if (isEditMode) {
                ensurePasswordViewAuth(showPassword);
            } else {
                showPassword.run();
            }
        });

        etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }

    @Override
    protected void onPause() {
        hidePasswordNow();
        super.onPause();
    }

    private void scheduleAutoHidePassword() {
        if (autoHidePasswordRunnable != null) {
            mainHandler.removeCallbacks(autoHidePasswordRunnable);
        }
        autoHidePasswordRunnable = () -> {
            boolean isVisible = !(etPassword.getTransformationMethod() instanceof PasswordTransformationMethod);
            if (isVisible) {
                hidePasswordNow();
            }
        };
        mainHandler.postDelayed(autoHidePasswordRunnable, 30_000L);
    }

    private void hidePasswordNow() {
        if (autoHidePasswordRunnable != null) {
            mainHandler.removeCallbacks(autoHidePasswordRunnable);
            autoHidePasswordRunnable = null;
        }
        if (tilPassword == null || etPassword == null) return;

        etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
        tilPassword.setEndIconDrawable(R.drawable.ic_visibility_off);
        if (etPassword.getText() != null) {
            etPassword.setSelection(etPassword.getText().length());
        }
    }

    private void ensurePasswordViewAuth(Runnable onAuthed) {
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
                        Toast.makeText(AddEditPasswordActivity.this, getString(R.string.biometric_auth_failed, String.valueOf(errString)), Toast.LENGTH_SHORT).show();
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

    private void updateStrengthIndicator(String password) {
        int strength = PasswordStrengthChecker.checkStrength(password);
        int color = PasswordStrengthChecker.getStrengthColor(strength);
        String text = PasswordStrengthChecker.getStrengthText(strength);

        int defaultColor = 0xFFE0E0E0;
        strengthBar1.setBackgroundColor(defaultColor);
        strengthBar2.setBackgroundColor(defaultColor);
        strengthBar3.setBackgroundColor(defaultColor);
        strengthBar4.setBackgroundColor(defaultColor);

        if (strength >= 1) strengthBar1.setBackgroundColor(color);
        if (strength >= 2) strengthBar2.setBackgroundColor(color);
        if (strength >= 3) strengthBar3.setBackgroundColor(color);
        if (strength >= 4) strengthBar4.setBackgroundColor(color);

        tvStrength.setText(text);
        tvStrength.setTextColor(color);
    }

    private void savePassword() {
        if (saving || (isEditMode && loadedEntry == null) || !refreshSession()) return;
        String title = etTitle.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String target;
        if (targetType == TARGET_TYPE_APP) {
            String pkg = selectedAppPackage == null ? "" : selectedAppPackage.trim();
            if (pkg.isEmpty()) {
                if (tilAppName != null) tilAppName.setError(getString(R.string.error_select_app));
                return;
            }
            target = Constants.TARGET_PREFIX_APP + pkg;
        } else {
            target = etWebsite.getText().toString().trim();
        }
        String category = actvCategory.getText().toString();
        String notes = etNotes.getText().toString().trim();

        // 验证
        if (title.isEmpty()) {
            etTitle.setError(getString(R.string.error_enter_title));
            return;
        }
        if (username.isEmpty()) {
            etUsername.setError(getString(R.string.error_enter_username));
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError(getString(R.string.error_enter_password));
            return;
        }

        saving = true;
        btnSave.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                String encryptedPassword = CryptoUtils.encrypt(password, masterPassword);
                if (encryptedPassword == null || encryptedPassword.isEmpty()) {
                    throw new IllegalStateException("Encryption failed");
                }
                // Reuse the loaded row so creation time and favorite state survive edits.
                PasswordEntry entry = isEditMode ? loadedEntry : new PasswordEntry();
                entry.setTitle(title);
                entry.setUsername(username);
                entry.setEncryptedPassword(encryptedPassword);
                entry.setWebsite(target);
                entry.setCategory(category);
                entry.setNotes(notes);
                entry.setStrengthLevel(PasswordStrengthChecker.checkStrength(password));
                entry.setUpdateTime(System.currentTimeMillis());
                if (isEditMode) {
                    if (database.passwordDao().update(entry) != 1) {
                        throw new IllegalStateException("Entry no longer exists");
                    }
                } else if (database.passwordDao().insert(entry) <= 0) {
                    throw new IllegalStateException("Insert failed");
                }
                postIfActive(() -> {
                    Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                postIfActive(() -> {
                    saving = false;
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "保存失败，原有记录未被覆盖，请重试", Toast.LENGTH_LONG).show();
                });
            }
        });
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