package com.circuitjournal.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bridge to call Python classification script from Java
 */
public class PythonClassifierBridge {
    
    private String pythonExecutable;
    private String scriptPath;
    private String modelPath;
    private ObjectMapper mapper = new ObjectMapper();
    
    // Categories for waste classification
    public static final List<String> CATEGORIES = Arrays.asList("Paper", "Glass", "Metal", "Plastic", "Trash");
    
    /**
     * Creates a bridge to the Python classifier
     * 
     * @param pythonExecutable Path to Python executable (e.g., "python" or "python3")
     * @param scriptPath Path to the Python classifier script
     * @param modelPath Path to the H5 model file
     */
    public PythonClassifierBridge(String pythonExecutable, String scriptPath, String modelPath) {
        this.pythonExecutable = pythonExecutable;
        this.scriptPath = scriptPath;
        this.modelPath = modelPath;
        
        validateSetup();
    }
    
    /**
     * Validate that the Python setup is correct
     */
    private void validateSetup() {
        File script = new File(scriptPath);
        if (!script.exists()) {
            throw new RuntimeException("Python script not found: " + scriptPath);
        }
        
        File model = new File(modelPath);
        if (!model.exists()) {
            throw new RuntimeException("Model file not found: " + modelPath);
        }
    }
    
    /**
     * Classify an image using the Python classifier
     * 
     * @param imagePath Path to the image file
     * @return Classification result map
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> classifyImage(String imagePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonExecutable,
                    scriptPath,
                    modelPath,
                    imagePath
                );
                
                Process process = processBuilder.start();
                
                // Read the output
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }
                
                // Check for errors
                StringBuilder error = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "Python script exited with code " + exitCode + ": " + error.toString());
                    return errorResult;
                }
                
                if (StringUtils.isBlank(output)) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "No output from Python script");
                    return errorResult;
                }
                
                return mapper.readValue(output.toString(), Map.class);
                
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "Error running Python script: " + e.getMessage());
                return errorResult;
            }
        });
    }
}