Feature: Retrieve download URL for a Publication File

  Scenario: A User retrieves a download URL for an Unpublished Publication File
    Given the User is the owner of the Publication
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 302
    And they see that the response Location contains a presigned download url

  Scenario: A User retrieves a download URL for a Published Publication File
    Given the Publication is published
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 302
    And they see that the response Location contains a presigned download url

  Scenario: An unauthorized User tries to retrieve a download URL of a Publication File
    Given A User wants to retrieve a Publication File
    But the Publication is not published
    And the User is not the Publication Owner
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 401
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Unauthorized"
    And they see the response body has a field "status" with the value "401"

  Scenario: A User tries to retrieve a download URL of a non existent Publication File
    Given A User wants to retrieve a Publication File
    But the Publication Identifier does not exist
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 404
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Not Found"
    And they see the response body has a field "status" with the value "404"

  Scenario: A User tries to retrieve a download URL of Publication File
    Given A User wants to retrieve a Publication File
    But An error occurs in communication with S3
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 503
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Service Unavailable"
    And they see the response body has a field "status" with the value "503"

  Scenario: A User tries to retrieve a download URL of Publication File
    Given A User wants to retrieve a Publication File
    But The User provides malformed identifier
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 400
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Bad Request"
    And they see the response body has a field "status" with the value "400"

  Scenario: A User tries to retrieve a download URL of Publication File
    Given A User wants to retrieve a Publication File
    But The User provides a non existing fileIdentifier
    When they request GET /download/{identifier}/files/{fileIdentifier}/generate
    And they set the Authentication header to a Bearer token with their credentials
    Then they receive a response with status code 404
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Not Found"
    And they see the response body has a field "status" with the value "404"