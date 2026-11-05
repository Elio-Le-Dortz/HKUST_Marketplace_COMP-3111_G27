package com.hkust.marketplace.models;

/**
 * Outcome of an action of use case 2.9 "Handle Reviews" (US-S09): checking whether a review
 * can be answered or flagged, replying to it, or flagging it for the admin.
 */
public enum ReviewHandlingStatus {

    /** The action succeeded. */
    SUCCESS,

    /** No review was selected, or the review no longer exists. */
    REVIEW_NOT_FOUND,

    /** The review was written about another Seller. */
    UNAUTHORIZED,

    /** The admin hid the review (use case 3.8), so the Seller can no longer see or handle it. */
    REVIEW_HIDDEN,

    /** The reply is empty (alternative flow A2). */
    EMPTY_REPLY,

    /** The reply is longer than the maximum length. */
    REPLY_TOO_LONG,

    /** The Seller has already replied to the review: only one reply is allowed (alternative flow A3). */
    ALREADY_REPLIED,

    /** The review is already flagged and awaits the admin's decision (alternative flow A4). */
    ALREADY_FLAGGED,

    /** No flag reason was chosen, the details required by "Other" are missing, or the details are too long. */
    INVALID_FLAG_REASON
}
