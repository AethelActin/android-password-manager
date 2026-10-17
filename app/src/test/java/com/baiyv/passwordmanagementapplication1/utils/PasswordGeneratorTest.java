package com.baiyv.passwordmanagementapplication1.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class PasswordGeneratorTest {
    @Test public void chosenClassesAndLengthAreRespected() {
        for (int i = 0; i < 50; i++) {
            String value = PasswordGenerator.generatePassword(20, true, true, true, true);
            assertEquals(20, value.length());
            assertTrue(value.matches(".*[a-z].*"));
            assertTrue(value.matches(".*[A-Z].*"));
            assertTrue(value.matches(".*[0-9].*"));
            assertTrue(value.matches(".*[^a-zA-Z0-9].*"));
        }
    }

    @Test public void digitsOnlyDoesNotIncludeOtherClasses() {
        assertTrue(PasswordGenerator.generatePassword(16, false, false, true, false).matches("[0-9]{16}"));
    }

    @Test public void emptySelectionUsesAllClassesAndShortInputHasSafeFallback() {
        String value = PasswordGenerator.generatePassword(0, false, false, false, false);
        assertEquals(8, value.length());
        assertTrue(value.matches(".*[a-z].*"));
        assertTrue(value.matches(".*[A-Z].*"));
        assertTrue(value.matches(".*[0-9].*"));
    }
}
