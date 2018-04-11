@smoke
Feature: Smoke test

    Scenario: Ping test to ensure endpoint is receiving notifications
        Given a shop exists in Mirakl
            | seller       |
            | UpdateShop01 |
        When the Mirakl Shop Details have been changed
        And the connector processes the data and pushes to Adyen
        Then the ACCOUNT_HOLDER_UPDATED will be sent by Adyen

