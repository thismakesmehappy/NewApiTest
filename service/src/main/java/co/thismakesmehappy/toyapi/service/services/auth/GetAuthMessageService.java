package co.thismakesmehappy.toyapi.service.services.auth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for GET /auth/message endpoint.
 * Follows Amazon/Coral pattern - one service per endpoint.
 */
public class GetAuthMessageService {
    
    /**
     * Execute the GET /auth/message operation.
     * 
     * @return Response map containing authenticated message
     */
    public Map<String, Object> execute() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from authenticated ToyApi endpoint!");
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}