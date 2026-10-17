package com.baiyv.passwordmanagementapplication1.utils;

public class PasswordStrengthChecker {

    public static final int STRENGTH_WEAK = 1;
    public static final int STRENGTH_FAIR = 2;
    public static final int STRENGTH_GOOD = 3;
    public static final int STRENGTH_STRONG = 4;

    /**
     * 检查密码强度
     * @param password 密码
     * @return 强度等级 1-4
     */
    public static int checkStrength(String password) {
        if (password == null || password.isEmpty()) {
            return STRENGTH_WEAK;
        }

        int score = 0;

        // 长度评分
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;

        // 包含小写字母
        if (password.matches(".*[a-z].*")) score++;

        // 包含大写字母
        if (password.matches(".*[A-Z].*")) score++;

        // 包含数字
        if (password.matches(".*[0-9].*")) score++;

        // 包含特殊字符
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*")) score++;

        // 没有连续重复字符
        if (!hasRepeatingChars(password)) score++;

        // 转换为等级
        if (score <= 2) return STRENGTH_WEAK;
        if (score <= 4) return STRENGTH_FAIR;
        if (score <= 6) return STRENGTH_GOOD;
        return STRENGTH_STRONG;
    }

    /**
     * 检查是否有连续重复字符
     */
    private static boolean hasRepeatingChars(String password) {
        for (int i = 0; i < password.length() - 2; i++) {
            if (password.charAt(i) == password.charAt(i + 1)
                    && password.charAt(i) == password.charAt(i + 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取强度描述
     */
    public static String getStrengthText(int level) {
        switch (level) {
            case STRENGTH_WEAK: return "弱";
            case STRENGTH_FAIR: return "一般";
            case STRENGTH_GOOD: return "良好";
            case STRENGTH_STRONG: return "强";
            default: return "未知";
        }
    }

    /**
     * 获取强度对应的颜色
     */
    public static int getStrengthColor(int level) {
        switch (level) {
            case STRENGTH_WEAK: return 0xFFE53935;    // 红色
            case STRENGTH_FAIR: return 0xFFFF9800;    // 橙色
            case STRENGTH_GOOD: return 0xFF4CAF50;    // 绿色
            case STRENGTH_STRONG: return 0xFF2196F3;  // 蓝色
            default: return 0xFF9E9E9E;               // 灰色
        }
    }
}