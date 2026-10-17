package com.baiyv.passwordmanagementapplication1.utils;

import android.content.Context;
import android.net.Uri;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Version 3 keeps logical passwords inside one authenticated encrypted envelope. */
public class BackupHelper {
    public static final String ERR_MASTER_PASSWORD_EMPTY = "err_master_password_empty";
    public static final String ERR_ENCRYPT_FAILED = "err_encrypt_failed";
    public static final String ERR_OPEN_OUTPUT_STREAM_FAILED = "err_open_output_stream_failed";
    public static final String ERR_IMPORT_NEEDS_MASTER_PASSWORD = "err_import_needs_master_password";
    public static final String ERR_IMPORT_MASTER_PASSWORD_INCORRECT_OR_CORRUPT = "err_import_master_password_incorrect_or_corrupt";
    public static final String ERR_INVALID_BACKUP = "err_invalid_backup";
    public static final String ERR_UNSUPPORTED_VERSION = "err_unsupported_backup_version";
    public static final String ERR_BACKUP_TOO_LARGE = "err_backup_too_large";
    private static final String MAGIC = "PM_BACKUP";
    private static final int VERSION = 3;
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10000;
    private static final Gson GSON = new Gson();

    public static void exportPasswords(Context context, Uri uri, List<PasswordEntry> passwords, String password) throws Exception {
        exportPasswords(context, uri, passwords, password, password);
    }

