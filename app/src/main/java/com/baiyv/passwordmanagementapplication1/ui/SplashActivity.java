package com.baiyv.passwordmanagementapplication1.ui;


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2秒

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(MyApplication.resolveSplashTheme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            SharedPrefsManager prefsManager = new SharedPrefsManager(this);

            Intent intent;
            if (prefsManager.isFirstRun()) {
                // 首次运行，跳转到注册页面设置主密码
                intent = new Intent(this, RegisterActivity.class);
            } else {
                // 非首次运行，跳转到登录页面
                intent = new Intent(this, LoginActivity.class);
            }

            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
    }
}