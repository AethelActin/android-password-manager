package com.baiyv.passwordmanagementapplication1.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;

import java.util.ArrayList;
import java.util.List;

final class AutofillResponseFactory {

    private static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    private static final String EXTRA_WEB_DOMAIN = "extra_web_domain";
    private static final String EXTRA_USERNAME_ID = "extra_username_id";
    private static final String EXTRA_PASSWORD_ID = "extra_password_id";

    private AutofillResponseFactory() {}

    @Nullable
    static FillResponse buildUnlockedResponse(
            Context context,
            AutofillParsedStructure parsed,
            List<PasswordEntry> entries,
            String masterPassword
    ) {
        FillResponse.Builder response = new FillResponse.Builder();

        AutofillId usernameId = parsed.usernameId;
        AutofillId passwordId = parsed.passwordId;
        if (usernameId == null && passwordId == null) return null;

        int count = 0;
        for (PasswordEntry entry : entries) {
            if (entry == null) continue;
            Dataset dataset = buildDataset(context, entry, usernameId, passwordId, masterPassword);
            if (dataset != null) {
                response.addDataset(dataset);
                count++;
            }
            if (count >= 10) break;
        }

        if (count == 0) return null;
        return response.build();
    }

    static FillResponse buildLockedResponse(Context context, AutofillParsedStructure parsed) {
        AutofillId usernameId = parsed.usernameId;
        AutofillId passwordId = parsed.passwordId;
        if (usernameId == null && passwordId == null) {
            return null;
        }

        Intent intent = new Intent(context, AutofillAuthActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, parsed.packageName);
        intent.putExtra(EXTRA_WEB_DOMAIN, parsed.webDomain);
        intent.putExtra(EXTRA_USERNAME_ID, usernameId);
        intent.putExtra(EXTRA_PASSWORD_ID, passwordId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        List<AutofillId> ids = new ArrayList<>(2);
        if (usernameId != null) ids.add(usernameId);
        if (passwordId != null) ids.add(passwordId);

        RemoteViews presentation = buildPresentation(
                context,
                context.getString(R.string.autofill_locked_title),
                context.getString(R.string.autofill_locked_subtitle),
                true
        );
        return new FillResponse.Builder()
                .setAuthentication(ids.toArray(new AutofillId[0]), pendingIntent.getIntentSender(), presentation)
                .build();
    }

    static void writeAuthResultExtras(
            Context context,
            Intent resultIntent,
            AutofillParsedStructure parsed,
            List<PasswordEntry> entries,
            String masterPassword
    ) {
        FillResponse response = buildUnlockedResponse(context, parsed, entries, masterPassword);
        if (response == null) return;
        resultIntent.putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, response);
    }

    @Nullable
    static AutofillParsedStructure readFromAuthIntent(Intent intent) {
        if (intent == null) return null;
        String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        String domain = intent.getStringExtra(EXTRA_WEB_DOMAIN);
        AutofillId usernameId = intent.getParcelableExtra(EXTRA_USERNAME_ID);
        AutofillId passwordId = intent.getParcelableExtra(EXTRA_PASSWORD_ID);
        return new AutofillParsedStructure(pkg, domain, usernameId, passwordId);
    }

    @Nullable
    private static Dataset buildDataset(
            Context context,
            PasswordEntry entry,
            @Nullable AutofillId usernameId,
            @Nullable AutofillId passwordId,
            String masterPassword
    ) {
        String username = entry.getUsername();
        String decrypted = CryptoUtils.decrypt(entry.getEncryptedPassword(), masterPassword);
        if (decrypted == null) return null;

        String title = entry.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = entry.getWebsite();
        }
        if (title == null || title.trim().isEmpty()) {
            title = context.getString(R.string.autofill_default_title);
        }

        RemoteViews presentation = buildPresentation(context, title, username == null ? "" : username, false);
        Dataset.Builder dataset = new Dataset.Builder(presentation);

        if (usernameId != null && username != null) {
            dataset.setValue(usernameId, AutofillValue.forText(username));
        }
        if (passwordId != null) {
            dataset.setValue(passwordId, AutofillValue.forText(decrypted));
        }

        return dataset.build();
    }

    private static RemoteViews buildPresentation(Context context, String title, String subtitle, boolean locked) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.autofill_item);
        views.setTextViewText(R.id.tvTitle, title);
        views.setTextViewText(R.id.tvSubtitle, subtitle);
        views.setImageViewResource(R.id.ivIcon, locked ? R.drawable.ic_lock : R.drawable.ic_autofill);
        return views;
    }
}
