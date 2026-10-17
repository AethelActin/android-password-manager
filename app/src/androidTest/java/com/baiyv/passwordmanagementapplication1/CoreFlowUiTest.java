package com.baiyv.passwordmanagementapplication1;

import android.app.Activity;
import android.content.Context;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.baiyv.passwordmanagementapplication1.data.db.AppDatabase;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.ui.MainActivity;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;
import com.baiyv.passwordmanagementapplication1.utils.VaultManager;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.Map;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** UI runs against a substituted in-memory Room singleton, never the installed vault. */
@RunWith(AndroidJUnit4.class)
public class CoreFlowUiTest {
    private AppDatabase db;
    private AppDatabase previousDb;
    private Field singleton;
    private SharedPreferences prefs;
    private Map<String, ?> previousPrefs;
    private ActivityScenario<MainActivity> scenario;
    private String token;

    @Before public void prepareIsolatedVault() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        singleton = AppDatabase.class.getDeclaredField("INSTANCE");
        singleton.setAccessible(true);
        previousDb = (AppDatabase) singleton.get(null);
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        singleton.set(null, db);
        prefs = context.getSharedPreferences("password_manager_prefs", Context.MODE_PRIVATE);
        previousPrefs = prefs.getAll();
        prefs.edit().clear().putBoolean("is_first_run", false)
                .putBoolean("use_biometric", false).putBoolean("totp_enabled", false)
                .putBoolean("password_view_auth_enabled", false)
                .putInt("auto_lock_time", 0).commit();
        token = new VaultManager(context).initialize("Disposable-ui-password-42!");
        MyApplication.getInstance().setMasterPassword(token);
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After public void releaseIsolatedVault() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : new java.util.ArrayList<>(ActivityLifecycleMonitorRegistry
                    .getInstance().getActivitiesInStage(Stage.RESUMED))) activity.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        if (scenario != null) scenario.close();
        MyApplication.getInstance().clearMasterPassword();
        if (db != null) db.close();
        if (singleton != null) singleton.set(null, previousDb);
        if (prefs != null && previousPrefs != null) {
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : previousPrefs.entrySet()) {
                Object v = entry.getValue(); String k = entry.getKey();
                if (v instanceof String) editor.putString(k, (String) v);
                else if (v instanceof Boolean) editor.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) editor.putInt(k, (Integer) v);
                else if (v instanceof Long) editor.putLong(k, (Long) v);
                else if (v instanceof Float) editor.putFloat(k, (Float) v);
                else if (v instanceof java.util.Set) editor.putStringSet(k, (java.util.Set<String>) v);
            }
            editor.commit();
        }
    }

    @Test public void addValidateSearchAndEditPreserveMetadata() throws Exception {
        onView(withId(R.id.fabAdd)).perform(click());
        onView(isRoot()).perform(reveal(R.id.btnSave));
        onView(withId(R.id.btnSave)).perform(click());
        String required = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.error_enter_title);
        onView(withId(R.id.etTitle)).check(matches(hasErrorText(required)));
        assertEquals(0, db.passwordDao().getPasswordCount());

        fill(R.id.etTitle, "UI unique account");
        fill(R.id.etUsername, "alice@example.test");
        fill(R.id.etPassword, "Original-password-42!");
        fill(R.id.etNotes, "Keep these notes");
        onView(isRoot()).perform(reveal(R.id.btnSave));
        onView(withId(R.id.btnSave)).perform(click());
        onView(isRoot()).perform(awaitView(withId(R.id.etSearch)));
        onView(withId(R.id.etSearch)).perform(replaceText("UI unique"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        onView(isRoot()).perform(awaitView(withText("UI unique account")));
        PasswordEntry created = db.passwordDao().getAllPasswords().get(0);
        int id = created.getId();
        created.setCreateTime(12345L); created.setFavorite(true);
        assertEquals(1, db.passwordDao().update(created));

        onView(withText("UI unique account")).perform(click());
        onView(isRoot()).perform(awaitView(allOf(withId(R.id.btnSave), isEnabled())));
        fill(R.id.etTitle, "UI unique edited");
        fill(R.id.etPassword, "Edited-password-99!");
        onView(isRoot()).perform(reveal(R.id.btnSave));
        onView(withId(R.id.btnSave)).perform(click());
        onView(isRoot()).perform(awaitView(withText("UI unique edited")));
        onView(withId(R.id.etSearch)).check(matches(withText("UI unique")));
        assertEquals(1, db.passwordDao().getPasswordCount());
        PasswordEntry edited = db.passwordDao().getPasswordById(id);
        assertEquals(12345L, edited.getCreateTime());
        assertTrue(edited.isFavorite());
        assertEquals("Keep these notes", edited.getNotes());
        assertEquals("Edited-password-99!", CryptoUtils.decrypt(edited.getEncryptedPassword(), token));
        android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().takeScreenshot();
        assertNotNull("Screenshot must be available", screenshot);
        java.io.File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null);
        if (directory == null) directory = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir();
        assertNotNull("Test external files directory", directory);
        java.io.File image = new java.io.File(directory, "main-smoke.png");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(image)) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { screenshot.recycle(); }
        android.util.Log.i("CoreFlowUiTest", "Screenshot: " + image.getAbsolutePath());
    }

    private static void fill(int id, String text) {
        onView(isRoot()).perform(reveal(id));
        onView(withId(id)).perform(replaceText(text), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    }
    private static ViewAction reveal(int id) {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isRoot(); }
            public String getDescription() { return "reveal field " + id; }
            public void perform(UiController ui, View root) {
                View view = root.findViewById(id);
                assertNotNull(view);
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
                ui.loopMainThreadUntilIdle();
            }
        };
    }
    private static ViewAction awaitView(Matcher<View> matcher) {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isRoot(); }
            public String getDescription() { return "wait for UI state: " + matcher; }
            public void perform(UiController ui, View root) {
                long deadline = android.os.SystemClock.uptimeMillis() + 15000;
                do {
                    // Activity changes replace the root, so inspect the current resumed window.
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)) {
                        if (contains(activity.getWindow().getDecorView(), matcher)) return;
                    }
                    ui.loopMainThreadForAtLeast(25);
                } while (android.os.SystemClock.uptimeMillis() < deadline);
                throw new AssertionError("Timed out waiting for " + matcher);
            }
        };
    }
    private static boolean contains(View view, Matcher<View> matcher) {
        if (matcher.matches(view)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (contains(group.getChildAt(i), matcher)) return true;
        }
        return false;
    }
}
