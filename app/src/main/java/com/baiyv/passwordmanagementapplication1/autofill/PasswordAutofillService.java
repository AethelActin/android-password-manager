package com.baiyv.passwordmanagementapplication1.autofill;

import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;

import com.baiyv.passwordmanagementapplication1.MyApplication;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordAutofillService extends AutofillService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onFillRequest(FillRequest request, android.os.CancellationSignal cancellationSignal, FillCallback callback) {
        AutofillParsedStructure parsed = AutofillParsedStructure.from(request);
        if (getPackageName().equals(parsed.packageName)) {
            callback.onSuccess(null);
            return;
        }
        if (parsed.usernameId == null && parsed.passwordId == null) {
            callback.onSuccess(null);
            return;
        }

        executor.execute(() -> {
            if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                callback.onSuccess(null);
                return;
            }

            List<PasswordEntry> all = AppDatabase.getInstance(this).passwordDao().getAllPasswords();
            List<PasswordEntry> matches = AutofillVaultMatcher.pickTopMatches(all, parsed.packageName, parsed.webDomain, 10);
            String masterPassword = MyApplication.getInstance().getMasterPassword();
            if (masterPassword == null) {
                masterPassword = new AutofillSessionStore(this).getIfNotExpired();
                if (masterPassword != null) {
                    MyApplication.getInstance().setMasterPassword(masterPassword);
                }
            }

            FillResponse response;
            if (masterPassword != null) {
                response = AutofillResponseFactory.buildUnlockedResponse(this, parsed, matches, masterPassword);
            } else {
                response = AutofillResponseFactory.buildLockedResponse(this, parsed);
            }
            callback.onSuccess(response);
        });
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        callback.onSuccess();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
