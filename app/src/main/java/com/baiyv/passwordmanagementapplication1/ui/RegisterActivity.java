package com.baiyv.passwordmanagementapplication1.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.utils.Constants;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.PasswordStrengthChecker;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private TextInputEditText etPassword, etConfirmPassword, etPasswordHint;
    private View strengthBar1, strengthBar2, strengthBar3, strengthBar4;
    private TextView tvStrength;
    private Button btnRegister;
    private SharedPrefsManager prefsManager;
    private int appliedThemeResId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = MyApplication.resolveNoActionBarTheme(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        prefsManager = new SharedPrefsManager(this);
        initViews();
        setupListeners();
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

    private void initViews() {
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etPasswordHint = findViewById(R.id.etPasswordHint);
        strengthBar1 = findViewById(R.id.strengthBar1);
        strengthBar2 = findViewById(R.id.strengthBar2);
        strengthBar3 = findViewById(R.id.strengthBar3);
        strengthBar4 = findViewById(R.id.strengthBar4);
        tvStrength = findViewById(R.id.tvStrength);
        btnRegister = findViewById(R.id.btnRegister);
    }

    private void setupListeners() {
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

        btnRegister.setOnClickListener(v -> register());
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

    private void register() {
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // 获取密码提示（可能为null）
        String passwordHint = "";
        if (etPasswordHint != null && etPasswordHint.getText() != null) {
            passwordHint = etPasswordHint.getText().toString().trim();
        }


        // 验证
        if (password.isEmpty()) {
            etPassword.setError(getString(R.string.error_enter_master_password));
            return;
        }

        if (password.length() < 6) {
            etPassword.setError(getString(R.string.error_new_master_password_min6));
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.error_password_mismatch));
            return;
        }

        final String hint=passwordHint;
        btnRegister.setEnabled(false);
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(()-> {
            try {
                String token=new com.baiyv.passwordmanagementapplication1.utils.VaultManager(this).initialize(password);
                prefsManager.setFirstRun(false); prefsManager.setPasswordHint(hint);
                runOnUiThread(()-> {
                    MyApplication.getInstance().setMasterPassword(token);
                    startActivity(new Intent(this,MainActivity.class)); finish();
                });
            } catch(Exception e) { runOnUiThread(()-> { btnRegister.setEnabled(true); Toast.makeText(this,R.string.toast_save_failed_retry,Toast.LENGTH_LONG).show(); }); }
        });
    }
}
