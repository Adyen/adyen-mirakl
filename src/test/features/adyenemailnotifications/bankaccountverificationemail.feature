Feature: Bank Account Verification email notification

    @ADY-23
    Scenario: Bank Statement message is sent to seller and operator (BCC)
        Given a seller creates a shop as an Individual with bank account information
            | city        | bank name | iban                   | bankOwnerName | lastName |
            | INVALIDDATA | testBank  | GB26TEST40051512347366 | TestData      | TestData |
        And the connector processes the data and pushes to Adyen
        When a RETRY_LIMIT_REACHED verificationStatus has been sent to the Connector
            """
                {
                    "eventDate": "2018-03-09T08:21:10+01:00",
                    "eventType": "ACCOUNT_HOLDER_VERIFICATION",
                    "executingUserKey": "Account Holder Update",
                    "live": "false",
                    "pspReference": "8815208551296708",
                    "content": {
                        "accountHolderCode": "$shopId$",
                        "bankAccountUUID": "e2d92677-1472-4e82-b8f5-54137bfd609c",
                        "statusSummary": {
                            "code": 1606,
                            "description": "The maximum amount of retries has been surpassed, please provide a document."
                        },
                        "statusSummaryItems": [{
                            "KYCCheckDataSummaryItem": {
                                "itemCode": 1606,
                                "itemDescription": "The maximum amount of retries has been surpassed, please provide a document."
                            }
                        }],
                        "verificationStatus": "RETRY_LIMIT_REACHED",
                        "verificationType": "BANK_ACCOUNT_VERIFICATION"
                    }
                }
            """
        Then an Account verification email will be sent to the seller

