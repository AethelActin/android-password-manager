package com.baiyv.passwordmanagementapplication1.data.repository;

import android.content.Context;

import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.db.PasswordDao;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordRepository {

    private final PasswordDao passwordDao;
    private final ExecutorService executorService;

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }

    public PasswordRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        passwordDao = database.passwordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    // 批量插入密码
    public void insertAll(List<PasswordEntry> passwords, DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                passwordDao.insertAll(passwords);
                if (callback != null) {
                    callback.onSuccess(null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    /** Recheck at the actual write, not just when a file picker/dialog returned. */
    public void insertAllForSession(List<PasswordEntry> entries, String token, DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                com.baiyv.passwordmanagementapplication1.MyApplication session = com.baiyv.passwordmanagementapplication1.MyApplication.getInstance();
                synchronized (session) {
                    if (token == null || !token.equals(session.getMasterPassword())) {
                        throw new IllegalStateException("Session expired; nothing imported");
                    }
                    passwordDao.insertAll(entries); // The entire batch commits before a concurrent explicit lock.
                }
                callback.onSuccess(null);
            } catch (Exception e) { callback.onError(e); }
        });
    }

    // 删除所有密码
    public void deleteAll(DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                passwordDao.deleteAll();
                if (callback != null) {
                    callback.onSuccess(null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    // 获取所有密码
    public void getAllPasswords(DataCallback<List<PasswordEntry>> callback) {
        executorService.execute(() -> {
            try {
                List<PasswordEntry> passwords = passwordDao.getAllPasswords();
                callback.onSuccess(passwords);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
}