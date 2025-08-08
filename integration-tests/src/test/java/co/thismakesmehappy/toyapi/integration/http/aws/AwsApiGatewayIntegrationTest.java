package co.thismakesmehappy.toyapi.integration.http.aws;

import co.thismakesmehappy.toyapi.integration.shared.TestConfiguration;
import co.thismakesmehappy.toyapi.integration.shared.AuthenticationHelper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Tag;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * HTTP integration tests against AWS API Gateway URLs.
 * Tests the actual AWS infrastructure using AWS-generated API Gateway domains.
 */
@Tag("integration")
@Tag("http")
@Tag("aws")
public class AwsApiGatewayIntegrationTest {
    
    private String baseUrl;
    private String environment;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        environment = TestConfiguration.getEnvironment();
        baseUrl = TestConfiguration.getAwsApiUrl(environment);
        
        RestAssured.baseURI = baseUrl;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        TestConfiguration.printConfiguration("HTTP - AWS API Gateway", environment);
        System.out.println("Test: " + testInfo.getDisplayName());
        System.out.println("Base URL: " + baseUrl);
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
    void testHealthCheck() {
        given()
            .when()
                .get("/public/message")
            .then()
                .statusCode(200)
                .time(lessThan(5000L)); // Response under 5 seconds
    }
    
    @Test
    @Tag("smoke")
    @Tag("health")
    void testAuthenticationRequired() {
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
        String username = AuthenticationHelper.getTestUsername();
        String password = AuthenticationHelper.getTestPassword();
        
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
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
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\": \"invaliduser\", \"password\": \"wrongpassword\"}")
            .when()
                .post("/auth/login")
            .then()
                .statusCode(401)
                .body("error", equalTo("UNAUTHORIZED"))
                .body("message", equalTo("Invalid credentials"));
    }
    
    @Test
    @Tag("integration")
    @Tag("auth")
    void testAuthenticatedEndpoint() {
        String username = AuthenticationHelper.getTestUsername();
        String password = AuthenticationHelper.getTestPassword();
        
        // Get authentication token
        AuthenticationHelper.TokenResponse tokens = AuthenticationHelper.login(baseUrl, username, password);
        
        // Test authenticated endpoint with idToken
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .get("/auth/message")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", containsString("Hello authenticated user"))
                .body("environment", equalTo(environment));
    }
    
    @Test
    @Tag("integration")
    @Tag("crud")
    void testItemsCrudFlow() {
        String username = AuthenticationHelper.getTestUsername();
        String password = AuthenticationHelper.getTestPassword();
        
        // Get authentication token
        AuthenticationHelper.TokenResponse tokens = AuthenticationHelper.login(baseUrl, username, password);
        
        // Test GET items (should be empty initially or contain existing items)
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .get("/items")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("items", notNullValue());
        
        // Test POST item
        String testMessage = "Test item from AWS API Gateway integration test " + System.currentTimeMillis();
        String itemId = given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .contentType(ContentType.JSON)
            .body("{\"message\": \"" + testMessage + "\"}")
            .when()
                .post("/items")
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("message", equalTo(testMessage))
                .body("createdAt", notNullValue())
                .extract().path("id");
        
        // Test GET specific item
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .get("/items/" + itemId)
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(itemId))
                .body("message", equalTo(testMessage))
                .body("createdAt", notNullValue());
        
        // Test PUT item
        String updatedMessage = "Updated test item " + System.currentTimeMillis();
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .contentType(ContentType.JSON)
            .body("{\"message\": \"" + updatedMessage + "\"}")
            .when()
                .put("/items/" + itemId)
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(itemId))
                .body("message", equalTo(updatedMessage));
        
        // Test DELETE item
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .delete("/items/" + itemId)
            .then()
                .statusCode(200)
                .body("message", containsString("Item deleted successfully"));
        
        // Verify item is deleted
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .get("/items/" + itemId)
            .then()
                .statusCode(404)
                .body("error", equalTo("ITEM_NOT_FOUND"));
    }
}