package com.hkust.marketplace.models;

/**
 * Enumerates the distinct categories of authentication failures
 * for role-based login (US-B02).
 */
public enum LoginErrorType {
    /** Successful authentication. */
    NONE,

    /** Username or password was not provided (Alternative Flow A1). */
    EMPTY_FIELDS,

    /** Username does not exist or password does not match (Alternative Flow A2). */
    INVALID_CREDENTIALS,

    /** Valid credentials but wrong user role (Alternative Flow A3). */
    WRONG_ROLE,

    /** Valid buyer credentials but account is deactivated or deleted (Alternative Flow A4). */
    ACCOUNT_DEACTIVATED
}
