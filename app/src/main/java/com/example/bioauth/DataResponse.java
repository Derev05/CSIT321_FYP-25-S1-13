package com.example.bioauth;

public class DataResponse {
    private String name;

    public DataResponse(String name) {
        this.name = name;
    }
    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name = name;

    }

}
