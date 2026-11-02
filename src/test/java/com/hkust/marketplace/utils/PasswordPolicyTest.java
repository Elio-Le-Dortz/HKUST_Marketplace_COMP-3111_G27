package com.hkust.marketplace.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordPolicy: password strength rules")
class PasswordPolicyTest {

    @ParameterizedTest(name = "\"{0}\" is accepted")
    @ValueSource(strings = {"Abcdefg1", "StrongPass123", "MyP@ssw0rd!", "ÉcoleNormale9", "Pass 1234"})
    @DisplayName("Passwords meeting every rule are valid")
    void validPasswords(String password) {
        assertTrue(PasswordPolicy.isValid(password));
        assertTrue(PasswordPolicy.findViolations(password).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Null or empty password breaks every rule")
    void nullOrEmptyBreaksEveryRule(String password) {
        assertEquals(PasswordPolicy.rules(), PasswordPolicy.findViolations(password));
        assertFalse(PasswordPolicy.isValid(password));
    }

    @Test
    @DisplayName("7 characters is too short, 8 characters is long enough")
    void lengthBoundary() {
        assertEquals(List.of(PasswordPolicy.RULE_MIN_LENGTH), PasswordPolicy.findViolations("Abcdef1"));
        assertTrue(PasswordPolicy.isValid("Abcdefg1"));
    }

    @Test
    @DisplayName("Length is counted in code points, not UTF-16 units")
    void lengthCountsCodePoints() {
        // 6 ASCII characters + 1 emoji = 7 code points but 8 UTF-16 units: still too short.
        String sevenCodePoints = "Abcde1😀";
        assertEquals(8, sevenCodePoints.length());
        assertEquals(List.of(PasswordPolicy.RULE_MIN_LENGTH), PasswordPolicy.findViolations(sevenCodePoints));
    }

    @Test
    @DisplayName("Missing uppercase letter is reported")
    void missingUppercase() {
        assertEquals(List.of(PasswordPolicy.RULE_UPPERCASE), PasswordPolicy.findViolations("alllowercase1"));
    }

    @Test
    @DisplayName("Missing lowercase letter is reported")
    void missingLowercase() {
        assertEquals(List.of(PasswordPolicy.RULE_LOWERCASE), PasswordPolicy.findViolations("ALLUPPERCASE1"));
    }

    @Test
    @DisplayName("Missing digit is reported")
    void missingDigit() {
        assertEquals(List.of(PasswordPolicy.RULE_DIGIT), PasswordPolicy.findViolations("NoDigitsHere"));
    }

    @Test
    @DisplayName("Every broken rule is reported, in rule order")
    void reportsEveryBrokenRule() {
        assertEquals(List.of(PasswordPolicy.RULE_MIN_LENGTH, PasswordPolicy.RULE_UPPERCASE, PasswordPolicy.RULE_DIGIT),
                PasswordPolicy.findViolations("short"));
        assertEquals(List.of(PasswordPolicy.RULE_UPPERCASE, PasswordPolicy.RULE_LOWERCASE, PasswordPolicy.RULE_DIGIT),
                PasswordPolicy.findViolations("        "));
    }

    @Test
    @DisplayName("Rule list and violation list cannot be modified by callers")
    void listsAreUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () -> PasswordPolicy.rules().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> PasswordPolicy.findViolations("x").clear());
    }

    @Test
    @DisplayName("Rule descriptions mention the minimum length")
    void ruleDescriptions() {
        assertEquals(4, PasswordPolicy.rules().size());
        assertTrue(PasswordPolicy.RULE_MIN_LENGTH.contains(String.valueOf(PasswordPolicy.MIN_LENGTH)));
    }
}
