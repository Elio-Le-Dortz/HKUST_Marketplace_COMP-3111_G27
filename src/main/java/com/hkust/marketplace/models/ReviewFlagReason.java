package com.hkust.marketplace.models;

/**
 * Reasons a Seller can choose when flagging a buyer's review for the admin (use case 2.9
 * "Handle Reviews", subflow S2, US-S09).
 *
 * <p>The class diagram stores the reason as text, in the {@code flagReason} attribute of
 * {@link Review}. This enumeration only offers the Seller a fixed list to choose from:
 * {@link #describe(String)} turns the choice, and the optional details typed by the
 * Seller, into the text that is stored and shown to the admin (use case 3.8).</p>
 */
public enum ReviewFlagReason {

    /** The review contains insults or offensive words. */
    OFFENSIVE_LANGUAGE("Offensive language", false),
    /** The review is about something other than the deal. */
    NOT_RELATED_TO_DEAL("Not related to the deal", false),
    /** The review states things that did not happen. */
    FALSE_INFORMATION("False or misleading information", false),
    /** The review reveals personal information, such as a phone number or a room number. */
    PERSONAL_INFORMATION("Shares personal information", false),
    /** The review advertises something or is spam. */
    SPAM("Spam or advertising", false),
    /** Any other reason, which the Seller must explain in the details. */
    OTHER("Other", true);

    /** Name shown to the Seller and stored in the review. */
    private final String displayName;
    /** Whether the Seller must explain the reason in the details. */
    private final boolean detailsRequired;

    /**
     * Creates a reason.
     *
     * @param displayName     name shown to the Seller and stored in the review
     * @param detailsRequired whether the Seller must explain the reason in the details
     */
    ReviewFlagReason(String displayName, boolean detailsRequired) {
        this.displayName = displayName;
        this.detailsRequired = detailsRequired;
    }

    /**
     * Returns the name of the reason, for example {@code Offensive language}.
     *
     * @return the name shown to the Seller
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Tells whether the Seller must explain this reason in the details, which is the case
     * for {@link #OTHER}.
     *
     * @return {@code true} if details are required
     */
    public boolean isDetailsRequired() {
        return detailsRequired;
    }

    /**
     * Builds the text stored as the review's flag reason: the name of the reason, followed by
     * the Seller's details if there are any, for example
     * {@code Offensive language: insults me in the second sentence}.
     *
     * @param details the details typed by the Seller, may be {@code null} or blank
     * @return the flag reason text
     */
    public String describe(String details) {
        String cleanDetails = details == null ? "" : details.trim();
        return cleanDetails.isEmpty() ? displayName : displayName + ": " + cleanDetails;
    }

    /**
     * Returns the name of the reason, so that a choice list shows it.
     *
     * @return the name shown to the Seller
     */
    @Override
    public String toString() {
        return displayName;
    }
}
