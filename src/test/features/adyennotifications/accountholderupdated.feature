Feature: Account Holder Updated notification upon Mirakl shop changes

    @ADY-11 @ADY-71 @ADY-83
    Scenario: Updating Mirakl existing shop with contact details and verifying Adyen Account Holder Details are updated
        Given a seller creates a new shop as an Individual
            | lastName |
            | TestData |
        And the connector processes the data and pushes to Adyen
        And an AccountHolder will be created in Adyen with status Active
        When the Mirakl Shop Details have been updated
            | firstName | lastName | postCode | city       |
            | John      | Smith    | SE1 9GB  | Manchester |
        And the connector processes the data and pushes to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_UPDATED
        And the shop data is correctly mapped to the Adyen Account

    @ADY-11
    Scenario: ACCOUNT_HOLDER_UPDATED will not be invoked if no data has been changed
        Given a shop exists in Mirakl with the following fields
            | seller       | lastName | city       |
            | UpdateShop01 | Smith    | Manchester |
        When the Mirakl Shop Details have been updated as the same as before
            | lastName | city       |
            | Smith    | Manchester |
        And the connector processes the data and pushes to Adyen
        Then a notification of ACCOUNT_HOLDER_UPDATED will not be sent
