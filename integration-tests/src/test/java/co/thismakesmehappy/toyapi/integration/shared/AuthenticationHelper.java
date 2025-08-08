package co.thismakesmehappy.toyapi.integration.shared;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/**
 * Shared authentication utilities for HTTP-based integration tests.
 * Handles JWT token acquisition and management.
 */
public class AuthenticationHelper {
    
    /**
     * Login and get JWT tokens for testing authenticated endpoints
     */
    public static class TokenResponse {
        public final String idToken;
        public final String accessToken;
        public final String tokenType;
        public final int expiresIn;
        
        public TokenResponse(String idToken, String accessToken, String tokenType, int expiresIn) {
            this.idToken = idToken;
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
        }
    }
    
    /**
     * Perform login and extract tokens
     */
    public static TokenResponse login(String baseUrl, String username, String password) {
        Response response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .extract().response();
        
        return new TokenResponse(
            response.path("idToken"),
            response.path("accessToken"), 
            response.path("tokenType"),
            response.path("expiresIn")
        );
    }
    
    /**
     * Get test credentials from system properties or defaults
     */
    public static String getTestUsername() {
        return System.getProperty("test.username", "testuser");
    }
    
    public static String getTestPassword() {
        return System.getProperty("test.password", "TestPassword123");
    }
}