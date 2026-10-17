package com.baiyv.passwordmanagementapplication1.autofill;

import androidx.annotation.Nullable;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import java.net.URI;
import java.net.IDN;
import java.util.*;

/** Only exact hosts or explicitly bound pmapp: package names are eligible. */
final class AutofillVaultMatcher {
    private AutofillVaultMatcher() {}

    static List<PasswordEntry> pickTopMatches(List<PasswordEntry> all, @Nullable String packageName,
                                              @Nullable String webDomain, int limit) {
        if (all == null || limit <= 0) return Collections.emptyList();
        String domain = host(webDomain);
        List<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry entry : all) {
            if (entry == null || entry.getWebsite() == null) continue;
            String website = entry.getWebsite().trim();
            // A browser request must never match its browser package binding.
            boolean matches = webDomain != null && !webDomain.trim().isEmpty()
                    ? domain != null && domain.equals(host(website))
                    : packageName != null && website.equals("pmapp:" + packageName);
            if (matches) result.add(entry);
        }
        result.sort(Comparator.comparingLong(PasswordEntry::getUpdateTime).reversed());
        return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
    }

    @Nullable private static String host(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        if (text.startsWith("pmapp:")) return null;
        try {
            URI uri = new URI(text.contains("://") ? text : "https://" + text);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getHost() == null) return null;
            String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
            return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        } catch (Exception ignored) { return null; }
    }
}
