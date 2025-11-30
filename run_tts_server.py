import os
import sys
import subprocess
import time

# Path to the GPT-SoVITS directory
GPT_SOVITS_DIR = os.path.join(os.getcwd(), "GPT-SoVITS-20250606v2pro")
API_SCRIPT = "api_v2.py"

def main():
    if not os.path.exists(GPT_SOVITS_DIR):
        print(f"Error: GPT-SoVITS directory not found at {GPT_SOVITS_DIR}")
        return

    print(f"Starting GPT-SoVITS Server from {GPT_SOVITS_DIR}...")
    
    # We need to run the script inside its directory so it can find its modules
    cmd = [sys.executable, API_SCRIPT, "-a", "127.0.0.1", "-p", "9880"]
    
    try:
        # Popen allows us to run it without blocking this script entirely (though we wait here)
        # or we can just replace the process
        subprocess.run(cmd, cwd=GPT_SOVITS_DIR, check=True)
    except KeyboardInterrupt:
        print("\nStopping GPT-SoVITS Server...")
    except Exception as e:
        print(f"Error running GPT-SoVITS: {e}")

if __name__ == "__main__":
    main()
