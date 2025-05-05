package com.circuitjournal.serialreader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles communication with Arduino
 */
public class ArduinoCommunicator {
    
    // Command prefixes for Arduino communications
    private static final byte COMMAND_PREFIX = (byte) 0xAA;
    private static final byte COMMAND_CLASSIFICATION_RESULT = (byte) 0x01;
    
    // Categories and their codes for Arduino
    private static final byte CATEGORY_PAPER = (byte) 0x01;
    private static final byte CATEGORY_GLASS = (byte) 0x02;
    private static final byte CATEGORY_METAL = (byte) 0x03;
    private static final byte CATEGORY_PLASTIC = (byte) 0x04;
    private static final byte CATEGORY_TRASH = (byte) 0x05;
    private static final byte CATEGORY_UNKNOWN = (byte) 0xFF;
    
    private SerialReader serialReader;
    
    /**
     * Create Arduino communicator
     * 
     * @param serialReader Serial reader for Arduino communication
     */
    public ArduinoCommunicator(SerialReader serialReader) {
        this.serialReader = serialReader;
    }
    
    /**
     * Send classification result to Arduino
     * 
     * @param category Classification category
     * @return true if sent successfully
     */
    public boolean sendClassificationResult(String category) {
        if (serialReader == null || !serialReader.isListening()) {
            return false;
        }
        
        byte categoryCode = getCategoryCode(category);
        
        try {
            OutputStream outputStream = serialReader.getOutputStream();
            if (outputStream != null) {
                // Protocol: PREFIX, COMMAND, CATEGORY, CHECKSUM
                byte checksum = (byte) (COMMAND_CLASSIFICATION_RESULT ^ categoryCode);
                
                outputStream.write(COMMAND_PREFIX);
                outputStream.write(COMMAND_CLASSIFICATION_RESULT);
                outputStream.write(categoryCode);
                outputStream.write(checksum);
                outputStream.flush();
                
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error sending data to Arduino: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Send text message to Arduino
     * 
     * @param message Message to send
     * @return true if sent successfully
     */
    public boolean sendTextMessage(String message) {
        if (serialReader == null || !serialReader.isListening()) {
            return false;
        }
        
        try {
            OutputStream outputStream = serialReader.getOutputStream();
            if (outputStream != null) {
                // Convert message to bytes
                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                
                // Send message with newline
                outputStream.write(messageBytes);
                outputStream.write('\n');
                outputStream.flush();
                
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error sending message to Arduino: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get category code for Arduino
     */
    private byte getCategoryCode(String category) {
        if (category == null) {
            return CATEGORY_UNKNOWN;
        }
        
        switch (category.toLowerCase()) {
            case "paper":
                return CATEGORY_PAPER;
            case "glass":
                return CATEGORY_GLASS;
            case "metal":
                return CATEGORY_METAL;
            case "plastic":
                return CATEGORY_PLASTIC;
            case "trash":
                return CATEGORY_TRASH;
            default:
                return CATEGORY_UNKNOWN;
        }
    }
}