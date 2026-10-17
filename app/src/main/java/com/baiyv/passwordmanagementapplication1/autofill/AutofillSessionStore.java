package com.baiyv.passwordmanagementapplication1.autofill;
import android.content.Context;
import com.baiyv.passwordmanagementapplication1.MyApplication;
/** Session keys exist in process memory only. Process recreation always requires unlock. */
public final class AutofillSessionStore {
 public AutofillSessionStore(Context context) { context.getSharedPreferences("autofill_session",Context.MODE_PRIVATE).edit().clear().apply(); }
 public void save(String token) { MyApplication.getInstance().setMasterPassword(token); }
 public String getIfNotExpired() { return MyApplication.getInstance().getMasterPassword(); }
 public void clear() { MyApplication.getInstance().clearMasterPassword(); }
}
