package com.baiyv.passwordmanagementapplication1.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.utils.PasswordGenerator;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;

public class PasswordGeneratorActivity extends AppCompatActivity {

    private TextView tvGeneratedPassword, tvLength;
    private int appliedThemeResId;
    private Slider sliderLength;
    private Chip chipLowercase, chipUppercase, chipDigits, chipSpecial;
    private Button btnCopy, btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = MyApplication.resolveNoActionBarTheme(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_generator);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.password_generator);
        }

        initViews();
        setupListeners();
        generatePassword();
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
        tvGeneratedPassword = findViewById(R.id.tvGeneratedPassword);
        tvLength = findViewById(R.id.tvLength);
        sliderLength = findViewById(R.id.sliderLength);
        chipLowercase = findViewById(R.id.chipLowercase);
        chipUppercase = findViewById(R.id.chipUppercase);
        chipDigits = findViewById(R.id.chipDigits);
        chipSpecial = findViewById(R.id.chipSpecial);
        btnCopy = findViewById(R.id.btnCopy);
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void setupListeners() {
        sliderLength.addOnChangeListener((slider, value, fromUser) -> {
            tvLength.setText(String.valueOf((int) value));
        });

        btnRefresh.setOnClickListener(v -> generatePassword());

        btnCopy.setOnClickListener(v -> {
            String password = tvGeneratedPassword.getText().toString();
            if (!password.isEmpty() && !password.equals(getString(R.string.generator_tap_to_generate))) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("password", password);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generatePassword() {
        int length = (int) sliderLength.getValue();
        boolean useLowercase = chipLowercase.isChecked();
        boolean useUppercase = chipUppercase.isChecked();
        boolean useDigits = chipDigits.isChecked();
        boolean useSpecial = chipSpecial.isChecked();

        String password = PasswordGenerator.generatePassword(length, useLowercase, useUppercase, useDigits, useSpecial);
        tvGeneratedPassword.setText(password);
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
