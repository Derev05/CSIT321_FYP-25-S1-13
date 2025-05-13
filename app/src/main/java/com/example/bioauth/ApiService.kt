package com.example.bioauth

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Define API interface
interface ApiService {
    @GET("api/user")
    fun getUser(): Call<UserResponse>

    @POST("api/login")
    fun login(@Body loginRequest: LoginRequest): Call<LoginResponse>

}