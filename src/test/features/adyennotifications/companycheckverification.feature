@cucumber
Feature: Company Verification check

    @ADY-14 @ADY-84
    Scenario: COMPANY_VERIFICATION check upon account creation
        Given a new shop has been created in Mirakl for a Business
            | maxUbos | lastName |
            | 4       | TestData |
        When we process the data and push to Adyen
        Then adyen will send the ACCOUNT_HOLDER_VERIFICATION comprising of COMPANY_VERIFICATION and status of DATA_PROVIDED


