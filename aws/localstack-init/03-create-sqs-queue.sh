#!/usr/bin/env bash
set -e

echo "*** Creating SQS Queue ***"

Queues=(
  Test-Queue
)

for queue in ${Queues[@]}; do
  echo "- Creating Queue: $queue"
  awslocal sqs create-queue --queue-name $queue.fifo --attributes file://__config__/$queue-Attributes.json
done