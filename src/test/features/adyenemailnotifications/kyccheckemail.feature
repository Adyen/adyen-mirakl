Feature: KYC check intervention emails

    @ADY-17 @ADY-112
    Scenario: email is sent to seller upon IDENTITY_VERIFICATION INVALID_DATA notification
        Given the seller created a Business shop with Invalid Data
            | maxUbos | lastName    | city        |
            | 4       | INVALIDDATA | INVALIDDATA |
        And the connector processes the data and pushes to Adyen
        When the IDENTITY_VERIFICATION notifications containing INVALID_DATA status are sent to the Connector for each UBO
        Then a remedial email will be sent for each ubo
        """
        Account verification, invalid data
        """
