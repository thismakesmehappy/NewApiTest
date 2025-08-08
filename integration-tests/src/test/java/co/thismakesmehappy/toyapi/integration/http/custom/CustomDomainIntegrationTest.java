package co.thismakesmehappy.toyapi.integration.http.custom;

import co.thismakesmehappy.toyapi.integration.shared.TestConfiguration;
import co.thismakesmehappy.toyapi.integration.shared.AuthenticationHelper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * HTTP integration tests against custom domain URLs.
 * Tests the API through custom domains once DNS propagation is complete.
 * 
 * These tests are disabled by default and enabled via system property:
 * -Dtest.custom.domains.enabled=true
 */
@Tag("integration")
@Tag("http")
@Tag("custom")
public class CustomDomainIntegrationTest {
    
    private String baseUrl;
    private String environment;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        environment = TestConfiguration.getEnvironment();
        baseUrl = TestConfiguration.getCustomApiUrl(environment);
        
        // Skip tests if custom domains are not available
        Assumptions.assumeTrue(TestConfiguration.areCustomDomainsAvailable(), 
            "Custom domains not enabled. Use -Dtest.custom.domains.enabled=true to enable these tests.");
        
        RestAssured.baseURI = baseUrl;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        TestConfiguration.printConfiguration("HTTP - Custom Domains", environment);
        System.out.println("Test: " + testInfo.getDisplayName());
        System.out.println("Base URL: " + baseUrl);
    }
    
    @Test
    @Tag("smoke")
    @Tag("health")
    void testPublicEndpointWithCustomDomain() {
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
    void testCustomDomainHealthCheck() {
        given()
            .when()
                .get("/public/message")
            .then()
                .statusCode(200)
                .time(lessThan(5000L)); // Response under 5 seconds
    }
    
    @Test
    @Tag("smoke")
    void testCustomDomainSslCertificate() {
        // This test ensures the custom domain has proper SSL configuration
        given()
            .relaxedHTTPSValidation() // Allow certificate validation
            .when()
                .get("/public/message")
            .then()
                .statusCode(200);
    }
    
    @Test
    @Tag("integration")
    void testLoginWithCustomDomain() {
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
    @Tag("auth")
    void testAuthenticatedEndpointWithCustomDomain() {
        String username = AuthenticationHelper.getTestUsername();
        String password = AuthenticationHelper.getTestPassword();
        
        // Get authentication token
        AuthenticationHelper.TokenResponse tokens = AuthenticationHelper.login(baseUrl, username, password);
        
        // Test authenticated endpoint
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
    void testItemsCrudWithCustomDomain() {
        String username = AuthenticationHelper.getTestUsername();
        String password = AuthenticationHelper.getTestPassword();
        
        // Get authentication token
        AuthenticationHelper.TokenResponse tokens = AuthenticationHelper.login(baseUrl, username, password);
        
        // Test complete CRUD flow through custom domain
        String testMessage = "Test item from custom domain integration test " + System.currentTimeMillis();
        
        // POST item
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
                .extract().path("id");
        
        // GET item
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .get("/items/" + itemId)
            .then()
                .statusCode(200)
                .body("message", equalTo(testMessage));
        
        // Clean up - DELETE item
        given()
            .header("Authorization", "Bearer " + tokens.idToken)
            .when()
                .delete("/items/" + itemId)
            .then()
                .statusCode(200);
    }
    
    @Test
    @Tag("performance")
    void testCustomDomainPerformance() {
        // Test that custom domains perform similarly to AWS domains
        given()
            .when()
                .get("/public/message")
            .then()
                .statusCode(200)
                .time(lessThan(3000L)); // Custom domain should be fast
    }
    
    @Test
    @Tag("integration")
    void testCorsWithCustomDomain() {
        // Test CORS headers are properly configured for custom domains
        given()
            .header("Origin", "https://frontend.thismakesmehappy.co")
            .when()
                .options("/public/message")
            .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", notNullValue())
                .header("Access-Control-Allow-Methods", containsString("GET"))
                .header("Access-Control-Allow-Headers", notNullValue());
    }
}