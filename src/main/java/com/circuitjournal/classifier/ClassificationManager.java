package com.circuitjournal.classifier;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages waste classification tracking and statistics
 */
public class ClassificationManager {
    
    // Category statistics counters
    private Map<String, Integer> categoryCounts = new ConcurrentHashMap<>();
    
    // Recent classifications for decision making
    private List<String> recentClassifications = new ArrayList<>();
    private static final int MAX_RECENT_CLASSIFICATIONS = 10;
    private static final int CONSECUTIVE_THRESHOLD = 2;
    
    // Current classification status
    private String currentClassification = null;
    private boolean classificationFinalized = false;
    
    // Classification results file
    private File statsFile;
    private static final String DEFAULT_STATS_FILENAME = "waste_classification_stats.csv";
    
    private final PythonClassifierBridge classifier;
    private Consumer<String> classificationCallback;
    
    /**
     * Create a classification manager
     * 
     * @param classifier Python classifier bridge
     * @param statsDirectory Directory to save classification statistics
     */
    public ClassificationManager(PythonClassifierBridge classifier, String statsDirectory) {
        this.classifier = classifier;
        
        // Initialize counters for all categories
        for (String category : PythonClassifierBridge.CATEGORIES) {
            categoryCounts.put(category, 0);
        }
        
        // Initialize statistics file
        if (StringUtils.isNotBlank(statsDirectory)) {
            File dir = new File(statsDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            this.statsFile = new File(dir, DEFAULT_STATS_FILENAME);
            
            // Load existing stats if the file exists
            if (statsFile.exists()) {
                loadStats();
            } else {
                // Create the header if it's a new file
                try (FileWriter writer = new FileWriter(statsFile)) {
                    writer.write("Timestamp,Category,Count\n");
                } catch (IOException e) {
                    System.err.println("Failed to create stats file: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Set a callback to be notified when classification is finalized
     */
    public void setClassificationCallback(Consumer<String> callback) {
        this.classificationCallback = callback;
    }
    
    /**
     * Classify an image and update the statistics
     * 
     * @param imagePath Path to the image
     */
    public void classifyImage(String imagePath) {
        classifier.classifyImage(imagePath).thenAccept(result -> {
            boolean success = (boolean) result.getOrDefault("success", false);
            
            if (success) {
                String category = (String) result.get("category");
                updateClassification(category);
            } else {
                String error = (String) result.getOrDefault("error", "Unknown error");
                System.err.println("Classification failed: " + error);
            }
        });
    }
    
    /**
     * Update classification with a new result
     */
    private void updateClassification(String category) {
        recentClassifications.add(category);
        if (recentClassifications.size() > MAX_RECENT_CLASSIFICATIONS) {
            recentClassifications.remove(0);
        }
        
        // Check if we should finalize the classification
        if (!classificationFinalized) {
            if (checkConsecutiveMatches() || checkTotalMatches()) {
                finalizeClassification(category);
            }
        }
    }
    
    /**
     * Check if there are consecutive matches of the same category
     */
    private boolean checkConsecutiveMatches() {
        if (recentClassifications.size() < CONSECUTIVE_THRESHOLD) {
            return false;
        }
        
        String lastCategory = recentClassifications.get(recentClassifications.size() - 1);
        String secondLastCategory = recentClassifications.get(recentClassifications.size() - 2);
        
        return lastCategory.equals(secondLastCategory);
    }
    
    /**
     * Check if the total count of a category in recent classifications reaches the threshold
     */
    private boolean checkTotalMatches() {
        if (recentClassifications.size() < MAX_RECENT_CLASSIFICATIONS) {
            return false;
        }
        
        Map<String, Integer> categoryFrequency = new HashMap<>();
        for (String category : recentClassifications) {
            categoryFrequency.put(category, categoryFrequency.getOrDefault(category, 0) + 1);
        }
        
        for (Map.Entry<String, Integer> entry : categoryFrequency.entrySet()) {
            if (entry.getValue() >= 5) { // Majority in last 10
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Finalize the classification decision
     */
    private void finalizeClassification(String category) {
        this.currentClassification = category;
        this.classificationFinalized = true;
        
        // Update category count
        int count = categoryCounts.getOrDefault(category, 0);
        categoryCounts.put(category, count + 1);
        
        // Save to file
        saveStats(category);
        
        // Notify listeners
        if (classificationCallback != null) {
            classificationCallback.accept(category);
        }
    }
    
    /**
     * Reset the current classification
     */
    public void resetClassification() {
        this.currentClassification = null;
        this.classificationFinalized = false;
        this.recentClassifications.clear();
    }
    
    /**
     * Get all category counts
     */
    public Map<String, Integer> getCategoryCounts() {
        return new HashMap<>(categoryCounts);
    }
    
    /**
     * Get current classification
     */
    public String getCurrentClassification() {
        return currentClassification;
    }
    
    /**
     * Check if classification has been finalized
     */
    public boolean isClassificationFinalized() {
        return classificationFinalized;
    }
    
    /**
     * Save classification statistics to file
     */
    private void saveStats(String category) {
        if (statsFile == null) {
            return;
        }
        
        try (FileWriter writer = new FileWriter(statsFile, true)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());
            
            writer.write(String.format("%s,%s,%d\n", 
                timestamp, category, categoryCounts.get(category)));
                
        } catch (IOException e) {
            System.err.println("Failed to save stats: " + e.getMessage());
        }
    }
    
    /**
     * Load existing stats from file
     */
    private void loadStats() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(statsFile.getPath()));
            
            // Skip header
            if (lines.size() <= 1) {
                return;
            }
            
            // Reset counters
            for (String category : PythonClassifierBridge.CATEGORIES) {
                categoryCounts.put(category, 0);
            }
            
            // Process each line
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split(",");
                
                if (parts.length >= 3) {
                    String category = parts[1];
                    
                    if (PythonClassifierBridge.CATEGORIES.contains(category)) {
                        categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load stats: " + e.getMessage());
        }
    }
}