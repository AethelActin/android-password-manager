package com.baiyv.passwordmanagementapplication1.autofill;

import android.service.autofill.FillRequest;
import android.service.autofill.FillContext;
import android.app.assist.AssistStructure;
import android.os.Build;
import android.text.InputType;
import android.util.Pair;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class AutofillParsedStructure {

    @Nullable
    final String packageName;

    @Nullable
    final String webDomain;

    @Nullable
    final AutofillId usernameId;

    @Nullable
    final AutofillId passwordId;

    AutofillParsedStructure(
            @Nullable String packageName,
            @Nullable String webDomain,
            @Nullable AutofillId usernameId,
            @Nullable AutofillId passwordId
    ) {
        this.packageName = packageName;
        this.webDomain = webDomain;
        this.usernameId = usernameId;
        this.passwordId = passwordId;
    }

    static AutofillParsedStructure from(FillRequest request) {
        String pkg = null;
        String domain = null;
        AutofillId bestUsernameId = null;
        int bestUsernameScore = Integer.MIN_VALUE;
        AutofillId bestPasswordId = null;
        int bestPasswordScore = Integer.MIN_VALUE;
        AutofillId firstTextFieldId = null;

        if (request == null || request.getFillContexts() == null || request.getFillContexts().isEmpty()) {
            return new AutofillParsedStructure(null, null, null, null);
        }

        FillContext latestContext = request.getFillContexts().get(request.getFillContexts().size() - 1);
        AssistStructure structure = latestContext.getStructure();
        if (structure == null) {
            return new AutofillParsedStructure(null, null, null, null);
        }

        if (structure.getActivityComponent() != null) {
            pkg = structure.getActivityComponent().getPackageName();
        }

        for (int w = 0; w < structure.getWindowNodeCount(); w++) {
            AssistStructure.WindowNode window = structure.getWindowNodeAt(w);
            if (window == null) continue;
            AssistStructure.ViewNode root = window.getRootViewNode();
            if (root == null) continue;

            Deque<AssistStructure.ViewNode> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                AssistStructure.ViewNode node = stack.pop();
                if (node == null) continue;

                if (domain == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String d = node.getWebDomain();
                    if (d != null && !d.trim().isEmpty()) {
                        domain = d.trim();
                    }
                }

                if (isCandidateTextField(node)) {
                    if (firstTextFieldId == null) {
                        firstTextFieldId = node.getAutofillId();
                    }

                    int usernameScore = scoreUsername(node);
                    if (usernameScore > bestUsernameScore) {
                        bestUsernameScore = usernameScore;
                        bestUsernameId = node.getAutofillId();
                    }

                    int passwordScore = scorePassword(node);
                    if (passwordScore > bestPasswordScore) {
                        bestPasswordScore = passwordScore;
                        bestPasswordId = node.getAutofillId();
                    }
                }

                for (int i = node.getChildCount() - 1; i >= 0; i--) {
                    AssistStructure.ViewNode child = node.getChildAt(i);
                    if (child != null) stack.push(child);
                }
            }
        }

        if (bestPasswordScore < 80) bestPasswordId = null;

        if (bestPasswordId != null && bestUsernameId != null && bestUsernameId.equals(bestPasswordId)) {
            bestUsernameId = null;
        }

        if (bestPasswordId != null) {
            if (bestUsernameId == null) {
                if (firstTextFieldId != null && !firstTextFieldId.equals(bestPasswordId)) {
                    bestUsernameId = firstTextFieldId;
                }
            }
        } else {
            if (bestUsernameScore < 0) bestUsernameId = null;
        }

        if (bestUsernameId == null && bestPasswordId == null) {
            return new AutofillParsedStructure(pkg, domain, null, null);
        }

        return new AutofillParsedStructure(pkg, domain, bestUsernameId, bestPasswordId);
    }

    private static boolean isCandidateTextField(AssistStructure.ViewNode node) {
        if (node.getAutofillId() == null) return false;
        int type = node.getAutofillType();
        if (type != View.AUTOFILL_TYPE_TEXT) return false;
        if (!node.isEnabled()) return false;

        CharSequence cls = node.getClassName();
        if (cls != null) {
            String s = cls.toString();
            if (s.contains("EditText") || s.contains("TextInput") || s.contains("AutoComplete")) {
                return true;
            }
        }

        return node.getInputType() != 0;
    }

    private static int scoreUsername(AssistStructure.ViewNode node) {
        int score = -100;

        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String h : hints) {
                if (h == null) continue;
                if (View.AUTOFILL_HINT_USERNAME.equals(h) || View.AUTOFILL_HINT_EMAIL_ADDRESS.equals(h)) {
                    score += 200;
                }
                if (View.AUTOFILL_HINT_PHONE.equals(h)) {
                    score += 120;
                }
            }
        }

        int inputType = node.getInputType();
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        int klass = inputType & InputType.TYPE_MASK_CLASS;
        if (klass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                score += 160;
            }
            if (variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) {
                score += 60;
            }
            if (variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
                score += 140;
            }
        }

        if (klass == InputType.TYPE_CLASS_PHONE) {
            score += 100;
        }

        String idEntry = node.getIdEntry();
        if (idEntry != null) {
            String t = idEntry.toLowerCase();
            if (t.contains("user") || t.contains("login") || t.contains("account") || t.contains("email") || t.contains("phone")) {
                score += 80;
            }
        }

        CharSequence hint = node.getHint();
        if (hint != null) {
            String ht = hint.toString();
            if (containsAnyIgnoreCase(ht, "用户名", "账号", "帐号", "邮箱", "email", "手机", "phone")) {
                score += 80;
            }
        }

        ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null) {
            String tag = htmlInfo.getTag();
            if (tag != null && tag.equalsIgnoreCase("input")) {
                List<Pair<String, String>> attrs = htmlInfo.getAttributes();
                if (attrs != null) {
                    for (Pair<String, String> p : attrs) {
                        if (p == null || p.first == null || p.second == null) continue;
                        String n = p.first.toLowerCase();
                        String v = p.second.toLowerCase();
                        if (n.equals("type") && v.equals("email")) score += 140;
                        if (n.equals("name") || n.equals("id") || n.equals("autocomplete")) {
                            if (v.contains("user") || v.contains("login") || v.contains("email")) score += 80;
                        }
                    }
                }
            }
        }

        return score;
    }

    private static int scorePassword(AssistStructure.ViewNode node) {
        int score = -100;

        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String h : hints) {
                if (h == null) continue;
                if (View.AUTOFILL_HINT_PASSWORD.equals(h)) {
                    score += 250;
                }
            }
        }

        int inputType = node.getInputType();
        int klass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (klass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                score += 220;
            }
        }
        if (klass == InputType.TYPE_CLASS_NUMBER) {
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                score += 180;
            }
        }

        String idEntry = node.getIdEntry();
        if (idEntry != null) {
            String t = idEntry.toLowerCase();
            if (t.contains("pass") || t.contains("pwd") || t.contains("password")) {
                score += 120;
            }
        }

        CharSequence hint = node.getHint();
        if (hint != null) {
            String ht = hint.toString();
            if (containsAnyIgnoreCase(ht, "密码", "pass", "pwd", "password")) {
                score += 120;
            }
        }

        ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null) {
            String tag = htmlInfo.getTag();
            if (tag != null && tag.equalsIgnoreCase("input")) {
                List<Pair<String, String>> attrs = htmlInfo.getAttributes();
                if (attrs != null) {
                    for (Pair<String, String> p : attrs) {
                        if (p == null || p.first == null || p.second == null) continue;
                        String n = p.first.toLowerCase();
                        String v = p.second.toLowerCase();
                        if (n.equals("type") && v.equals("password")) score += 220;
                        if (n.equals("name") || n.equals("id") || n.equals("autocomplete")) {
                            if (v.contains("pass") || v.contains("pwd") || v.contains("password")) score += 120;
                        }
                    }
                }
            }
        }

        return score;
    }

    private static boolean containsAnyIgnoreCase(String text, String... needles) {
        if (text == null) return false;
        String t = text.toLowerCase();
        for (String n : needles) {
            if (n == null) continue;
            if (t.contains(n.toLowerCase())) return true;
        }
        return false;
    }
}

