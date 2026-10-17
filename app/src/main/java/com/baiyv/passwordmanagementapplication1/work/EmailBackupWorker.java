package com.baiyv.passwordmanagementapplication1.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.autofill.AutofillSessionStore;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.network.ApiService;
import com.baiyv.passwordmanagementapplication1.network.RetrofitClient;
import com.baiyv.passwordmanagementapplication1.utils.BackupHelper;
import com.baiyv.passwordmanagementapplication1.utils.SharedPrefsManager;

import java.util.List;

import retrofit2.Response;

public class EmailBackupWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "timed_email_backup";

    public EmailBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Cancelled legacy jobs must never send credentials or backups.
        return Result.success();
    }
}