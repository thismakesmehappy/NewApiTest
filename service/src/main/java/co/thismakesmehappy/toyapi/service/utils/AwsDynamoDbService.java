package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * AWS DynamoDB implementation of DynamoDbService.
 * Provides production DynamoDB operations using injected client.
 */
public class AwsDynamoDbService implements DynamoDbService {
    
    private final DynamoDbClient dynamoDbClient;
    
    public AwsDynamoDbService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }
    
    @Override
    public PutItemResponse putItem(PutItemRequest request) {
        return dynamoDbClient.putItem(request);
    }
    
    @Override
    public GetItemResponse getItem(GetItemRequest request) {
        return dynamoDbClient.getItem(request);
    }
    
    @Override
    public UpdateItemResponse updateItem(UpdateItemRequest request) {
        return dynamoDbClient.updateItem(request);
    }
    
    @Override
    public DeleteItemResponse deleteItem(DeleteItemRequest request) {
        return dynamoDbClient.deleteItem(request);
    }
    
    @Override
    public QueryResponse query(QueryRequest request) {
        return dynamoDbClient.query(request);
    }
    
    @Override
    public ScanResponse scan(ScanRequest request) {
        return dynamoDbClient.scan(request);
    }
}