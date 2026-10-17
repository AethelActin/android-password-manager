package com.baiyv.passwordmanagementapplication1.autofill;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;
import com.baiyv.passwordmanagementapplication1.utils.TotpUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.concurrent.Executors;

public class AutofillAuthActivity extends AppCompatActivity {

    private TextInputEditText etPassword;
    private Button btnUnlock;
    private SharedPrefsManager prefsManager;
    private AutofillParsedStructure parsed;
    private int appliedThemeResId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = MyApplication.resolveNoActionBarTheme(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefsManager = new SharedPrefsManager(this);
        parsed = AutofillResponseFactory.readFromAuthIntent(getIntent());
        if (parsed == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        View btnForgot = findViewById(R.id.btnForgotPassword);
        View btnBiometric = findViewById(R.id.btnBiometric);

        if (tvTitle != null) tvTitle.setText(getString(R.string.autofill_unlock_title));
        if (tvSubtitle != null) tvSubtitle.setText(getString(R.string.autofill_unlock_subtitle));
        if (btnForgot != null) btnForgot.setVisibility(View.GONE);
        if (btnBiometric != null) btnBiometric.setVisibility(View.GONE);

        etPassword = findViewById(R.id.etPassword);
        btnUnlock = findViewById(R.id.btnLogin);

        btnUnlock.setOnClickListener(v -> unlock());
    }

    @Override
    protected void onResume() {
        super.onResume();
        int currentTheme = MyApplication.resolveNoActionBarTheme(this);
        if (currentTheme != appliedThemeResId) {
            appliedThemeResId = currentTheme;
            recreate();
        }
    }

    private void unlock() {
        String password = etPassword.getText() == null ? null : etPassword.getText().toString();
        if (password == null || password.isEmpty()) {
            etPassword.setError(getString(R.string.error_enter_master_password));
            return;
        }

        Executors.newSingleThreadExecutor().execute(()-> {
            try {
                String token=new com.baiyv.passwordmanagementapplication1.utils.VaultManager(this).unlock(password);
                runOnUiThread(()-> {
                    if(token==null) { Toast.makeText(this,R.string.toast_password_incorrect,Toast.LENGTH_SHORT).show(); return; }
                    if(prefsManager.isTotpEnabled() && prefsManager.getTotpSecretEncrypted()!=null) showTotpDialog(token);
                    else proceedUnlock(token);
                });
            } catch(Exception e) { runOnUiThread(()->Toast.makeText(this,"解锁失败，原数据已保留",Toast.LENGTH_LONG).show()); }
        });
    }

    private void showTotpDialog(String password) {
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
            proceedUnlock(password);
        }));

        dialog.show();
    }

    private void proceedUnlock(String password) {
        MyApplication.getInstance().setMasterPassword(password);
        new AutofillSessionStore(this).save(password);
        Executors.newSingleThreadExecutor().execute(() -> {
            List<PasswordEntry> all = AppDatabase.getInstance(this).passwordDao().getAllPasswords();
            List<PasswordEntry> matches = AutofillVaultMatcher.pickTopMatches(all, parsed.packageName, parsed.webDomain, 10);

            Intent result = new Intent();
            AutofillResponseFactory.writeAuthResultExtras(this, result, parsed, matches, password);
            runOnUiThread(() -> {
                if (result.hasExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT)) {
                    setResult(RESULT_OK, result);
                } else {
                    setResult(RESULT_CANCELED);
                }
                finish();
            });
        });
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
}
