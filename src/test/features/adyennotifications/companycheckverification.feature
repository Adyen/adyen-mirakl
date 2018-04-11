Feature: Company Verification check

    @ADY-14 @ADY-84
    Scenario: COMPANY_VERIFICATION check upon account creation
        Given a Business shop has been created
            | maxUbos | lastName |
            | 4       | TestData |
        When the connector processes the data and pushes to Adyen
        Then adyen will send the ACCOUNT_HOLDER_VERIFICATION comprising of COMPANY_VERIFICATION and status of DATA_PROVIDED
