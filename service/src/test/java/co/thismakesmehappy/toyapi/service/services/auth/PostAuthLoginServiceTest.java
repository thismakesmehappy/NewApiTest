package co.thismakesmehappy.toyapi.service.services.auth;

import co.thismakesmehappy.toyapi.service.utils.CognitoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PostAuthLoginService.
 * Tests the business logic for POST /auth/login endpoint.
 */
class PostAuthLoginServiceTest {

    private CognitoService mockCognitoService;
    private PostAuthLoginService service;

    @BeforeEach
    void setUp() {
        mockCognitoService = mock(CognitoService.class);
        service = new PostAuthLoginService(mockCognitoService, "test-client-id", false);
    }

    @Test
    void testExecuteSuccessfulLogin() throws Exception {
        // Setup mock response
        AuthenticationResultType authResult = AuthenticationResultType.builder()
            .idToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMzQ1In0.signature")
            .accessToken("access-token")
            .expiresIn(3600)
            .build();
            
        InitiateAuthResponse authResponse = InitiateAuthResponse.builder()
            .authenticationResult(authResult)
            .build();
            
        when(mockCognitoService.initiateAuth(any(InitiateAuthRequest.class)))
            .thenReturn(authResponse);
        
        // Create request
        PostAuthLoginService.LoginRequest request = 
            new PostAuthLoginService.LoginRequest("testuser", "testpass");
        
        // Execute
        Map<String, Object> result = service.execute(request);
        
        // Verify response
        assertNotNull(result);
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMzQ1In0.signature", result.get("token"));
        assertEquals("access-token", result.get("accessToken"));
        assertEquals("user-12345", result.get("userId"));
        assertEquals(3600, result.get("expiresIn"));
        
        // Verify Cognito was called
        verify(mockCognitoService).initiateAuth(any(InitiateAuthRequest.class));
    }

    @Test
    void testExecuteWithMockAuthentication() throws Exception {
        // Create service with mock authentication enabled
        PostAuthLoginService mockService = 
            new PostAuthLoginService(mockCognitoService, "test-client-id", true);
        
        // Create request
        PostAuthLoginService.LoginRequest request = 
            new PostAuthLoginService.LoginRequest("testuser", "testpass");
        
        // Execute
        Map<String, Object> result = mockService.execute(request);
        
        // Verify mock response
        assertNotNull(result);
        assertEquals("mock-id-token-testuser", result.get("token"));
        assertEquals("mock-access-token-testuser", result.get("accessToken"));
        assertEquals("mock-user-testuser", result.get("userId"));
        assertEquals(3600, result.get("expiresIn"));
        
        // Verify Cognito was NOT called
        verify(mockCognitoService, never()).initiateAuth(any(InitiateAuthRequest.class));
    }

    @Test
    void testExecuteInvalidCredentials() {
        // Setup mock to throw NotAuthorizedException
        when(mockCognitoService.initiateAuth(any(InitiateAuthRequest.class)))
            .thenThrow(NotAuthorizedException.builder()
                .message("Invalid credentials")
                .build());
        
        // Create request
        PostAuthLoginService.LoginRequest request = 
            new PostAuthLoginService.LoginRequest("testuser", "wrongpass");
        
        // Execute and verify exception
        Exception exception = assertThrows(Exception.class, () -> {
            service.execute(request);
        });
        
        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    void testExecuteAuthChallenge() {
        // Setup mock to return challenge (no authentication result)
        InitiateAuthResponse authResponse = InitiateAuthResponse.builder()
            .challengeName("NEW_PASSWORD_REQUIRED")
            .build();
            
        when(mockCognitoService.initiateAuth(any(InitiateAuthRequest.class)))
            .thenReturn(authResponse);
        
        // Create request
        PostAuthLoginService.LoginRequest request = 
            new PostAuthLoginService.LoginRequest("testuser", "testpass");
        
        // Execute and verify exception
        Exception exception = assertThrows(Exception.class, () -> {
            service.execute(request);
        });
        
        assertEquals("Authentication failed: Authentication challenge not supported", exception.getMessage());
    }
}