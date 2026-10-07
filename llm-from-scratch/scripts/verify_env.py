# scripts/verify_env.py

import os
import sys

# Ensure UTF-8 output on Windows consoles
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

import torch
import numpy as np
import matplotlib
import transformers
import datasets
import tokenizers
import tqdm

def check_environment():
    print("==================================================")
    print("          ENVIRONMENT VERIFICATION CHECK          ")
    print("==================================================")

    # 1. Package Versions Check
    print(f"[OK] PyTorch Version:     {torch.__version__}")
    print(f"[OK] Transformers Version:{transformers.__version__}")
    print(f"[OK] Datasets Version:    {datasets.__version__}")
    print(f"[OK] Tokenizers Version:  {tokenizers.__version__}")
    print(f"[OK] NumPy Version:       {np.__version__}")
    print(f"[OK] Matplotlib Version:  {matplotlib.__version__}")
    print(f"[OK] Tqdm Version:        {tqdm.__version__}")
    print("--------------------------------------------------")

    # 2. CUDA & GPU Verification
    cuda_available = torch.cuda.is_available()
    print(f"CUDA Available: {cuda_available}")

    if cuda_available:
        device_count = torch.cuda.device_count()
        device_name = torch.cuda.get_device_name(0)
        cuda_version = torch.version.cuda
        
        print(f"[OK] CUDA Version:        {cuda_version}")
        print(f"[OK] GPU Device Count:    {device_count}")
        print(f"[OK] Primary GPU Model:   {device_name}")
        
        # Test Tensor Allocation on GPU
        x = torch.randn(1000, 1000, device="cuda")
        y = torch.matmul(x, x)
        print("[OK] GPU Tensor Operation Test: PASSED")
    else:
        print("[X] WARNING: CUDA is NOT detected by PyTorch!")
        print("    Ensure NVIDIA drivers are installed and correct PyTorch CUDA wheel was used.")
        
    print("==================================================")

if __name__ == "__main__":
    check_environment()
