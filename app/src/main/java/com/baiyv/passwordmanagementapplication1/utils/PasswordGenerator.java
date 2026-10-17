package com.baiyv.passwordmanagementapplication1.utils;


import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PasswordGenerator {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?";

    private static final SecureRandom random = new SecureRandom();

    /**
     * 生成随机密码
     * @param length 密码长度
     * @param useLowercase 是否包含小写字母
     * @param useUppercase 是否包含大写字母
     * @param useDigits 是否包含数字
     * @param useSpecial 是否包含特殊字符
     * @return 生成的密码
     */
    public static String generatePassword(int length, boolean useLowercase,
                                          boolean useUppercase, boolean useDigits, boolean useSpecial) {

        if (length < 4) {
            length = 8; // 最小长度
        }

        StringBuilder charPool = new StringBuilder();
        List<Character> password = new ArrayList<>();

        // 确保每种选中的字符类型至少有一个
        if (useLowercase) {
            charPool.append(LOWERCASE);
            password.add(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        }
        if (useUppercase) {
            charPool.append(UPPERCASE);
            password.add(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        }
        if (useDigits) {
            charPool.append(DIGITS);
            password.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        if (useSpecial) {
            charPool.append(SPECIAL);
            password.add(SPECIAL.charAt(random.nextInt(SPECIAL.length())));
        }

        // 如果没有选择任何类型，默认使用所有类型
        if (charPool.length() == 0) {
            charPool.append(LOWERCASE).append(UPPERCASE).append(DIGITS).append(SPECIAL);
            password.add(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
            password.add(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
            password.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
            password.add(SPECIAL.charAt(random.nextInt(SPECIAL.length())));
        }

        // 填充剩余长度
        String pool = charPool.toString();
        while (password.size() < length) {
            password.add(pool.charAt(random.nextInt(pool.length())));
        }

        // 打乱顺序
        Collections.shuffle(password, random);

        // 转换为字符串
        StringBuilder result = new StringBuilder();
        for (Character c : password) {
            result.append(c);
        }

        return result.toString();
    }

    /**
     * 生成默认强密码（16位，包含所有类型）
     */
    public static String generateStrongPassword() {
        return generatePassword(16, true, true, true, true);
    }

    /**
     * 生成PIN码
     */
    public static String generatePIN(int length) {
        StringBuilder pin = new StringBuilder();
        for (int i = 0; i < length; i++) {
            pin.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return pin.toString();
    }
}