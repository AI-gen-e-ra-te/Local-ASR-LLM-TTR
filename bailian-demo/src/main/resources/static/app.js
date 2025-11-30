const API_BASE = "http://localhost:8080";

const textInput = document.getElementById("textInput");
const sendTextBtn = document.getElementById("sendTextBtn");
const recordBtn = document.getElementById("recordBtn");
const statusEl = document.getElementById("status");
const subtitleEl = document.getElementById("subtitle");
const audioPlayer = document.getElementById("audioPlayer");

// ------- 1. 文字聊天 -------
sendTextBtn.addEventListener("click", async () => {
    const text = textInput.value.trim();
    if (!text) {
        alert("先输入一点内容吧~");
        return;
    }

    sendTextBtn.disabled = true;
    sendTextBtn.textContent = "发送中...";
    statusEl.textContent = "正在思考...";

    try {
        const resp = await fetch(`${API_BASE}/api/chat/text`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ text })
        });

        await handleResponse(resp);

    } catch (e) {
        console.error(e);
        statusEl.textContent = "出错：" + e.message;
    } finally {
        sendTextBtn.disabled = false;
        sendTextBtn.textContent = "发送文字";
    }
});

// ------- 2. 语音聊天 (录音) -------
let mediaRecorder;
let audioChunks = [];

recordBtn.addEventListener("mousedown", async () => {
    statusEl.textContent = "正在录音...";
    recordBtn.textContent = "松开结束";
    recordBtn.style.background = "linear-gradient(135deg, #ef4444, #f97316)"; // 变红提示

    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        mediaRecorder = new MediaRecorder(stream);
        audioChunks = [];

        mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                audioChunks.push(event.data);
            }
        };

        mediaRecorder.onstop = async () => {
            const audioBlob = new Blob(audioChunks, { type: "audio/wav" });
            uploadAudio(audioBlob);
            
            // 停止所有轨道，释放麦克风
            stream.getTracks().forEach(track => track.stop());
        };

        mediaRecorder.start();
    } catch (err) {
        console.error("无法获取麦克风权限", err);
        statusEl.textContent = "麦克风权限被拒绝或不支持";
        resetRecordBtn();
    }
});

recordBtn.addEventListener("mouseup", () => {
    if (mediaRecorder && mediaRecorder.state !== "inactive") {
        mediaRecorder.stop();
        statusEl.textContent = "录音结束，正在发送...";
        resetRecordBtn();
    }
});

// 鼠标移出按钮也停止录音
recordBtn.addEventListener("mouseleave", () => {
    if (mediaRecorder && mediaRecorder.state !== "inactive") {
        mediaRecorder.stop();
        resetRecordBtn();
        statusEl.textContent = "录音取消";
    }
});

function resetRecordBtn() {
    recordBtn.textContent = "按住说话";
    recordBtn.style.background = ""; // 恢复默认
}

async function uploadAudio(blob) {
    statusEl.textContent = "正在识别并思考...";
    
    const formData = new FormData();
    formData.append("file", blob, "input.wav");

    try {
        const resp = await fetch(`${API_BASE}/api/chat/audio`, {
            method: "POST",
            body: formData
        });

        await handleResponse(resp);

    } catch (e) {
        console.error(e);
        statusEl.textContent = "出错：" + e.message;
    }
}

// ------- 通用响应处理 -------
async function handleResponse(resp) {
    if (!resp.ok) {
        const errText = await resp.text();
        throw new Error("后端错误：" + errText);
    }

    const data = await resp.json();

    // 如果有识别到的文本，显示出来
    if (data.recognizedText) {
        statusEl.textContent = `我听到："${data.recognizedText}"`;
    } else {
        statusEl.textContent = "回复已生成";
    }

    subtitleEl.textContent = data.replyText || "(无文本)";

    if (data.audioUrl) {
        audioPlayer.src = data.audioUrl;
        audioPlayer.play().catch(e => console.log("播放失败", e));
    }
}

