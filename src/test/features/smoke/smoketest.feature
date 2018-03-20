@cucumber
Feature: Smoke test

    Scenario: Ping test to ensure endpoint is receiving notifications
        Given an update shop exists in Mirakl
            | seller       | firstName | lastName | postCode | city   |
            | UpdateShop01 | Raquel    | TestData | WE5P 8JL | PASSED |
        When the Mirakl Shop Details have been updated
            | firstName | lastName | postCode | city       |
            | John      | Smith    | SE1 9GB  | Manchester |
        And we process the data and push to Adyen
        Then a notification will be sent pertaining to ACCOUNT_HOLDER_UPDATED
        And the Mirakl Shop Details have been updated
            | firstName | lastName | postCode | city   |
            | Raquel    | TestData | WE5P 8JL | PASSED |
        And we process the data and push to Adyen

