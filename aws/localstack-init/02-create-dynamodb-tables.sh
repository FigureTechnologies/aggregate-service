#!/usr/bin/env bash
set -e

echo "*** Creating DynamoDB tables ***"

TABLES=(
  Aggregate-Service-Metadata-Table
  Aggregate-Service-Block-Batch-Table
  Aggregate-Service-Block-Metadata-Table
  Aggregate-Service-S3-Key-Cache-Table
)

for table in ${TABLES[@]}; do
  echo "- Creating table: $table"
  awslocal dynamodb create-table --cli-input-json file://__config__/$table.json
done
