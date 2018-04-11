@exclude
Feature: Create payout shops which will be KYC'ed for seller payout tests

#    Description:
#    this feature file is used to create the seller payout shops in Mirakl and AccountHolders in Adyen,
#    it is not to be used as part of testing or regression testing hence the @exclude tag.
#
#    Sellers:
#    UpdateShop01 will be used for the smoketest and ADY-11
#    UpdateShop02 will be used for scenario ADY-42

#   Individual shops:
    Scenario: Create shops for Individual sellers
        Given a shop has been created in Mirakl for an Individual with mandatory KYC data
            | companyName  | city       | bank name | iban                   | bankOwnerName | lastName |
            | UpdateShop01 | Manchester | testBank  | GB26TEST40051512347366 | TestData      | Smith    |
        When the connector processes the data and pushes to Adyen
        Then the ACCOUNT_HOLDER_VERIFICATION notification is sent by Adyen comprising of BANK_ACCOUNT_VERIFICATION and DATA_PROVIDED

#   Business shops:
    Scenario: Create test shops for Business sellers
        Given a seller creates a shop as a Business and provides full UBO data
            | maxUbos | lastName | companyName  |
            | 4       | TestData | UpdateShop02 |
        When the connector processes the data and pushes to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_CREATED

