package com.baiyv.passwordmanagementapplication1.utils;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.room.Room;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import org.junit.Test;
import static org.junit.Assert.*;
public class VaultMigrationTest {
 private static class MemoryPrefs extends SharedPrefsManager {
  String hash=CryptoUtils.hashPassword("old password");
  String oldTotp=CryptoUtils.encrypt("JBSWY3DPEHPK3PXP","old password");
  MemoryPrefs(){ super(InstrumentationRegistry.getInstrumentation().getContext()); }
  @Override public String getMasterPasswordHash(){ return hash; }
  @Override public void setMasterPasswordHash(String s){ hash=s; }
  @Override public void clearLegacyCredentials(){ hash=null; oldTotp=null; }
  @Override public String getTotpSecretEncrypted(){ return oldTotp; }
 }
 private AppDatabase database(){ return Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getContext(),AppDatabase.class).build(); }
 @Test public void migrationAndPasswordRotationPreserveEntriesAndTotp() throws Exception {
  AppDatabase db=database(); try {
   MemoryPrefs prefs=new MemoryPrefs(); VaultManager vault=new VaultManager(db,prefs);
   PasswordEntry e=new PasswordEntry();e.setEncryptedPassword(CryptoUtils.encrypt("entry","old password")); db.passwordDao().insert(e);
   assertNull(vault.unlock("wrong")); assertNull(db.vaultDao().get());
   String token=vault.unlock("old password");
   assertNull(prefs.hash); assertNull(prefs.oldTotp);
   assertEquals("entry",CryptoUtils.decrypt(db.passwordDao().getAllPasswords().get(0).getEncryptedPassword(),token));
   assertEquals("JBSWY3DPEHPK3PXP",CryptoUtils.decrypt(db.vaultDao().get().totpSecret,token));
   assertEquals(token,vault.changePassword("old password","new password"));
   assertNull(vault.unlock("old password")); assertEquals(token,vault.unlock("new password"));
  } finally { db.close(); }
 }
 @Test public void damagedEntryRollsBackEntireMigration() throws Exception {
  AppDatabase db=database(); try {
   MemoryPrefs prefs=new MemoryPrefs(); VaultManager vault=new VaultManager(db,prefs);
   PasswordEntry good=new PasswordEntry();good.setEncryptedPassword(CryptoUtils.encrypt("entry","old password"));db.passwordDao().insert(good);
   PasswordEntry bad=new PasswordEntry();bad.setEncryptedPassword("broken");db.passwordDao().insert(bad);
   try { vault.unlock("old password"); fail("Expected failure"); } catch(IllegalStateException expected) {}
   assertNull(db.vaultDao().get()); assertNotNull(prefs.hash);
   for(PasswordEntry e:db.passwordDao().getAllPasswords()) if(!"broken".equals(e.getEncryptedPassword())) assertEquals("entry",CryptoUtils.decrypt(e.getEncryptedPassword(),"old password"));
  } finally { db.close(); }
 }

 @Test public void sqliteVersionOneUpgradesWithoutLosingRows() {
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  String name="schema-migration-"+java.util.UUID.randomUUID()+".db";
  AppDatabase upgraded=null;
  java.io.File parent=context.getDatabasePath(name).getParentFile();
  assertNotNull(parent);
  assertTrue("Cannot create dedicated test database directory",parent.isDirectory() || parent.mkdirs());
  try {
   // A dedicated test database: no app singleton or real password vault is opened.
   try(android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase(name,0,null)) {
    old.execSQL("CREATE TABLE passwords (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT, username TEXT, encryptedPassword TEXT, website TEXT, category TEXT, notes TEXT, strengthLevel INTEGER NOT NULL, createTime INTEGER NOT NULL, updateTime INTEGER NOT NULL, isFavorite INTEGER NOT NULL)");
    old.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, icon TEXT, color INTEGER NOT NULL)");
    old.execSQL("INSERT INTO passwords VALUES(7,'fictional title','test-user','fictional ciphertext','example.test','test-category','test-note',3,11,22,1)");
    old.execSQL("INSERT INTO categories VALUES(9,'test-category','test-icon',123)");
    old.setVersion(1);
   }
   upgraded=Room.databaseBuilder(context,AppDatabase.class,name).addMigrations(AppDatabase.MIGRATION_1_2).build();
   // Opening invokes migration and Room's full schema validation.
   assertEquals(2,upgraded.getOpenHelper().getWritableDatabase().getVersion());
   PasswordEntry entry=upgraded.passwordDao().getPasswordById(7);
   assertNotNull(entry); assertEquals("fictional ciphertext",entry.getEncryptedPassword());
   assertEquals("test-user",entry.getUsername()); assertEquals("test-note",entry.getNotes());
   assertEquals(11L,entry.getCreateTime()); assertEquals(22L,entry.getUpdateTime()); assertTrue(entry.isFavorite());
   try(android.database.Cursor cursor=upgraded.getOpenHelper().getReadableDatabase().query("SELECT name,icon,color FROM categories WHERE id=9")) {
    assertTrue(cursor.moveToFirst());assertEquals("test-category",cursor.getString(0));assertEquals("test-icon",cursor.getString(1));assertEquals(123,cursor.getInt(2));
   }
   assertNull(upgraded.vaultDao().get());
  } finally { if(upgraded!=null) upgraded.close(); context.deleteDatabase(name); }
 }
}
