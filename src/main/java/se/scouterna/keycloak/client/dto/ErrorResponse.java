package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {
    @JsonProperty("error")
    private String error;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("code")
    private String code;

    // Getters and Setters
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getSafeErrorMessage() {
        if (message != null && !message.trim().isEmpty()) {
            return message.length() > 100 ? message.substring(0, 100) + "..." : message;
        }
        if (error != null && !error.trim().isEmpty()) {
            return error.length() > 100 ? error.substring(0, 100) + "..." : error;
        }
        return "Unknown error";
    }
}