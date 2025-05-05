package com.circuitjournal.serialreader;

import java.io.OutputStream;
import java.util.List;

public interface SerialReader {

    interface SerialDataReceived {
        void serialDataReceived(byte [] bytes);
    }

    void setReceivedDataHandler(SerialDataReceived callback);

    List<String> getAvailablePorts();
    List<Integer> getAvailableBaudRates();
    Integer getDefaultBaudRate(Integer overrideBaudRate);

    void startListening(String portName, Integer baudRate);
    void stopListening();
    boolean isListening();
    
    /**
     * Get the output stream for sending data to the device
     * @return OutputStream or null if not connected
     */
    OutputStream getOutputStream();
}