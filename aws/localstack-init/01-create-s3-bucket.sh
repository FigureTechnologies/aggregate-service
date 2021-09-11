#!/usr/bin/env bash
set -e

# See docker-compose.local.yml file for how "__resources__" directory is mapped:
S3_BUCKET=$(python __scripts__/read-property.py "__resources__/application.properties" "s3.bucket")
echo "*** Read S3 bucket name from config = $S3_BUCKET ***"

echo "*** Creating bucket $S3_BUCKET ***"
awslocal s3 mb s3://$S3_BUCKET

