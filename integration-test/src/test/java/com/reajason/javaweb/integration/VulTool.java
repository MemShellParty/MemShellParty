package com.reajason.javaweb.integration;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author ReaJason
 * @since 2024/11/30
 */
@Slf4j
public class VulTool {

    /**
     * CI runner 上的老旧服务器（WebLogic 10.3.6 等）类定义 + 静态初始化可能超过 OkHttp 默认的
     * 10s 读超时（SocketTimeoutException），显式放宽到 60s。
     */
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @SneakyThrows
    public static void urlIsOk(String url) {
        Request request = new Request.Builder()
                .url(url).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            System.out.println(response.body().string());
            Assertions.assertTrue(response.isSuccessful());
        }
    }


    @SneakyThrows
    public static void uploadJspFileToServer(String uploadUrl, String filename, String fileContent) {
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody fileRequestBody = RequestBody.create(fileContent, mediaType);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileRequestBody)
                .build();
        Request request = new Request.Builder()
                .url(uploadUrl).post(requestBody)
                .header("Referer", uploadUrl)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            System.out.println(response.body().string());
            assertEquals(200, response.code());
        }
    }

    @SneakyThrows
    public static String postIsOk(String uploadUrl, String data) {
        RequestBody requestBody = new FormBody.Builder()
                .add("data", data)
                .build();
        return postIsOk(uploadUrl, requestBody);
    }

    public static String postRawIsOk(String uploadUrl, String data) {
        RequestBody requestBody = RequestBody.create(data, MediaType.parse("application/octet-stream"));
        return postIsOk(uploadUrl, requestBody);
    }

    @SneakyThrows
    private static String postIsOk(String uploadUrl, RequestBody requestBody) {
        Request request = new Request.Builder()
                .header("Referer", uploadUrl)
                .url(uploadUrl).post(requestBody)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String res = response.body().string();
            System.out.println(res);
            Assertions.assertNotEquals(404, response.code());
            return res;
        }
    }

    @SneakyThrows
    public static String post(String uploadUrl, String data) {
        RequestBody requestBody = new FormBody.Builder()
                .add("data", data)
                .build();
        Request request = new Request.Builder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", uploadUrl)
                .url(uploadUrl).post(requestBody)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            return response.body().string();
        }
    }

    @SneakyThrows
    public static void xxlJobHessianExecutor(String url, String base64Bytes) {
        byte[] requestBytes = Base64.getDecoder().decode(base64Bytes);
        OkHttpClient client = CLIENT;
        RequestBody body = RequestBody.create(requestBytes, MediaType.parse("application/octet-stream"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Connection", "close")
                .build();
        log.info("sending hessian2 xxl-rpc request to: {}", url);
        try (Response response = client.newCall(request).execute()) {
            log.info("xxl-rpc hessian2 response code: {}", response.code());
            Thread.sleep(1000);
        }
    }

    @SneakyThrows
    public static void xxlJobExecutor(String url, String data) {
        OkHttpClient client = CLIENT;
        log.info(data);
        RequestBody body = RequestBody.create(data, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Connection", "close")
                .addHeader("XXL-JOB-ACCESS-TOKEN", "default_token")
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            Thread.sleep(1000); // wait for job execute
            log.info(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
