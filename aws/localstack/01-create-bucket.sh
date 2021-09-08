#!/usr/bin/env bash
set -e

S3_BUCKET=$(python scripts/read-property.py "__resources/application.properties" "s3.bucket")
echo "*** Read S3 bucket name from config = $S3_BUCKET ***"

echo "*** Creating bucket $S3_BUCKET ***"
awslocal s3 mb s3://$S3_BUCKET

