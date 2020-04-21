Feature: Download a Publication File

  Scenario: The User downloads a Publication File from a Publication
    Given the User is the owner of the Publication
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request POST /files/{publicationIdentifier}/file/{fileIdentifier}/download
    Then they receive a response with status code 200
    And they see that the response Content-type is "application/json"
    And they see the response body is a JSON object with a presigned download url

  Scenario: A User downloads a Publication File from a published Publication
    Given the Publication is published
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request POST /files/{publicationIdentifier}/file/{fileIdentifier}/download
    Then they receive a response with status code 200
    And they see that the response Content-type is "application/json"
    And they see the response body is a JSON object with a presigned download url