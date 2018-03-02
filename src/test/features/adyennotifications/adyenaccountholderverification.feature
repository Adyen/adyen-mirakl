Feature: Bank Account Verification

    @ADY-13
    Scenario: ACCOUNT_HOLDER_VERIFICATION notification is received after seller provides Bank Account Details
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity |
            | Individual  |
        When we process the data and push to Adyen
        Then the ACCOUNT_HOLDER_VERIFICATION notification is sent by Adyen comprising of BANK_ACCOUNT_VERIFICATION and <verificationStatus>
            | verificationStatus |
            | AWAITING_DATA      |

    @ADY-8
    Scenario: New BankAccountDetail is created for Account Holder upon new IBAN entry in Mirakl
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | bank name | iban                   |
            | Individual  | RBS       | GB26TEST40051512347366 |
        When we process the data and push to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType                   |
            | ACCOUNT_HOLDER_VERIFICATION |

    @ADY-8
    Scenario: New BankAccountDetail is created for Account Holder upon new IBAN entry in Mirakl for an existing accountHolder
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity |
            | Individual  |
        And we process the data and push to Adyen
        And a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided
        When we process the data and push to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              |
            | ACCOUNT_HOLDER_UPDATED |

    @ADY-8
    Scenario:  Editing IBAN in Mirakl will create new BankAccountDetail in Adyen
        Given a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data
            | legalEntity | bank name |
            | Individual  | RBS       |
        And we process the data and push to Adyen
        When the IBAN has been modified in Mirakl
        And we process the data and push to Adyen
        Then a new bankAccountDetail will be created for the existing Account Holder
            | eventType              |
            | ACCOUNT_HOLDER_UPDATED |
        And the previous BankAccountDetail will be removed
