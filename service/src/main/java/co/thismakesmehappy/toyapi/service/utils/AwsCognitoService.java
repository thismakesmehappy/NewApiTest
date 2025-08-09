package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

/**
 * AWS implementation of CognitoService.
 * Wraps the AWS Cognito Identity Provider client for dependency injection.
 */
public class AwsCognitoService implements CognitoService {
    
    private final CognitoIdentityProviderClient cognitoClient;
    
    public AwsCognitoService(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }
    
    @Override
    public InitiateAuthResponse initiateAuth(InitiateAuthRequest request) {
        return cognitoClient.initiateAuth(request);
    }
    
    @Override
    public RespondToAuthChallengeResponse respondToAuthChallenge(RespondToAuthChallengeRequest request) {
        return cognitoClient.respondToAuthChallenge(request);
    }
    
    @Override
    public GetUserResponse getUser(GetUserRequest request) {
        return cognitoClient.getUser(request);
    }
    
    @Override
    public ListUsersResponse listUsers(ListUsersRequest request) {
        return cognitoClient.listUsers(request);
    }
    
    @Override
    public AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest request) {
        return cognitoClient.adminCreateUser(request);
    }
    
    @Override
    public AdminSetUserPasswordResponse adminSetUserPassword(AdminSetUserPasswordRequest request) {
        return cognitoClient.adminSetUserPassword(request);
    }
    
    @Override
    public AdminInitiateAuthResponse adminInitiateAuth(AdminInitiateAuthRequest request) {
        return cognitoClient.adminInitiateAuth(request);
    }
}