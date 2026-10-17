package com.baiyv.passwordmanagementapplication1.utils;
import java.util.concurrent.*;
/** Compatibility bridge for existing synchronous preference reads; costly vault operations use UI workers. */
public final class VaultThread {
 private static final ExecutorService IO=Executors.newCachedThreadPool();
 public static <T> T run(Callable<T> work) {
  try { if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) return work.call(); return IO.submit(work).get(); }
  catch(Exception e) { throw new IllegalStateException("Vault operation failed",e); }
 }
}
