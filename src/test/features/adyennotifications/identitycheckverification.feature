@cucumber
Feature: Identity verification check

    @ADY-18
    Scenario: Mandatory shareholder data is passed to Adyen to perform KYC Identity Check
        Given a new shop has been created in Mirakl with UBO Data for a Business
            | maxUbos | lastName |
            | 4       | testData |
        And we process the data and push to Adyen
        Then adyen will send multiple ACCOUNT_HOLDER_VERIFICATION notifications with IDENTITY_VERIFICATION of status DATA_PROVIDED
            | maxUbos |
            | 4       |

    @ADY-18
    Scenario: Mandatory shareholder data is updated and sent to Adyen to re-perform KYC Identity Check
        Given a new shop has been created in Mirakl with UBO Data for a Business
            | maxUbos | lastName |
            | 4       | testData |
        And we process the data and push to Adyen
        When the shareholder data has been updated in Mirakl
            | UBO | firstName | lastName |
            | 1   | John      | Smith    |
            | 2   | Sarah     | Godwin   |
            | 3   | Alex      | Pincher  |
            | 4   | Faye      | Jarvis   |
        And we process the data and push to Adyen
        Then adyen will send the ACCOUNT_HOLDER_UPDATED notification with multiple IDENTITY_VERIFICATION of status DATA_PROVIDED
        And getAccountHolder will have the correct amount of shareholders and data in Adyen
            | maxUbos |
            | 4       |

    @ADY-18 @ADY-102
    Scenario: Share Holder mandatory information is not provided therefore Identity Check will return AWAITING_DATA
        Given a new Business shop has been created in Mirakl without mandatory Shareholder Information
            | maxUbos | lastName |
            | 4       | testData |
        And we process the data and push to Adyen
        Then adyen will send multiple ACCOUNT_HOLDER_VERIFICATION notifications with IDENTITY_VERIFICATION of status AWAITING_DATA
            | maxUbos |
            | 4       |

    @ADY-99 @ADY-108 @bug
    Scenario: Uploading a new photo Id/Updating photo Id for shareholder to complete Identity Checks
        Given a new shop has been created in Mirakl with UBO Data for a Business
            | maxUbos | lastName |
            | 4       | testData |
        And we process the data and push to Adyen
        When the seller uploads a document in Mirakl
            | front                   | back                    | UBO |
            | passportFront.jpg       | passportBack.jpg        | 1   |
            | idCardFront.jpg         | idCardBack.jpg          | 2   |
            | drivingLicenseFront.jpg | drivingLicenseBack.jpg  | 3   |
            |                         | anotherPassportBack.jpg | 4   |
        And sets the photoIdType in Mirakl
            | photoIdType     | UBO |
            | PASSPORT        | 1   |
            | ID_CARD         | 2   |
            | DRIVING_LICENCE | 3   |
            | PASSPORT        | 4   |
        And we process the document data and push to Adyen
        Then the documents are successfully uploaded to Adyen
            | documentType    | filename                |
            | PASSPORT        | passportFront.jpg       |
            | ID_CARD         | idCardFront.jpg         |
            | DRIVING_LICENCE | drivingLicenseFront.jpg |
            | DRIVING_LICENCE | drivingLicenseBack.jpg  |
        And the following document will not be uploaded to Adyen
            | documentType | filename         |
            | PASSPORT     | passportBack.jpg |
        When the seller uploads a document in Mirakl
            | front             | back | UBO |
            | passportFront.jpg |      | 4   |
        And we process the document data and push to Adyen
        Then the updated documents are successfully uploaded to Adyen
            | documentType | filename          |
            | PASSPORT     | passportFront.jpg |
