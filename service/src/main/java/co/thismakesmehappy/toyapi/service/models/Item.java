package co.thismakesmehappy.toyapi.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Item data model supporting individual and team-based ownership
 */
public class Item {
    
    private String id;
    private String message;
    
    // Ownership model - can be individual OR team-based
    private String userId;           // Owner user ID (always set)
    private String teamId;           // Team ID (optional - null for individual items)
    private AccessLevel accessLevel; // INDIVIDUAL, TEAM, or PUBLIC
    
    // Metadata
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;        // User who created the item (for audit trail)
    
    public enum AccessLevel {
        @JsonProperty("individual")
        INDIVIDUAL,  // Only the owner can access
        
        @JsonProperty("team") 
        TEAM,        // All team members can access
        
        @JsonProperty("public")
        PUBLIC       // All authenticated users can access (future use)
    }
    
    // Constructors
    public Item() {}
    
    public Item(String id, String message, String userId, AccessLevel accessLevel) {
        this.id = id;
        this.message = message;
        this.userId = userId;
        this.accessLevel = accessLevel;
        this.createdBy = userId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Builder pattern for easy construction
    public static class Builder {
        private String id;
        private String message;
        private String userId;
        private String teamId;
        private AccessLevel accessLevel = AccessLevel.INDIVIDUAL;
        private String createdBy;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            this.createdBy = userId; // Default creator to owner
            return this;
        }
        
        public Builder teamId(String teamId) {
            this.teamId = teamId;
            if (teamId != null) {
                this.accessLevel = AccessLevel.TEAM;
            }
            return this;
        }
        
        public Builder accessLevel(AccessLevel accessLevel) {
            this.accessLevel = accessLevel;
            return this;
        }
        
        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }
        
        public Item build() {
            Item item = new Item();
            item.id = this.id;
            item.message = this.message;
            item.userId = this.userId;
            item.teamId = this.teamId;
            item.accessLevel = this.accessLevel;
            item.createdBy = this.createdBy;
            item.createdAt = Instant.now();
            item.updatedAt = Instant.now();
            return item;
        }
    }
    
    // Helper methods
    public boolean isTeamItem() {
        return teamId != null && accessLevel == AccessLevel.TEAM;
    }
    
    public boolean isIndividualItem() {
        return accessLevel == AccessLevel.INDIVIDUAL;
    }
    
    public boolean isPublicItem() {
        return accessLevel == AccessLevel.PUBLIC;
    }
    
    public void touch() {
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    
    public AccessLevel getAccessLevel() { return accessLevel; }
    public void setAccessLevel(AccessLevel accessLevel) { this.accessLevel = accessLevel; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    @Override
    public String toString() {
        return "Item{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                ", userId='" + userId + '\'' +
                ", teamId='" + teamId + '\'' +
                ", accessLevel=" + accessLevel +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }
}