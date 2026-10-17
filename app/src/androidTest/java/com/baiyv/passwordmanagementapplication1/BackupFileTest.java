package com.baiyv.passwordmanagementapplication1;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.utils.BackupHelper;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import org.junit.Test;
import java.io.File;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Real ContentResolver file I/O, using a disposable test-APK cache file and synthetic keys. */
public class BackupFileTest {
    @Test public void fileRestoresToAnotherVaultAndLockedExportDoesNotTruncateIt() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = File.createTempFile("disposable-backup-", ".json", context.getCacheDir());
        String keyA = "dek1:" + java.util.Base64.getEncoder().encodeToString(new byte[32]);
        byte[] bytesB = new byte[32]; bytesB[0] = 1;
        String keyB = "dek1:" + java.util.Base64.getEncoder().encodeToString(bytesB);
        try {
            PasswordEntry entry = new PasswordEntry();
            entry.setTitle("Synthetic file backup");
            entry.setUsername("demo-user");
            entry.setEncryptedPassword(CryptoUtils.encrypt("demo-secret", keyA));
            MyApplication.getInstance().setMasterPassword(keyA);
            BackupHelper.exportPasswords(context, Uri.fromFile(file), Collections.singletonList(entry), keyA, "independent-backup-pass");
            long exportedSize = file.length();
            assertTrue(exportedSize > 0);
            MyApplication.getInstance().clearMasterPassword();
            try {
                BackupHelper.exportPasswords(context, Uri.fromFile(file), Collections.singletonList(entry), keyA, "independent-backup-pass");
                fail("Locked export must be rejected before opening/truncating the file");
            } catch (Exception expected) { assertEquals(exportedSize, file.length()); }
            List<PasswordEntry> restored = BackupHelper.importPasswords(context, Uri.fromFile(file), "independent-backup-pass", keyB);
            assertEquals(1, restored.size());
            assertEquals("demo-secret", CryptoUtils.decrypt(restored.get(0).getEncryptedPassword(), keyB));
            assertNull(CryptoUtils.decrypt(restored.get(0).getEncryptedPassword(), keyA));
        } finally {
            MyApplication.getInstance().clearMasterPassword();
            assertTrue(file.delete());
        }
    }
}
