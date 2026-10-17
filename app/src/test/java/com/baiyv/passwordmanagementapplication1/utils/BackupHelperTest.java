package com.baiyv.passwordmanagementapplication1.utils;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class BackupHelperTest {
 private PasswordEntry entry(String key) { PasswordEntry e=new PasswordEntry(); e.setId(42); e.setTitle("Example"); e.setUsername("user"); e.setFavorite(true); e.setEncryptedPassword(CryptoUtils.encrypt("secret",key)); return e; }
 private void rejected(String text,String password) throws Exception { try { BackupHelper.importPasswordsFromString(text,password,"new"); fail("accepted invalid backup"); } catch(Exception expected) { assertNotNull(expected.getMessage()); } }
 @Test public void restoresUnderDifferentCurrentPassword() throws Exception { String text=BackupHelper.exportPasswordsToString(Arrays.asList(entry("old")),"old","backup"); assertFalse(text.contains("secret")); PasswordEntry restored=BackupHelper.importPasswordsFromString(text,"backup","new").get(0); assertEquals("secret",CryptoUtils.decrypt(restored.getEncryptedPassword(),"new")); assertEquals(0,restored.getId()); assertTrue(restored.isFavorite()); rejected(text,"wrong"); }
 @Test public void oldV2ReencryptsInnerPasswords() throws Exception { JsonObject env=new JsonObject(); env.addProperty("magic","PM_BACKUP"); env.addProperty("version",2); env.addProperty("cipherText",CryptoUtils.encrypt(new Gson().toJson(Arrays.asList(entry("old"))),"old")); PasswordEntry restored=BackupHelper.importPasswordsFromString(env.toString(),"old","new").get(0); assertEquals("secret",CryptoUtils.decrypt(restored.getEncryptedPassword(),"new")); }
 @Test public void rejectsMalformedUnknownAndUnwrapped() throws Exception { rejected("", "x"); rejected("[]","x"); rejected("{}","x"); rejected("{\"magic\":\"PM_BACKUP\",\"version\":99}","x"); }
 @Test public void rejectsCorruptEntryWithoutPartialReturn() throws Exception { PasswordEntry bad=entry("wrong"); try { BackupHelper.exportPasswordsToString(Arrays.asList(entry("old"),bad),"old","backup"); fail(); } catch(Exception expected) {} }
 @Test public void emptyVaultRoundTrips() throws Exception { assertTrue(BackupHelper.importPasswordsFromString(BackupHelper.exportPasswordsToString(Collections.emptyList(),"old","backup"),"backup","new").isEmpty()); }
 @Test public void rejectsOversizedInput() throws Exception { char[] large=new char[10*1024*1024+1]; Arrays.fill(large,'x'); rejected(new String(large),"x"); }
}
