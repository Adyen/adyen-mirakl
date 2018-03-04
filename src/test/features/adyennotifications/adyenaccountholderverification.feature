Feature: Bank Account Verification

    @ADY-13 @ADY-77 @bug
    Scenario: ACCOUNT_HOLDER_VERIFICATION notification is received after seller provides Bank Account Details
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | city   | bank name | iban                   | bankOwnerName | lastName |
            | Individual  | PASSED | testBank  | GB26TEST40051512347366 | TestData      | TestData |
        When we process the data and push to Adyen
        Then the ACCOUNT_HOLDER_VERIFICATION notification is sent by Adyen comprising of BANK_ACCOUNT_VERIFICATION and DATA_PROVIDED

    @ADY-8 @ADY-77 @bug
    Scenario: New BankAccountDetail is created for Account Holder upon new IBAN entry in Mirakl
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | bank name | iban                   | bankOwnerName | lastName |
            | Individual  | RBS       | GB26TEST40051512347366 | TestData      | TestData |
        When we process the data and push to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              |
            | ACCOUNT_HOLDER_CREATED |

    @ADY-8 @ADY-71 @bug
    Scenario: New BankAccountDetail is created for Account Holder upon new IBAN entry in Mirakl for an existing accountHolder
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | lastName |
            | Individual  | TestData |
        And we process the data and push to Adyen
        And a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided
        When we process the data and push to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              |
            | ACCOUNT_HOLDER_UPDATED |

    @ADY-8 @ADY-71 @bug
    Scenario: Editing IBAN in Mirakl will create new BankAccountDetail in Adyen
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | bank name | iban                   | bankOwnerName | lastName |
            | Individual  | RBS       | GB26TEST40051512347366 | TestData      | TestData |
        And we process the data and push to Adyen
        When the IBAN has been modified in Mirakl
            | iban                   |
            | GB26TEST40051512393150 |
        And we process the data and push to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              |
            | ACCOUNT_HOLDER_UPDATED |
        And the previous BankAccountDetail will be removed

    @ADY-14
    Scenario: COMPANY_VERIFICATION check upon account creation
        Given create Shareholder data is set to true
        And legalBusinessName and taxId have been provided in Mirakl
        And a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | maxUbos | lastName |
            | Business    | 4       | TestData |
        When we process the data and push to Adyen
        Then adyen will send the ACCOUNT_HOLDER_VERIFICATION comprising of COMPANY_VERIFICATION and status of DATA_PROVIDED

    @ADY-14
    Scenario: COMPANY_VERIFICATION check upon account update
        Given create Shareholder data is set to true
        And a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | maxUbos | lastName |
            | Business    | 4       | TestData |
        And we process the data and push to Adyen
        And Mirakl has been updated with a taxId
        And we process the data and push to Adyen
        Then adyen will send the ACCOUNT_HOLDER_UPDATED comprising of accountHolder COMPANY_VERIFICATION and status of DATA_PROVIDED
