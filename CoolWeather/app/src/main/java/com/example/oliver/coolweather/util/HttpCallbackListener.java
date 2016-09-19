package com.example.oliver.coolweather.util;

/**
 * Created by Oliver on 2016/6/16.
 */
public interface HttpCallbackListener {
    void onFinish(String response);
    void onError(Exception e);
}
