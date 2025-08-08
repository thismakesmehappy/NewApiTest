package co.thismakesmehappy.toyapi.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Simple persistent mock database for local development.
 * Provides basic CRUD operations with file-based persistence.
 */
public class MockDatabase {
    
    private static final Map<String, Map<String, Object>> items = new ConcurrentHashMap<>();
    private static final AtomicInteger idCounter = new AtomicInteger(1);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DATA_FILE = "/tmp/toyapi-mock-data.json";
    private static boolean loaded = false;
    
    public static class Item {
        public String id;
        public String userId;
        public String message;
        public String createdAt;
        public String updatedAt;
        
        public Item() {}
        
        public Item(String userId, String message) {
            this.id = "item-" + idCounter.getAndIncrement();
            this.userId = userId;
            this.message = message;
            this.createdAt = Instant.now().toString();
            this.updatedAt = this.createdAt;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("userId", userId);
            map.put("message", message);
            map.put("createdAt", createdAt);
            map.put("updatedAt", updatedAt);
            return map;
        }
        
        public static Item fromMap(Map<String, Object> map) {
            Item item = new Item();
            item.id = (String) map.get("id");
            item.userId = (String) map.get("userId");
            item.message = (String) map.get("message");
            item.createdAt = (String) map.get("createdAt");
            item.updatedAt = (String) map.get("updatedAt");
            return item;
        }
    }
    
    private static void loadData() {
        if (loaded) return;
        loaded = true;
        
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                Map<String, Object> data = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
                
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> itemsData = (Map<String, Map<String, Object>>) data.get("items");
                if (itemsData != null) {
                    items.putAll(itemsData);
                }
                
                Integer maxId = (Integer) data.get("maxId");
                if (maxId != null) {
                    idCounter.set(maxId + 1);
                }
            }
        } catch (Exception e) {
            // Ignore errors, start fresh
            System.err.println("Failed to load mock data: " + e.getMessage());
        }
    }
    
    private static void saveData() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("items", items);
            data.put("maxId", idCounter.get() - 1);
            
            File file = new File(DATA_FILE);
            mapper.writeValue(file, data);
        } catch (Exception e) {
            // Ignore save errors
            System.err.println("Failed to save mock data: " + e.getMessage());
        }
    }
    
    public static String createItem(String userId, String message) {
        loadData();
        Item item = new Item(userId, message);
        items.put(item.id, item.toMap());
        saveData();
        return item.id;
    }
    
    public static Optional<Item> getItem(String itemId, String userId) {
        loadData();
        Map<String, Object> itemData = items.get(itemId);
        if (itemData == null) {
            return Optional.empty();
        }
        
        Item item = Item.fromMap(itemData);
        // Check if the item belongs to the user
        if (!userId.equals(item.userId)) {
            return Optional.empty();
        }
        
        return Optional.of(item);
    }
    
    public static List<Item> getUserItems(String userId) {
        loadData();
        return items.values().stream()
                .map(Item::fromMap)
                .filter(item -> userId.equals(item.userId))
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt)) // Newest first
                .collect(Collectors.toList());
    }
    
    public static boolean updateItem(String itemId, String userId, String message) {
        loadData();
        Map<String, Object> itemData = items.get(itemId);
        if (itemData == null) {
            return false;
        }
        
        Item item = Item.fromMap(itemData);
        // Check if the item belongs to the user
        if (!userId.equals(item.userId)) {
            return false;
        }
        
        // Update the item
        item.message = message;
        item.updatedAt = Instant.now().toString();
        items.put(itemId, item.toMap());
        saveData();
        return true;
    }
    
    public static boolean deleteItem(String itemId, String userId) {
        loadData();
        Map<String, Object> itemData = items.get(itemId);
        if (itemData == null) {
            return false;
        }
        
        Item item = Item.fromMap(itemData);
        // Check if the item belongs to the user
        if (!userId.equals(item.userId)) {
            return false;
        }
        
        items.remove(itemId);
        saveData();
        return true;
    }
    
    public static void clear() {
        items.clear();
        idCounter.set(1);
        saveData();
    }
    
    public static int size() {
        loadData();
        return items.size();
    }
}