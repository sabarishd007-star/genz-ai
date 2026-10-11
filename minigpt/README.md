# MiniGPT (genzai) 🚀

A scratch-built, lightweight GPT-style Causal Language Model and Desktop Workstation built for local training and inference on NVIDIA RTX laptop GPUs (tested on RTX 3050 4GB VRAM).

---

## 📂 Project Directory Structure

```
minigpt/
├── configs/        # tiny.yaml, small.yaml
├── data/           # raw/, processed/ (.bin memory-mapped files)
├── models/         # checkpoints (best.pt, latest.pt, loss_curve.png)
├── scripts/        # check_env.py, prepare_data.py, train.py, evaluate.py, generate.py
├── src/minigpt/    # model.py, tokenizer.py, data.py, sampling.py
├── app/            # gui.py (Tkinter Desktop Workstation)
├── requirements.txt
└── README.md
```

---

## 🛠️ Setup & Installation (Windows / Linux)

1. **Create and Activate Virtual Environment:**
   ```bash
   python -m venv venv
   # Windows Command Prompt:
   venv\Scripts\activate.bat
   ```

2. **Install PyTorch with CUDA Support:**
   ```bash
   pip install torch --index-url https://download.pytorch.org/whl/cu121
   ```

3. **Install Dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Verify Environment & Hardware:**
   ```bash
   python scripts/check_env.py
   ```

---

## 🚀 Step-by-Step Usage Guide

### Step 1: Prepare Data & Train Tokenizer

Downloads the TinyStories dataset subset, trains a byte-level BPE tokenizer (16k vocab), and formats sequences into memory-mapped `uint16` binary files:

```bash
python scripts/prepare_data.py --config configs/tiny.yaml
```

### Step 2: Train the Model

Trains the 17M parameter model using micro-batching, gradient accumulation, and bfloat16 mixed precision. Supports `--resume` for interrupted runs:

```bash
python scripts/train.py --config configs/tiny.yaml
```

### Step 3: Run CLI Generation

```bash
python scripts/generate.py --checkpoint models/checkpoints/best.pt --prompt "Once upon a time" --temperature 0.8
```

### Step 4: Launch Desktop GUI Workstation

Opens the Tkinter dark-mode workstation with real-time token streaming and adjustable sampling parameters:

```bash
python app/gui.py
```

### Step 5: Run Evaluation & Generate Report

```bash
python scripts/evaluate.py --config configs/tiny.yaml --checkpoint models/checkpoints/best.pt
```

---

## 💡 How It Works (Viva Defense Notes)

1. **Pre-LayerNorm & Residual Highway:** Layer normalization is applied before attention/MLP blocks rather than after, establishing a clean residual gradient highway back to early layers to prevent vanishing gradients.
2. **Gradient Accumulation:** Bypasses VRAM limits on 4GB laptop GPUs by processing small micro-batches sequentially and accumulating gradients over multiple steps before executing an optimizer step.
3. **Weight Tying:** Shares the embedding weight matrix (`wte`) with the final output projection head (`lm_head`), saving over 6 million parameters (~35% of total model footprint).
4. **KV-Caching:** Stores past Keys and Values during inference, converting autoregressive attention complexity from $O(T^2)$ down to $O(1)$ per new token step.
