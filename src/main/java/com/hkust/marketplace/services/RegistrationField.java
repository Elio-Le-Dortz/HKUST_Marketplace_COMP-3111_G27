package com.hkust.marketplace.services;

/**
 * Fields of a registration form that can fail validation. A {@link RegistrationResult}
 * groups its error messages by these fields so the screen can highlight each
 * invalid field.
 */
public enum RegistrationField {
    /** The username: required and unique across buyer, seller and admin accounts. */
    USERNAME,
    /** The full name: required. */
    FULL_NAME,
    /** The password: required and must meet the password rules. */
    PASSWORD,
    /** The confirm password: must be identical to the password. */
    CONFIRM_PASSWORD
}
