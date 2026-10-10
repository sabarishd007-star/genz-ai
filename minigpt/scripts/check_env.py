import sys
import psutil
from pathlib import Path
import torch

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

def run_environment_check():
    print("=" * 60)
    print("           MiniGPT (genzai) - Stage 0 Check            ")
    print("=" * 60)
    
    # 1. System Info
    python_ver = sys.version.split()[0]
    ram_gb = psutil.virtual_memory().total / (1024 ** 3)
    print(f"[✓] Python Version:      {python_ver}")
    print(f"[✓] System RAM:          {ram_gb:.2f} GB")
    print(f"[✓] PyTorch Version:     {torch.__version__}")
    
    # 2. CUDA & GPU VRAM Detection
    cuda_available = torch.cuda.is_available()
    print(f"[✓] CUDA Available:      {cuda_available}")
    
    recommended_preset = "tiny"
    
    if cuda_available:
        gpu_name = torch.cuda.get_device_name(0)
        gpu_props = torch.cuda.get_device_properties(0)
        total_vram_gb = gpu_props.total_memory / (1024 ** 3)
        free_vram_gb = torch.cuda.mem_get_info()[0] / (1024 ** 3)
        
        # Check BFloat16 Support (Ampere architecture RTX 3050+ supports bf16)
        bf16_supported = torch.cuda.is_bf16_supported()
        
        print(f"[✓] GPU Detected:        {gpu_name}")
        print(f"[✓] Total VRAM:          {total_vram_gb:.2f} GB")
        print(f"[✓] Free VRAM:           {free_vram_gb:.2f} GB")
        print(f"[✓] Native BF16 Support: {bf16_supported}")
        
        # Select preset according to VRAM limits
        if total_vram_gb >= 6.0:
            recommended_preset = "small"
        else:
            recommended_preset = "tiny"
            
        # 3. Functional GPU Verification Test (Matmul Test)
        try:
            x = torch.randn(2000, 2000, device="cuda", dtype=torch.float32)
            y = torch.matmul(x, x)
            torch.cuda.synchronize()
            print("[✓] GPU Tensor Matmul Test: PASSED")
        except Exception as e:
            print(f"[X] GPU Tensor Matmul Test FAILED: {e}")
    else:
        print("[!] WARNING: CUDA not detected! PyTorch is running on CPU.")
        recommended_preset = "tiny"
        
    print("-" * 60)
    print(f"RECOMMENDED MODEL PRESET FOR YOUR HARDWARE: [{recommended_preset.upper()}]")
    print("=" * 60)
    
    return recommended_preset

if __name__ == "__main__":
    run_environment_check()
