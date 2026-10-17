package com.baiyv.passwordmanagementapplication1;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.db.PasswordDao;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Disposable in-memory database: never touches the user's vault. */
@RunWith(AndroidJUnit4.class)
public class PasswordCrudTest {
    private AppDatabase db;
    private PasswordDao dao;

    @Before public void open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), AppDatabase.class).build();
        dao = db.passwordDao();
    }
    @After public void close() { db.close(); }

    @Test public void editPreservesMetadataAndPersistsFields() {
        PasswordEntry entry = new PasswordEntry();
        entry.setTitle("Original");
        entry.setCreateTime(123L);
        entry.setFavorite(true);
        entry.setEncryptedPassword("cipher-before");
        int id = (int) dao.insert(entry);
        PasswordEntry loaded = dao.getPasswordById(id);
        loaded.setTitle("Edited");
        loaded.setEncryptedPassword("cipher-after");
        assertEquals(1, dao.update(loaded));
        PasswordEntry edited = dao.getPasswordById(id);
        assertEquals(123L, edited.getCreateTime());
        assertTrue(edited.isFavorite());
        assertEquals("Edited", edited.getTitle());
        assertEquals("cipher-after", edited.getEncryptedPassword());
    }

    @Test public void missingRowsCannotReportSuccessfulMutation() {
        PasswordEntry missing = new PasswordEntry();
        missing.setId(999);
        assertEquals(0, dao.update(missing));
        assertEquals(0, dao.delete(missing));
        assertEquals(0, dao.getPasswordCount());
    }

    @Test public void searchMatchesTitleAndAccountAndUsesRecentOrder() {
        PasswordEntry older = new PasswordEntry();
        older.setTitle("match title"); older.setUsername("other"); older.setUpdateTime(1);
        dao.insert(older);
        PasswordEntry newer = new PasswordEntry();
        newer.setTitle("Other"); newer.setUsername("match account"); newer.setUpdateTime(2);
        dao.insert(newer);
        assertEquals(2, dao.searchPasswords("match").size());
        assertEquals("match account", dao.searchPasswords("match").get(0).getUsername());
        assertTrue(dao.searchPasswords("absent").isEmpty());
        assertEquals(1, dao.delete(dao.getAllPasswords().get(0)));
        assertEquals(1, dao.getPasswordCount());
    }
}
