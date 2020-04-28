Feature: Retrieve download URL for a Publication File

  Scenario: A User retrieves a download URL for a Publication File
    Given the User is the owner of the Publication
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request POST /download/{identifier}/files/{fileIdentifier}/generate
    Then they receive a response with status code 201
    And they see that the response Content-type is "application/json"
    And they see the response body is a JSON object with a presigned download url

  Scenario: A User retrieves a download URL of a Publication File
    Given the Publication is published
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request POST /download/{identifier}/files/{fileIdentifier}/generate
    Then they receive a response with status code 201
    And they see that the response Content-type is "application/json"
    And they see the response body is a JSON object with a presigned download url

  Scenario: An unauthorized User tries to retrieve a download URL of a Publication File
    Given A User wants to retrieve a Publication File
    But the Publication is not published
    And the User is not the Publication Owner
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request POST /download/{identifier}/files/{fileIdentifier}/generate
    Then they receive a response with status code 401
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Unauthorized"
    And they see the response body has a field "status" with the value "401"