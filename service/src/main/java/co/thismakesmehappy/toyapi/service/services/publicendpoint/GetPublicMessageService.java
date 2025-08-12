package co.thismakesmehappy.toyapi.service.services.publicendpoint;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for GET /public/message endpoint.
 * Follows Amazon/Coral pattern - one service per endpoint.
 */
public class GetPublicMessageService {
    
    private final String environment;
    
    public GetPublicMessageService(String environment) {
        this.environment = environment;
    }
    
    /**
     * Execute the GET /public/message operation.
     * 
     * @return Response map containing message and timestamp
     */
    public Map<String, Object> execute() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from ToyApi public endpoint! Environment: " + environment);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}