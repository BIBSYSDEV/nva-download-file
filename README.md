# nva-download-file

##### Description

Lambda for generating presigned download URLs for publication files stored in Amazon S3 bucket.

Credentials are required. Users are authorized to receive a download URL when one of the conditions below are met:
 
 * When the publication is unpublished, the user must be the owner of the publication
 * When the publication is published
 
##### Envs

See template.yaml for more details

##### Input
Input path parameters are `identifier` (publication id) and `fileIdentifier` (file id)

Usage:

```
GET /download/{identifier}/files/{fileIdentifier}/redirect

or

GET /download/{identifier}/files/{fileIdentifier}/json
```

##### Successful response .../redirect:

Status: 302 Found 

Location: https://apresigneddownloadurlexample.com/download?id=1234

(The response from the presigned download url should provide Content-Disposition, Content-Type, etc.)

##### Successful response .../json:

Status: 200 OK 

Body:
```
{
    "presignedDownloadUrl": "https://apresigneddownloadurlexample.com/download?id=1234" 
}
```

(The response from the presigned download url should provide Content-Disposition, Content-Type, etc.)
