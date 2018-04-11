Feature: Bank Account Verification

    @ADY-8 @ADY-77 @ADY-84 @ADY-102 @ADY-13 @ADY-100 @ADY-15 @ADY-89
    Scenario: ACCOUNT_HOLDER_VERIFICATION notification including a new BankAccountDetail is sent by Adyen upon providing Bank Account Details and editing IBAN.
    Seller uploads Bank Statement Mirakl to fulfil BANK_ACCOUNT_VERIFICATION in Adyen
        Given a shop has been created in Mirakl for an Individual with Bank Information
            | city   | bank name | iban                   | bankOwnerName | lastName |
            | PASSED | RBS       | GB26TEST40051512347366 | TestData      | TestData |
        When the connector processes the data and pushes to Adyen
        Then the ACCOUNT_HOLDER_VERIFICATION notification is sent by Adyen comprising of BANK_ACCOUNT_VERIFICATION and DATA_PROVIDED
        And a new bankAccountDetail will be created for the existing Account Holder
            | eventType              | iban                   |
            | ACCOUNT_HOLDER_CREATED | GB26TEST40051512347366 |
        When the IBAN has been modified in Mirakl
            | iban                   |
            | GB26TEST40051512393150 |
        And the connector processes the data and pushes to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              | iban                   |
            | ACCOUNT_HOLDER_UPDATED | GB26TEST40051512393150 |
        And the previous BankAccountDetail will be removed
            | eventType                    | reason                |
            | ACCOUNT_HOLDER_STATUS_CHANGE | Bank account deletion |
        When the seller uploads a Bank Statement in Mirakl
        And the connector processes the document data and push to Adyen
        And the document is successfully uploaded to Adyen
            | documentType   | filename          |
            | BANK_STATEMENT | BankStatement.jpg |

    @ADY-8 @ADY-71 @ADY-84 @ADY-104
    Scenario: New BankAccountDetail is created for Account Holder upon new IBAN entry in Mirakl for an existing Adyen accountHolder
        Given a seller creates a shop as a Individual without entering a bank account
            | lastName |
            | TestData |
        And the connector processes the data and pushes to Adyen
        And a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided
        When the connector processes the data and pushes to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              |
            | ACCOUNT_HOLDER_UPDATED |


