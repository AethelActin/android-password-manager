package com.baiyv.passwordmanagementapplication1.utils;
import android.content.Context;
import java.util.Base64;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.*;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
/** All callers perform this work off the UI thread. Metadata and converted entries commit together. */
public final class VaultManager {
    private final AppDatabase db;
    private final SharedPrefsManager prefs;
    public VaultManager(Context context) {
        db=AppDatabase.getInstance(context);
        prefs=new SharedPrefsManager(context);
    }
    VaultManager(AppDatabase db, SharedPrefsManager prefs) {
        this.db=db;
        this.prefs=prefs;
    }
    private static String random(int count) {
        byte[] b=new byte[count];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
    public static String deriveBackupKey(String password,String salt,int rounds) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),rounds,256);
        try {
            return "dek1:"+Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        }
        finally {
            spec.clearPassword();
        }
    }
    private static String wrap(String password,String token) throws Exception {
        String salt=random(16);
        int rounds=600000;
        String cipher=CryptoUtils.encrypt(token,deriveBackupKey(password,salt,rounds));
        if(cipher==null) throw new IllegalStateException("Cannot wrap vault key");
        return "v1$"+rounds+"$"+salt+"$"+cipher;
    }
    private static String unwrap(String password,String envelope) {
        try {
            String[] parts=envelope.split("\\$",4);
            if(parts.length!=4 || !parts[0].equals("v1")) return null;
            int rounds=Integer.parseInt(parts[1]);
            if(rounds<100000 || rounds>2000000) return null;
            String token=CryptoUtils.decrypt(parts[3],deriveBackupKey(password,parts[2],rounds));
            return token!=null && token.startsWith("dek1:") ? token : null;
        }
        catch(Exception e) {
            return null;
        }
    }
    public boolean verifyPassword(String password) {
        return VaultThread.run(()->verifyInternal(password));
    }
    private boolean verifyInternal(String password) {
        VaultMetadata m=db.vaultDao().get();
        if(m!=null) return unwrap(password,m.envelope)!=null;
        String hash=CryptoUtils.hashPassword(password), stored=prefs.getMasterPasswordHash();
        return hash!=null && stored!=null && java.security.MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8),stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public String initialize(String password) throws Exception {
        String token="dek1:"+random(32);
        VaultMetadata m=new VaultMetadata();
        m.envelope=wrap(password,token);
        db.runInTransaction(()-> {
            if(db.vaultDao().get()!=null || db.passwordDao().getPasswordCount()!=0) throw new IllegalStateException("Vault already exists");
            db.vaultDao().put(m);
        });
        return token;
    }
    public String unlock(String password) throws Exception {
        VaultMetadata current=db.vaultDao().get();
        if(current!=null) {
            String token=unwrap(password,current.envelope);
            if(token!=null) prefs.clearLegacyCredentials();
            return token;
        }
        if(!verifyPassword(password)) return null;
        String token="dek1:"+random(32);
        VaultMetadata m=new VaultMetadata();
        m.envelope=wrap(password,token);
        db.runInTransaction(()-> {
            if(db.vaultDao().get()!=null) throw new IllegalStateException("Concurrent unlock; retry");
            for(PasswordEntry e:db.passwordDao().getAllPasswords()) {
                String plain=CryptoUtils.decryptLegacy(e.getEncryptedPassword(),password);
                if(plain==null) throw new IllegalStateException("Entry cannot be decrypted; original vault preserved");
                String enc=CryptoUtils.encrypt(plain,token);
                if(enc==null) throw new IllegalStateException("Encryption failed");
                e.setEncryptedPassword(enc);
            db.passwordDao().update(e);
            }
            String old=prefs.getTotpSecretEncrypted();
            if(old!=null) {
                String secret=CryptoUtils.decryptLegacy(old,password);
                if(secret==null) throw new IllegalStateException("TOTP cannot be decrypted");
                m.totpSecret=CryptoUtils.encrypt(secret,token);
                if(m.totpSecret==null) throw new IllegalStateException("TOTP encryption failed");
            }
            db.vaultDao().put(m);
        });
        prefs.clearLegacyCredentials();
        return token;
    }
    public String changePassword(String oldPassword,String newPassword) throws Exception {
        String token=unlock(oldPassword);
        if(token==null) throw new IllegalArgumentException("Incorrect password");
        String envelope=wrap(newPassword,token);
        db.runInTransaction(()-> {
            VaultMetadata m=db.vaultDao().get();
                if(m==null || unwrap(oldPassword,m.envelope)==null) throw new IllegalStateException("Vault changed; retry");
                m.envelope=envelope;
                db.vaultDao().put(m);
        });
        return token;
    }
}
