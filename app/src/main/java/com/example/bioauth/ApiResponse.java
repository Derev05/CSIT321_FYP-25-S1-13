package com.example.bioauth;

public class ApiResponse {
    private String message;
    private String response;
    public ApiResponse (String message, String response){
        this.message = message;
        this.response = response;
    }
    public String getMessage() {
        return message;
    }
    public String getResponse(){
        return response;
    }

}
