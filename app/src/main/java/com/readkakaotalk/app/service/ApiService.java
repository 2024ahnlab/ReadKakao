package com.readkakaotalk.app.service;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import okhttp3.RequestBody;

public interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("/predict")
    Call<ResponseBody> sendText(@Body RequestBody text);
}
