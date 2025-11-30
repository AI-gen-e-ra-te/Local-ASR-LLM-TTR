package com.example.bailiandemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FunASRClient {

    private static final String WS_URL = "ws://localhost:10095";
    private final ObjectMapper mapper = new ObjectMapper();

    public String audioToText(byte[] audioBytes) throws Exception {
        // 使用 CompletableFuture 来同步等待 WebSocket 的结果
        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        WebSocketClient client = new WebSocketClient(new URI(WS_URL)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("FunASR Connected");
                // 连接成功后，发送音频数据
                send(audioBytes);
                // 发送结束标志（有些服务器需要，对于我们的简单 Python 脚本可能发完就等着就行）
            }

            @Override
            public void onMessage(String message) {
                System.out.println("FunASR Message: " + message);
                try {
                    JsonNode root = mapper.readTree(message);
                    if (root.has("text")) {
                        String text = root.get("text").asText();
                        // 只要拿到结果，就完成 Future，并关闭连接
                        resultFuture.complete(text);
                        close();
                    } else if (root.has("error")) {
                        resultFuture.completeExceptionally(new RuntimeException(root.get("error").asText()));
                        close();
                    }
                } catch (Exception e) {
                    resultFuture.completeExceptionally(e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("FunASR Closed: " + reason);
                if (!resultFuture.isDone()) {
                    resultFuture.completeExceptionally(new RuntimeException("Connection closed before result: " + reason));
                }
            }

            @Override
            public void onError(Exception ex) {
                System.out.println("FunASR Error: " + ex.getMessage());
                resultFuture.completeExceptionally(ex);
            }
        };

        // 连接并等待
        boolean connected = client.connectBlocking(5, TimeUnit.SECONDS);
        if (!connected) {
            throw new RuntimeException("无法连接到本地 FunASR 服务 (ws://localhost:10095)");
        }

        // 等待结果（最多等 30 秒）
        try {
            return resultFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            client.close();
            throw e;
        }
    }
}

