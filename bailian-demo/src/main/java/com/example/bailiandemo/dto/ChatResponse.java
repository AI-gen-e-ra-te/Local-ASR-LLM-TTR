package com.example.bailiandemo.dto;

public class ChatResponse {

    private String replyText;
    private String audioUrl;
    private String recognizedText; // 新增：识别到的用户语音文本

    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getRecognizedText() { return recognizedText; }
    public void setRecognizedText(String recognizedText) { this.recognizedText = recognizedText; }
}

