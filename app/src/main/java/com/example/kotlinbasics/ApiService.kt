package com.example.kotlinbasics

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import com.example.kotlinbasics.LoginRequest
import com.example.kotlinbasics.LoginResponse
// Define API interface
interface ApiService {
    @GET("api/user")
    fun getUser(): Call<UserResponse>

    @POST("api/login")
    fun login(@Body loginRequest: LoginRequest): Call<LoginResponse>

}