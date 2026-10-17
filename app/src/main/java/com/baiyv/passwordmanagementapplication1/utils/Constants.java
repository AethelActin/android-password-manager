package com.baiyv.passwordmanagementapplication1.utils;

public class Constants {

    // Intent 额外数据键名
    public static final String EXTRA_MASTER_PASSWORD = "master_password";
    public static final String EXTRA_PASSWORD_ID = "password_id";

    // 请求码
    public static final int REQUEST_ADD_PASSWORD = 1001;
    public static final int REQUEST_EDIT_PASSWORD = 1002;

    // 自动锁定时间选项（分钟）
    public static final int[] AUTO_LOCK_OPTIONS = {1, 5, 15, 30, 60};

    // 密码相关
    public static final int MIN_MASTER_PASSWORD_LENGTH = 6;
    public static final int DEFAULT_PASSWORD_LENGTH = 16;
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 32;

    // 剪贴板清除时间（毫秒）
    public static final int CLIPBOARD_CLEAR_DELAY = 30000; // 30秒

    public static final String TARGET_PREFIX_APP = "pmapp:";

    public static final String BASE_URL = "https://example.invalid/"; // Offline edition: no requests permitted.
}
