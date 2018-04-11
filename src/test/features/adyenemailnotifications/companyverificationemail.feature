Feature: Company verification email

    @ADY-87 @ADY-114
    Scenario Outline: Remedial email is sent to seller upon COMPANY_VERIFICATION notification of status AWAITING_DATA
        Given a seller creates a Business shop
            | maxUbos | lastName |
            | 4       | testData |
        And the connector processes the data and pushes to Adyen
        When the COMPANY_VERIFICATION notifications containing <verificationType> status are sent to the Connector
        Then an <title> email will be sent to the seller
        Examples:
            | title                    | verificationType |
            | Awaiting company ID data | AWAITING_DATA    |
            | Awaiting company ID data | INVALID_DATA     |
