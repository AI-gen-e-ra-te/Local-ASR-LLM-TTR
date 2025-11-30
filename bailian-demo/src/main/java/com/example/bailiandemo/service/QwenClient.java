package com.example.bailiandemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class QwenClient {

    // === 开关：是否使用本地 LLM (Ollama) ===
    private static final boolean USE_LOCAL_LLM = true;
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String LOCAL_MODEL_NAME = "qwen2.5:7b";

    // === 开关：是否使用本地 ASR (FunASR) ===
    private static final boolean USE_LOCAL_ASR = true;
    private final FunASRClient funASRClient = new FunASRClient();

    // === 开关：是否使用本地 TTS (GPT-SoVITS) ===
    private static final boolean USE_LOCAL_TTS = true;
    private static final String LOCAL_TTS_URL = "http://localhost:9880/tts";

    // TODO: 用户必须配置以下路径和文本，才能使用 GPT-SoVITS 的 Zero-Shot 功能
    // 这是一个绝对路径或相对 Python 服务的路径的示例
    private static final String REF_AUDIO_PATH = "C:/Users/Paradox/Desktop/数字人项目/demoLLM_TTS/ref_audio.wav";
    private static final String PROMPT_TEXT = "你好，我是参考音频。"; // 参考音频里说的话
    private static final String PROMPT_LANG = "zh";


    private static final String CHAT_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private static final String TTS_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    private static final String ASR_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/generation";

    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private void checkApiKey() {
        if (!USE_LOCAL_LLM && (API_KEY == null || API_KEY.isEmpty())) {
            throw new IllegalStateException("环境变量 DASHSCOPE_API_KEY 未配置！(使用云端服务时必须配置)");
        }
    }

    /**
     * 调用 LLM API
     */
    public String chatWithQwen(String userText) throws Exception {
        if (USE_LOCAL_LLM) {
            return chatWithLocalOllama(userText);
        } else {
            return chatWithAliyunQwen(userText);
        }
    }

    // 原始阿里云调用逻辑
    private String chatWithAliyunQwen(String userText) throws Exception {
        checkApiKey();

        String body = """
                {
                  "model": "qwen-plus",
                  "messages": [
                    { "role": "system", "content": "You are a helpful assistant." },
                    { "role": "user", "content": %s }
                  ]
                }
                """.formatted(mapper.writeValueAsString(userText));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    // 新增：调用本地 Ollama
    private String chatWithLocalOllama(String userText) throws Exception {
        String body = """
                {
                  "model": "%s",
                  "prompt": %s,
                  "stream": false
                }
                """.formatted(LOCAL_MODEL_NAME, mapper.writeValueAsString(userText));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        
        if (root.has("error")) {
             throw new RuntimeException("Ollama Error: " + root.get("error").asText());
        }
        return root.path("response").asText();
    }

    /**
     * 调用 TTS API（返回音频 URL）
     */
    public String ttsToAudioUrl(String text) throws Exception {
        if (USE_LOCAL_TTS) {
            return ttsToAudioLocal(text);
        } else {
            return ttsToAudioCloud(text);
        }
    }

    /**
     * 调用本地 GPT-SoVITS 服务
     */
    private String ttsToAudioLocal(String text) throws Exception {
        // 构建 GPT-SoVITS 需要的 JSON Body
        // 这里的参数需要根据实际情况调整，特别是 ref_audio_path
        String body = """
                {
                  "text": %s,
                  "text_lang": "zh",
                  "ref_audio_path": %s,
                  "prompt_text": %s,
                  "prompt_lang": %s,
                  "media_type": "wav"
                }
                """.formatted(
                        mapper.writeValueAsString(text),
                        mapper.writeValueAsString(REF_AUDIO_PATH),
                        mapper.writeValueAsString(PROMPT_TEXT),
                        mapper.writeValueAsString(PROMPT_LANG)
                );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCAL_TTS_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("TTS Failed: " + new String(response.body()));
        }

        // 保存文件到 target/classes/static/audio/ 下，无需重启即可访问
        // 注意：GPT-SoVITS 返回的是 WAV (如果 media_type=wav)，这里改后缀
        String fileName = "tts-" + UUID.randomUUID() + ".wav";
        Path outputDir = Paths.get("target/classes/static/audio");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        Path outputFile = outputDir.resolve(fileName);
        Files.write(outputFile, response.body());

        return "http://localhost:8080/audio/" + fileName;
    }

    /**
     * 调用阿里云 TTS
     */
    private String ttsToAudioCloud(String text) throws Exception {
        checkApiKey();

        String body = """
                {
                  "model": "qwen3-tts-flash",
                  "input": {
                    "text": %s,
                    "voice": "Cherry",
                    "language_type": "Chinese"
                  }
                }
                """.formatted(mapper.writeValueAsString(text));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TTS_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        JsonNode audioUrlNode = root.path("output").path("audio").path("url");

        if (audioUrlNode.isMissingNode() || audioUrlNode.asText().isEmpty()) {
            throw new RuntimeException("TTS 返回中没有 audio.url 字段！");
        }

        return audioUrlNode.asText();
    }

    /**
     * 调用 ASR API (语音转文字)
     */
    public String audioToText(byte[] audioBytes) throws Exception {
        if (USE_LOCAL_ASR) {
            return funASRClient.audioToText(audioBytes);
        } else {
            return audioToTextCloud(audioBytes);
        }
    }

    /**
     * 调用阿里云 ASR (SenseVoice)
     */
    public String audioToTextCloud(byte[] audioBytes) throws Exception {
        checkApiKey();

        String audioBase64 = java.util.Base64.getEncoder().encodeToString(audioBytes);

        String body = """
                {
                  "model": "sensevoice-v1",
                  "input": {
                    "audio": "data:application/octet-stream;base64,%s"
                  },
                  "parameters": {
                    "language": "auto"
                  }
                }
                """.formatted(audioBase64);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ASR_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());

        if (root.has("code") && !root.path("code").asText().isEmpty()) {
             if (!root.path("output").has("text")) {
                  throw new RuntimeException("ASR 失败: " + root.path("message").asText());
             }
        }

        String text = root.path("output").path("text").asText();
        if (text == null) {
            text = "";
        }
        return text;
    }
}
