package co.thismakesmehappy.toyapi.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Tag;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for ToyApi endpoints across different environments.
 * Tests run against live AWS infrastructure to validate end-to-end functionality.
 */
public class ApiIntegrationTest {
    
    private String baseUrl;
    private String environment;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Get environment from system property, default to dev
        environment = System.getProperty("test.environment", "dev");
        
        // Set base URL based on environment - using stable AWS URLs (custom domains pending DNS)
        switch (environment.toLowerCase()) {
            case "prod":
            case "production":
                baseUrl = "https://55g7hsw2c1.execute-api.us-east-1.amazonaws.com/prod";
                break;
            case "stage":
            case "staging":
                baseUrl = "https://5ytzml6fnb.execute-api.us-east-1.amazonaws.com/stage";
                break;
            case "dev":
            case "development":
            default:
                baseUrl = "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev";
                break;
        }
        
        RestAssured.baseURI = baseUrl;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        System.out.println("Running integration tests against: " + baseUrl + " (environment: " + environment + ")");
        System.out.println("Test: " + testInfo.getDisplayName());
    }
    
    @Test
    @Tag("smoke")
    @Tag("health")
    void testPublicEndpoint() {
        given()
            .when()
                .get("/public/message")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", containsString("Hello from ToyApi public endpoint"))
                .body("message", containsString("Environment: " + environment))
                .body("timestamp", notNullValue());
    }
    
    @Test
    @Tag("smoke")
    @Tag("health")
    void testAuthenticationRequired() {
        // Test that auth endpoints require authentication
        given()
            .when()
                .get("/auth/message")
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }
    
    @Test
    @Tag("integration")
    void testItemsEndpointRequiresAuth() {
        // Test that items endpoints require authentication
        given()
            .when()
                .get("/items")
            .then()
                .statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }
    
    @Test
    @Tag("integration")
    void testLoginEndpoint() {
        // Test login with test credentials
        given()
            .contentType(ContentType.JSON)
            .body("{\n" +
                  "  \"username\": \"testuser\",\n" +
                  "  \"password\": \"TestPassword123\"\n" +
                  "}")
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("idToken", notNullValue())
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600));
    }
    
    @Test
    @Tag("integration")
    void testInvalidLogin() {
        // Test login with invalid credentials
        given()
            .contentType(ContentType.JSON)
            .body("{\n" +
                  "  \"username\": \"invaliduser\",\n" +
                  "  \"password\": \"wrongpassword\"\n" +
                  "}")
            .when()
                .post("/auth/login")
            .then()
                .statusCode(401)
                .body("error", equalTo("UNAUTHORIZED"))
                .body("message", equalTo("Invalid credentials"));
    }
    
    @Test
    @Tag("health")
    @Tag("smoke")
    void testHealthCheck() {
        // Basic connectivity test
        given()
            .when()
                .get("/public/message")
            .then()
                .statusCode(200)
                .time(lessThan(5000L)); // Response under 5 seconds
    }
}