# nva-download-file

#####Description

Lambda for generating presigned download URLs for publication files stored in Amazon S3 bucket.

Credentials are required. Users are only authorized to receive a download URL when:
 
 1. The user is the owner of the publication
 2. The publication is published.
 
#####Envs

See template.yaml for more details

#####Input
Input path parameters are `identifier` (publication id) and `fileIdentifier` (file id)
Usage:

```
POST /download/{identifier}/files/{fileIdentifier}/generate
```

#####Successful response:

Status: 201 Created

Content-type: application/json
```
{
    "presignedDownloadUrl": "https://example.com/download?id=1234"
}
```