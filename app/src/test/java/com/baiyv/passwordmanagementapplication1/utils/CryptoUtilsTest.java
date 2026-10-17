package com.baiyv.passwordmanagementapplication1.utils;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Base64;
public class CryptoUtilsTest {
 @Test public void tokenEncryptsUnicodeAndEmptyValues() {
  String key="dek1:"+Base64.getEncoder().encodeToString(new byte[32]);
  assertEquals("密码🔐",CryptoUtils.decrypt(CryptoUtils.encrypt("密码🔐",key),key));
  assertEquals("",CryptoUtils.decrypt(CryptoUtils.encrypt("",key),key));
 }
 @Test public void randomIvAndAuthenticatedFailure() {
  String a=CryptoUtils.encrypt("secret","old password");
  assertNotEquals(a,CryptoUtils.encrypt("secret","old password"));
  assertNull(CryptoUtils.decrypt(a,"wrong password"));
  byte[] bytes=Base64.getDecoder().decode(a); bytes[bytes.length-1]^=1;
  assertNull(CryptoUtils.decrypt(Base64.getEncoder().encodeToString(bytes),"old password"));
  assertNull(CryptoUtils.decrypt("bad","password"));
 }
 @Test public void legacyPrefixPasswordUsesSha256() throws Exception {
  String raw="dek1:not-a-key";
  byte[] key=java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"));
  byte[] encrypted=cipher.doFinal("legacy".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  byte[] combined=new byte[12+encrypted.length];System.arraycopy(cipher.getIV(),0,combined,0,12);System.arraycopy(encrypted,0,combined,12,encrypted.length);
  assertEquals("legacy",CryptoUtils.decryptLegacy(Base64.getEncoder().encodeToString(combined),raw));
 }
 @Test public void legacyCiphertextRemainsReadable() {
  assertEquals("secret",CryptoUtils.decrypt(CryptoUtils.encrypt("secret","legacy"),"legacy"));
 }
 @Test public void kdfSaltChangesKeyAndIsDeterministic() throws Exception {
  String a=Base64.getEncoder().encodeToString(new byte[16]);
  byte[] salt=new byte[16]; salt[0]=1; String b=Base64.getEncoder().encodeToString(salt);
  String key=VaultManager.deriveBackupKey("password",a,210000);
  assertEquals(key,VaultManager.deriveBackupKey("password",a,210000));
  assertNotEquals(key,VaultManager.deriveBackupKey("password",b,210000));
 }
}