    public static void exportPasswords(Context context, Uri uri, List<PasswordEntry> passwords, String currentToken, String backupPassword) throws Exception {
        byte[] bytes = exportPasswordsToString(passwords, currentToken, backupPassword).getBytes(StandardCharsets.UTF_8);
        com.baiyv.passwordmanagementapplication1.MyApplication session = com.baiyv.passwordmanagementapplication1.MyApplication.getInstance();
        synchronized (session) {
            if (!currentToken.equals(session.getMasterPassword())) throw new Exception(ERR_IMPORT_NEEDS_MASTER_PASSWORD);
            try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new Exception(ERR_OPEN_OUTPUT_STREAM_FAILED);
                output.write(bytes);
            }
        }
    }

    public static String exportPasswordsToString(List<PasswordEntry> passwords, String password) throws Exception {
        return exportPasswordsToString(passwords, password, password);
    }

    public static String exportPasswordsToString(List<PasswordEntry> passwords, String currentToken, String backupPassword) throws Exception {
        requirePassword(currentToken);
        requirePassword(backupPassword);
        if (passwords == null || passwords.size() > MAX_ENTRIES) throw new Exception(ERR_INVALID_BACKUP);
        JsonArray rows = new JsonArray();
        for (PasswordEntry entry : passwords) {
            validate(entry);
            String plain = CryptoUtils.decrypt(entry.getEncryptedPassword(), currentToken);
            if (plain == null || plain.isEmpty()) throw new Exception(ERR_IMPORT_MASTER_PASSWORD_INCORRECT_OR_CORRUPT);
            JsonObject row = GSON.toJsonTree(entry).getAsJsonObject();
            row.remove("encryptedPassword");
            row.remove("id");
            row.addProperty("password", plain);
            rows.add(row);
        }
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        String cipher = CryptoUtils.encrypt(rows.toString(), VaultManager.deriveBackupKey(backupPassword, java.util.Base64.getEncoder().encodeToString(salt), 600000));
        if (cipher == null) throw new Exception(ERR_ENCRYPT_FAILED);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("magic", MAGIC);
        envelope.addProperty("version", VERSION);
        envelope.addProperty("salt", java.util.Base64.getEncoder().encodeToString(salt));
        envelope.addProperty("rounds", 600000);
        envelope.addProperty("cipherText", cipher);
        String text = envelope.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new Exception(ERR_BACKUP_TOO_LARGE);
        return text;
    }

    public static List<PasswordEntry> importPasswords(Context context, Uri uri, String password) throws Exception {
        return importPasswords(context, uri, password, password);
    }

    public static List<PasswordEntry> importPasswords(Context context, Uri uri, String backupPassword, String currentPassword) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new Exception(ERR_INVALID_BACKUP);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_BYTES) throw new Exception(ERR_BACKUP_TOO_LARGE);
                output.write(buffer, 0, count);
            }
            return importPasswordsFromString(output.toString("UTF-8"), backupPassword, currentPassword);
        }
    }

    public static List<PasswordEntry> importPasswordsFromString(String text, String password) throws Exception {
        return importPasswordsFromString(text, password, password);
    }

    /** Validates the entire file before returning anything for database insertion. */
    public static List<PasswordEntry> importPasswordsFromString(String text, String backupPassword, String currentPassword) throws Exception {
        requirePassword(backupPassword);
        requirePassword(currentPassword);
        if (text == null || text.trim().isEmpty()) throw new Exception(ERR_INVALID_BACKUP);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new Exception(ERR_BACKUP_TOO_LARGE);
        try {
            JsonObject envelope = JsonParser.parseString(text).getAsJsonObject();
            if (!MAGIC.equals(envelope.get("magic").getAsString())) throw new Exception(ERR_INVALID_BACKUP);
            int version = envelope.get("version").getAsInt();
            if (version != 2 && version != VERSION) throw new Exception(ERR_UNSUPPORTED_VERSION);
            String decryptKey = backupPassword;
            if (version == VERSION) {
                byte[] salt = java.util.Base64.getDecoder().decode(envelope.get("salt").getAsString());
                int rounds = envelope.get("rounds").getAsInt();
                if (salt.length != 16 || (rounds != 210000 && rounds != 600000)) throw new Exception(ERR_INVALID_BACKUP);
                decryptKey = VaultManager.deriveBackupKey(backupPassword, java.util.Base64.getEncoder().encodeToString(salt), rounds);
            }
            String plain = version == 2
                    ? CryptoUtils.decryptLegacy(envelope.get("cipherText").getAsString(), backupPassword)
                    : CryptoUtils.decrypt(envelope.get("cipherText").getAsString(), decryptKey);
            if (plain == null) throw new Exception(ERR_IMPORT_MASTER_PASSWORD_INCORRECT_OR_CORRUPT);
            JsonArray rows = JsonParser.parseString(plain).getAsJsonArray();
            if (rows.size() > MAX_ENTRIES) throw new Exception(ERR_BACKUP_TOO_LARGE);
            List<PasswordEntry> result = new ArrayList<>();
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                PasswordEntry entry = GSON.fromJson(row, PasswordEntry.class);
                String secret;
                if (version == 2) {
                    validate(entry);
                    secret = CryptoUtils.decryptLegacy(entry.getEncryptedPassword(), backupPassword);
                } else {
                    secret = row.get("password").getAsString();
                }
                if (secret == null || secret.isEmpty() || secret.length() > 65536) throw new Exception(ERR_INVALID_BACKUP);
                entry.setEncryptedPassword(CryptoUtils.encrypt(secret, currentPassword));
                entry.setId(0);
                validate(entry);
                result.add(entry);
            }
            return result;
        } catch (JsonParseException | IllegalStateException | NullPointerException | IllegalArgumentException e) {
            throw new Exception(ERR_INVALID_BACKUP, e);
        }
    }

    private static void requirePassword(String password) throws Exception {
        if (password == null || password.isEmpty()) throw new Exception(ERR_IMPORT_NEEDS_MASTER_PASSWORD);
    }

    private static void validate(PasswordEntry entry) throws Exception {
        if (entry == null || entry.getTitle() == null || entry.getTitle().trim().isEmpty()
                || entry.getTitle().length() > 1000 || entry.getEncryptedPassword() == null
                || entry.getEncryptedPassword().isEmpty() || entry.getCreateTime() < 0 || entry.getUpdateTime() < 0
                || tooLong(entry.getUsername()) || tooLong(entry.getWebsite()) || tooLong(entry.getCategory()) || tooLong(entry.getNotes())) {
            throw new Exception(ERR_INVALID_BACKUP);
        }
    }

    private static boolean tooLong(String value) { return value != null && value.length() > 65536; }
}
