package com.adyen.mirakl.config;

/**
 * Application constants.
 */
public final class Constants {

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";

    public static final String SYSTEM_ACCOUNT = "system";
    public static final String ANONYMOUS_USER = "anonymoususer";
    public static final String DEFAULT_LANGUAGE = "en";

    public static final String BANKPROOF = "adyen-bankproof";

    public final class Messages {
        public static final String EMAIL_ACCOUNT_HOLDER_VALIDATION_TITLE = "email.account.holder.validation.title";
        public static final String EMAIL_ACCOUNT_HOLDER_PAYOUT_FAILED_TITLE = "email.account.holder.payout.failed.title";

        private Messages() {
        }
    }

    private Constants() {
    }
}
