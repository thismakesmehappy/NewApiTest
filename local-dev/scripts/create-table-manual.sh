#!/bin/bash

# Quick manual table creation script
echo "🚀 Creating DynamoDB table manually..."

aws dynamodb create-table \
  --table-name toyapi-local-items \
  --attribute-definitions '[
    {"AttributeName":"PK","AttributeType":"S"},
    {"AttributeName":"SK","AttributeType":"S"},
    {"AttributeName":"userId","AttributeType":"S"},
    {"AttributeName":"createdAt","AttributeType":"S"}
  ]' \
  --key-schema '[
    {"AttributeName":"PK","KeyType":"HASH"},
    {"AttributeName":"SK","KeyType":"RANGE"}
  ]' \
  --global-secondary-indexes '[
    {
      "IndexName":"UserIndex",
      "KeySchema":[
        {"AttributeName":"userId","KeyType":"HASH"},
        {"AttributeName":"createdAt","KeyType":"RANGE"}
      ],
      "Projection":{"ProjectionType":"ALL"},
      "ProvisionedThroughput":{"ReadCapacityUnits":5,"WriteCapacityUnits":5}
    }
  ]' \
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
  --endpoint-url http://localhost:8000

echo "✅ Table created! You can now test your API."