package com.baiyv.passwordmanagementapplication1.autofill;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class AutofillVaultMatcherTest {
 private PasswordEntry row(String website) { PasswordEntry e=new PasswordEntry(); e.setWebsite(website); e.setTitle("example.com"); e.setFavorite(true); return e; }
 @Test public void unrelatedFavoriteAndTitleCannotGrantAccess() { assertTrue(AutofillVaultMatcher.pickTopMatches(Arrays.asList(row("https://other.test")),"com.browser","example.com",5).isEmpty()); }
 @Test public void hostMatchingRejectsSubstringAndSubdomains() { List<PasswordEntry> list=Arrays.asList(row("https://example.com.evil.test"),row("https://evil.test/example.com"),row("https://sub.example.com"),row("https://EXAMPLE.com/login")); assertEquals(1,AutofillVaultMatcher.pickTopMatches(list,null,"example.com",5).size()); }
 @Test public void packageNeedsExplicitExactBinding() { List<PasswordEntry> list=Arrays.asList(row("com.demo"),row("pmapp:com.demo.evil"),row("pmapp:com.demo")); assertEquals(1,AutofillVaultMatcher.pickTopMatches(list,"com.demo",null,5).size()); assertTrue(AutofillVaultMatcher.pickTopMatches(list,"com.demo","unknown.test",5).isEmpty()); }
 @Test public void absentSourceNeverReturnsAll() { assertTrue(AutofillVaultMatcher.pickTopMatches(Arrays.asList(row("https://example.com")),null,null,5).isEmpty()); }
 @Test public void invalidHostAndUserInfoFailClosed() { assertTrue(AutofillVaultMatcher.pickTopMatches(Arrays.asList(row("https://example.com")),null,"https://evil@example.com",5).isEmpty()); }
}
