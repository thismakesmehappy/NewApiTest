package co.thismakesmehappy.toyapi.service.analytics;

import co.thismakesmehappy.toyapi.service.utils.MockDynamoDbService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.nio.ByteBuffer;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsHandler with dependency injection.
 * Demonstrates improved testability through mock DynamoDB service.
 */
class AnalyticsHandlerTest {

    private MockDynamoDbService mockDynamoDbService;
    private AnalyticsHandler analyticsHandler;
    private Context mockContext;
    private LambdaLogger mockLogger;

    @BeforeEach
    void setUp() {
        mockDynamoDbService = new MockDynamoDbService();
        analyticsHandler = new AnalyticsHandler(mockDynamoDbService);
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
    }

    @AfterEach
    void tearDown() {
        mockDynamoDbService.clearTable();
    }

    @Test
    void testHandleValidKinesisEvent() {
        // Create a valid analytics event
        String eventData = createValidAnalyticsEventJson();
        KinesisEvent kinesisEvent = createKinesisEvent(eventData);

        // Process the event
        String result = analyticsHandler.handleRequest(kinesisEvent, mockContext);

        // Verify result
        assertEquals("Processed: 1, Errors: 0", result);
        
        // Verify logging
        verify(mockLogger, times(1)).log("Processing 1 analytics events");
        verify(mockLogger, times(1)).log("Processed: 1, Errors: 0");
        
        // Verify DynamoDB interactions - should have stored metrics
        assertTrue(mockDynamoDbService.getTableSize() > 0, "Should have stored analytics data");
    }

    @Test
    void testHandleInvalidJsonEvent() {
        // Create an invalid JSON event
        String invalidJson = "{ invalid json }";
        KinesisEvent kinesisEvent = createKinesisEvent(invalidJson);

        // Process the event
        String result = analyticsHandler.handleRequest(kinesisEvent, mockContext);

        // Verify result - should handle error gracefully
        assertEquals("Processed: 0, Errors: 1", result);
        
        // Verify error logging
        verify(mockLogger, times(1)).log("Processing 1 analytics events");
        verify(mockLogger, times(1)).log(contains("Error processing record"));
        verify(mockLogger, times(1)).log("Processed: 0, Errors: 1");
    }

    @Test
    void testHandleEmptyEvent() {
        // Create an empty event
        KinesisEvent kinesisEvent = new KinesisEvent();
        kinesisEvent.setRecords(Collections.emptyList());

        // Process the event
        String result = analyticsHandler.handleRequest(kinesisEvent, mockContext);

        // Verify result
        assertEquals("Processed: 0, Errors: 0", result);
        
        // Verify logging
        verify(mockLogger, times(1)).log("Processing 0 analytics events");
        verify(mockLogger, times(1)).log("Processed: 0, Errors: 0");
    }

    @Test
    void testDynamoDbExceptionHandling() {
        // Configure mock to throw exception
        mockDynamoDbService.setThrowException(true, "Mock DynamoDB failure");

        // Create a valid event
        String eventData = createValidAnalyticsEventJson();
        KinesisEvent kinesisEvent = createKinesisEvent(eventData);

        // Process the event
        String result = analyticsHandler.handleRequest(kinesisEvent, mockContext);

        // Verify error handling
        assertEquals("Processed: 0, Errors: 1", result);
        
        // Verify error logging
        verify(mockLogger, times(1)).log(contains("Error processing record"));
    }

    @Test
    void testValidationOfAnalyticsFields() {
        // Create event with edge case values
        String eventData = createAnalyticsEventWithEdgeCases();
        KinesisEvent kinesisEvent = createKinesisEvent(eventData);

        // Process the event
        String result = analyticsHandler.handleRequest(kinesisEvent, mockContext);

        // Should handle validation gracefully
        assertTrue(result.contains("Processed:"), "Should process event with validation");
    }

    @Test
    void testAnonymousApiKeyHandling() {
        // Create event with anonymous API key
        String eventData = createAnalyticsEventJson("anonymous", "/test", "GET", 200, 100);
        KinesisEvent kinesisEvent = createKinesisEvent(eventData);

        // Process the event
        String result = analyticsHandler.handleRequest(kinesisEvent, mockContext);

        // Verify processing
        assertEquals("Processed: 1, Errors: 0", result);
        
        // Anonymous events should still be stored but won't update developer insights
        assertTrue(mockDynamoDbService.getTableSize() > 0);
    }

    private String createValidAnalyticsEventJson() {
        return createAnalyticsEventJson("test-api-key-12345", "/items", "POST", 201, 250);
    }

    private String createAnalyticsEventJson(String apiKey, String endpoint, String method, int statusCode, int responseTime) {
        return String.format(
            "{\n" +
            "  \"eventType\": \"api_request\",\n" +
            "  \"timestamp\": \"%s\",\n" +
            "  \"endpoint\": \"%s\",\n" +
            "  \"method\": \"%s\",\n" +
            "  \"apiKey\": \"%s\",\n" +
            "  \"responseTime\": %d,\n" +
            "  \"statusCode\": %d,\n" +
            "  \"userAgent\": \"Test-Agent/1.0\"\n" +
            "}",
            Instant.now().toString(),
            endpoint,
            method,
            apiKey,
            responseTime,
            statusCode
        );
    }

    private String createAnalyticsEventWithEdgeCases() {
        return String.format(
            "{\n" +
            "  \"eventType\": \"api_request\",\n" +
            "  \"timestamp\": \"%s\",\n" +
            "  \"endpoint\": \"/very/long/endpoint/path/that/might/cause/issues\",\n" +
            "  \"method\": \"POST\",\n" +
            "  \"apiKey\": \"edge-case-key-123456789\",\n" +
            "  \"responseTime\": 5000,\n" +
            "  \"statusCode\": 500,\n" +
            "  \"userAgent\": \"Very-Long-User-Agent-String-That-Might-Be-Truncated-In-Processing\"\n" +
            "}",
            Instant.now().toString()
        );
    }

    private KinesisEvent createKinesisEvent(String data) {
        KinesisEvent event = new KinesisEvent();
        KinesisEvent.KinesisEventRecord record = new KinesisEvent.KinesisEventRecord();
        
        KinesisEvent.Record kinesisRecord = new KinesisEvent.Record();
        kinesisRecord.setData(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)));
        kinesisRecord.setPartitionKey("test-partition");
        kinesisRecord.setSequenceNumber("12345");
        
        record.setKinesis(kinesisRecord);
        record.setEventSource("aws:kinesis");
        record.setEventSourceARN("arn:aws:kinesis:us-east-1:123456789012:stream/test-stream");
        
        event.setRecords(Collections.singletonList(record));
        return event;
    }
}