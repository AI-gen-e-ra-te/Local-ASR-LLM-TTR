package com.example.bailiandemo.controller;

import com.example.bailiandemo.dto.ChatTextRequest;
import com.example.bailiandemo.dto.ChatResponse;
import com.example.bailiandemo.service.QwenClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin
@RequestMapping("/api/chat")
public class ChatController {

    private final QwenClient qwenClient = new QwenClient();

    @PostMapping("/text")
    public ChatResponse chatByText(@RequestBody ChatTextRequest request) throws Exception {

        // 1. 调用 LLM
        String reply = qwenClient.chatWithQwen(request.getText());

        // 2. 调用 TTS（返回 URL）
        String audioUrl = qwenClient.ttsToAudioUrl(reply);

        // 3. 组装响应
        ChatResponse resp = new ChatResponse();
        resp.setReplyText(reply);
        resp.setAudioUrl(audioUrl);
        return resp;
    }

    @PostMapping("/audio")
    public ChatResponse chatByAudio(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的音频文件为空");
        }

        // 1. ASR (语音 -> 文字)
        String userText = qwenClient.audioToText(file.getBytes());
        // 如果识别结果为空，给个默认处理
        if (userText == null || userText.trim().isEmpty()) {
            userText = "(未识别到语音内容)";
        }

        // 2. LLM (文字 -> 文字)
        String reply = qwenClient.chatWithQwen(userText);

        // 3. TTS (文字 -> 语音URL)
        String audioUrl = qwenClient.ttsToAudioUrl(reply);

        // 4. 组装响应
        ChatResponse resp = new ChatResponse();
        resp.setRecognizedText(userText); // 告诉前端识别到了啥
        resp.setReplyText(reply);
        resp.setAudioUrl(audioUrl);
        return resp;
    }
}
