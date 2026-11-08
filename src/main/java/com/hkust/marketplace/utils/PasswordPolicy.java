package com.hkust.marketplace.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Password strength rules shared by every registration and password-change flow
 * (see the "Special Requirements" of the Register As Buyer and Register As Seller
 * use-case specifications).
 *
 * <p>A password is accepted only if it satisfies <strong>all</strong> of these rules:</p>
 * <ul>
 *   <li>at least {@value #MIN_LENGTH} characters long;</li>
 *   <li>at least one uppercase letter;</li>
 *   <li>at least one lowercase letter;</li>
 *   <li>at least one digit.</li>
 * </ul>
 *
 * <p>Length is counted in Unicode code points, so a character outside the Basic
 * Multilingual Plane (for example an emoji) counts as one character. Letters and
 * digits are classified with {@link Character}, so accented letters such as
 * {@code 'É'} count as uppercase letters.</p>
 */
public final class PasswordPolicy {

    /** Minimum number of characters a password must contain. */
    public static final int MIN_LENGTH = 8;

    /** Rule description reported when the password is too short. */
    public static final String RULE_MIN_LENGTH = "at least " + MIN_LENGTH + " characters";

    /** Rule description reported when the password has no uppercase letter. */
    public static final String RULE_UPPERCASE = "at least one uppercase letter";

    /** Rule description reported when the password has no lowercase letter. */
    public static final String RULE_LOWERCASE = "at least one lowercase letter";

    /** Rule description reported when the password has no digit. */
    public static final String RULE_DIGIT = "at least one digit";

    /** Every rule description, in the order the rules are checked. */
    private static final List<String> ALL_RULES =
            List.of(RULE_MIN_LENGTH, RULE_UPPERCASE, RULE_LOWERCASE, RULE_DIGIT);

    /**
     * Not used: this class only has static methods.
     */
    private PasswordPolicy() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Returns the description of every rule, in the order they are checked.
     *
     * @return an unmodifiable list of all rule descriptions
     */
    public static List<String> rules() {
        return ALL_RULES;
    }

    /**
     * Checks a password against every rule and reports each one it breaks, so the
     * user can be told exactly which rules are not met (alternative flow A3).
     *
     * @param password the plaintext password to check; {@code null} is treated as empty
     * @return an unmodifiable list of the descriptions of the rules that are not met,
     *         in the order of {@link #rules()}; empty if the password is valid
     */
    public static List<String> findViolations(String password) {
        String candidate = password == null ? "" : password;
        List<String> violations = new ArrayList<>();
        if (candidate.codePointCount(0, candidate.length()) < MIN_LENGTH) {
            violations.add(RULE_MIN_LENGTH);
        }
        if (candidate.codePoints().noneMatch(Character::isUpperCase)) {
            violations.add(RULE_UPPERCASE);
        }
        if (candidate.codePoints().noneMatch(Character::isLowerCase)) {
            violations.add(RULE_LOWERCASE);
        }
        if (candidate.codePoints().noneMatch(Character::isDigit)) {
            violations.add(RULE_DIGIT);
        }
        return Collections.unmodifiableList(violations);
    }

    /**
     * Tells whether a password satisfies every rule.
     *
     * @param password the plaintext password to check; {@code null} is treated as empty
     * @return {@code true} if no rule is broken, {@code false} otherwise
     */
    public static boolean isValid(String password) {
        return findViolations(password).isEmpty();
    }

    /**
     * Joins rule descriptions into readable English: {@code "a"}, {@code "a and b"}
     * or {@code "a, b and c"}.
     *
     * @param rules the rule descriptions; must not be empty
     * @return the joined descriptions, or empty string if {@code rules} is empty
     */
    public static String joinRules(List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return "";
        }
        if (rules.size() == 1) {
            return rules.get(0);
        }
        String allButLast = String.join(", ", rules.subList(0, rules.size() - 1));
        return allButLast + " and " + rules.get(rules.size() - 1);
    }

    /**
     * Formats a list of password rule violations into a user-facing error message.
     *
     * @param violations the broken rules from {@link #findViolations(String)}
     * @return the formatted error message, e.g. "Password must contain at least 8 characters and at least one digit."
     */
    public static String formatViolations(List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            return "";
        }
        return "Password must contain " + joinRules(violations) + ".";
    }
}
