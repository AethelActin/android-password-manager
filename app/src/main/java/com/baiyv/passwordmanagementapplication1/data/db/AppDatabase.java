package com.baiyv.passwordmanagementapplication1.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.baiyv.passwordmanagementapplication1.data.model.Category;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;

@Database(entities = {PasswordEntry.class, Category.class, com.baiyv.passwordmanagementapplication1.data.model.VaultMetadata.class}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public static final androidx.room.migration.Migration MIGRATION_1_2 =
            new androidx.room.migration.Migration(1, 2) {
                @Override
                public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS vault_metadata (id INTEGER NOT NULL PRIMARY KEY, envelope TEXT, totpSecret TEXT)");
                }
            };

    public abstract PasswordDao passwordDao();
    public abstract CategoryDao categoryDao();
    public abstract VaultDao vaultDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "password_manager_db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}