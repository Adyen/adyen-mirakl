@cucumber
Feature: Seller Account Management

    @ADY-6
    Scenario: Creating Mirakl shop will create Adyen Account Holder for an Individual
        Given a new shop has been created in Mirakl for an Individual
            | lastName |
            | TestData |
        When we process the data and push to Adyen
        Then an AccountHolder will be created in Adyen with status Active
        And a notification will be sent pertaining to ACCOUNT_HOLDER_CREATED
        And the shop data is correctly mapped to the Adyen Account

    @ADY-53
    Scenario: Creating new shop as Business with all shareholders will create new Account Holder in adyen
        Given a new shop has been created in Mirakl with UBO Data for a Business
            | maxUbos | lastName |
            | 4       | TestData |
        And we process the data and push to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_CREATED
        And the account holder is created in Adyen with status Active
        And the shop data is correctly mapped to the Adyen Business Account
            | maxUbos |
            | 4       |

    @ADY-53 @ADY-91 @ADY-104 @bug
    Scenario: Creating new shop as Business with 1 shareholder and updating shop with 3 shareholder details
        Given a new shop has been created in Mirakl with UBO Data for a Business
            | maxUbos | lastName |
            | 1       | TestData |
        And we process the data and push to Adyen
        When we update the shop by adding more shareholder data
            | maxUbos |
            | 3       |
        And we process the data and push to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_UPDATED
        And the shop data is correctly mapped to the Adyen Business Account
            | maxUbos |
            | 4       |

    @ADY-53
    Scenario: Don't create Adyen Account Holder if no shareholder data has been entered
        Given a new shop has been created in Mirakl for a Business
            | lastName |
            | TestData |
        When we process the data and push to Adyen
        Then no account holder is created in Adyen
