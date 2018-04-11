Feature: Seller Account Management

    @ADY-6
    Scenario: Creating Mirakl shop will create Adyen Account Holder for an Individual
        Given a seller creates a new shop as an Individual
            | lastName |
            | TestData |
        When the connector processes the data and pushes to Adyen
        Then an AccountHolder will be created in Adyen with status Active
        And a notification will be sent pertaining to ACCOUNT_HOLDER_CREATED
        And the shop data is correctly mapped to the Adyen Account

    @ADY-53
    Scenario: Creating new shop as Business with all shareholders will create new Account Holder in adyen
        Given a seller creates a shop as a Business and provides full UBO data
            | maxUbos | lastName |
            | 4       | TestData |
        And the connector processes the data and pushes to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_CREATED
        And the account holder is created in Adyen with status Active
        And the shop data is correctly mapped to the Adyen Business Account
            | maxUbos |
            | 4       |

    @ADY-53 @ADY-91 @ADY-104
    Scenario: Creating new shop as Business with 1 shareholder and updating shop with 3 shareholder details
        Given a seller creates a shop as a Business and provides full UBO data
            | maxUbos | lastName |
            | 1       | TestData |
        And the connector processes the data and pushes to Adyen
        When we update the shop by adding more shareholder data
            | maxUbos |
            | 3       |
        And the connector processes the data and pushes to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_UPDATED
        And the shop data is correctly mapped to the Adyen Business Account
            | maxUbos |
            | 4       |

    @ADY-53
    Scenario: Don't create Adyen Account Holder if no shareholder data has been entered
        Given a seller creates a new shop as a Business and does not provide any UBO data
            | lastName |
            | TestData |
        When the connector processes the data and pushes to Adyen
        Then no account holder is created in Adyen

    @ADY-116
    Scenario: Shareholder mapping table handles UBOs created in non-sequential order
        Given shop is created as a Business where UBO is entered in non-sequential order
            | UBO | lastName |
            | 2   | TestData |
        And the connector processes the data and pushes to Adyen
        When the Mirakl Shop is updated by adding more shareholder data
            | UBO |
            | 1   |
            | 3   |
            | 4   |
        And the connector processes the data and pushes to Adyen
        And a notification will be sent pertaining to ACCOUNT_HOLDER_UPDATED
        Then the shop data is correctly mapped to the Adyen Business Account
            | maxUbos |
            | 4       |

    @ADY-70
    Scenario: The connector will strip out the house name or number for Netherlands addresses
        Given a Netherlands seller creates a Business shop in Mirakl with UBO data and a bankAccount
            | city   | bank name | iban                   | bankOwnerName | lastName | maxUbos |
            | PASSED | testBank  | GB26TEST40051512347366 | TestData      | TestData | 4       |
        When the connector processes the data and pushes to Adyen
        Then an AccountHolder will be created in Adyen with status Active
        And a notification will be sent pertaining to ACCOUNT_HOLDER_CREATED
        And the netherlands shop data is correctly mapped to the Adyen Business Account
            | maxUbos |
            | 4       |
        And the Adyen bankAccountDetails will posses the correct street data
