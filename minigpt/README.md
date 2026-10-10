# MiniGPT (genzai)

A lightweight GPT model built from scratch with PyTorch, tailored and optimized for local training and inference on NVIDIA RTX 3050 laptop hardware (4GB VRAM) and 16GB system RAM on Windows.

## Project Structure
- `configs/`: YAML configuration files for different model scales (`tiny.yaml`, `small.yaml`, `gpt2_small.yaml`).
- `data/`: Raw and tokenized/processed dataset artifacts.
- `models/`: Trained model weights and checkpoints.
- `scripts/`: Diagnostic, data preparation, training, evaluation, and generation scripts.
- `src/minigpt/`: Modular source package (Transformer architecture, tokenizer, dataset loaders, sampling, utilities).
- `app/`: GUI interface for real-time text generation and token probability visualization.
- `tests/`: Automated unit and integration tests.

## Hardware Diagnostics
Run the hardware diagnostic script to verify your CUDA environment and GPU specifications:
```bash
python scripts/check_env.py
```
