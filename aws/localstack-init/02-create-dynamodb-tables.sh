#!/usr/bin/env bash
set -e

echo "*** Creating DynamoDB tables ***"
awslocal dynamodb create-table --cli-input-json file://__config__/create-aggregate-table.json