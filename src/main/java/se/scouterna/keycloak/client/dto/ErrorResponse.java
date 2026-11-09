package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body returned by the Scoutnet API.
 * Scoutnet puts its reason in {@code err}, e.g.
 * {@code {"err":"Incorrect login: Invalid username or password"}};
 * {@code message} and {@code error} are kept as fallbacks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {
    private static final int MAX_LENGTH = 100;

    @JsonProperty("err")
    private String err;

    @JsonProperty("error")
    private String error;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("code")
    private String code;

    // Getters and Setters
    public String getErr() { return err; }
    public void setErr(String err) { this.err = err; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getSafeErrorMessage() {
        for (String candidate : new String[] { err, message, error }) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.length() > MAX_LENGTH ? candidate.substring(0, MAX_LENGTH) + "..." : candidate;
            }
        }
        return "Unknown error";
    }
}
