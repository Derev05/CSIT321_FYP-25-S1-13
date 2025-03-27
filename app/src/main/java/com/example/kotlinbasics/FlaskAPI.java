package com.example.kotlinbasics;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
    public interface FlaskAPI {
        @GET("/")
        Call<ApiResponse> getHomeMessage();

        @POST("/data")
        Call<ApiResponse> sendData(@Body DataResponse data);
    }

