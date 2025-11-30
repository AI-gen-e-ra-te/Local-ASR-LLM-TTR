import sys
import traceback

print("System: Starting script...", flush=True)

try:
    print("System: Importing libraries...", flush=True)
    import asyncio
    import websockets
    import json
    from funasr import AutoModel
    print("System: Libraries imported successfully!", flush=True)

    # 1. 使用官方 ID 自动下载 (Python 环境下这样最稳)
    print("Loading models...", flush=True)
    
    # 指定本地缓存目录 (可选，如果不指定默认在 C:\Users\Paradox\.cache\modelscope)
    # 这里我们不指定本地路径，让它自己去下标准 PyTorch 版，兼容性最好
    
    model = AutoModel(
        model="iic/speech_paraformer-large-vad-punc_asr_nat-zh-cn-16k-common-vocab8404-pytorch",
        vad_model="iic/speech_fsmn_vad_zh-cn-16k-common-pytorch",
        punc_model="iic/punc_ct-transformer_zh-cn-common-vocab272727-pytorch",
        # disable_update=True # 先把这个注释掉，允许它第一次联网下载
    )
    print("Models loaded successfully!", flush=True)

    # 2. 定义 WebSocket 服务
    async def asr_server(websocket):
        print("New client connected", flush=True)
        async for message in websocket:
            try:
                print(f"Received audio data: {len(message)} bytes", flush=True)
                # FunASR generate 支持直接输入 bytes
                res = model.generate(input=message, batch_size_s=300)
                
                # 解析结果
                text = ""
                if res and len(res) > 0:
                     text = res[0].get('text', '')
                
                print(f"Recognized: {text}", flush=True)
                
                # 返回 JSON 格式结果
                await websocket.send(json.dumps({"text": text}))
            except Exception as e:
                print(f"Processing Error: {e}", flush=True)
                traceback.print_exc()
                await websocket.send(json.dumps({"error": str(e)}))

    # 3. 启动服务
    async def main():
        print("Starting WebSocket server on port 10095...", flush=True)
        async with websockets.serve(asr_server, "0.0.0.0", 10095):
            await asyncio.Future()  # run forever

    if __name__ == "__main__":
        asyncio.run(main())

except Exception as e:
    print("\nCRITICAL ERROR:", flush=True)
    traceback.print_exc()
    input("Press Enter to exit...") # 让窗口暂停，防止闪退
