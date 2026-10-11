# MiniGPT (genzai) 🚀

A scratch-built, lightweight GPT-style Causal Language Model and Desktop Workstation built for local training and inference on NVIDIA RTX laptop GPUs (tested on RTX 3050 4GB VRAM).

---

## 📂 Project Directory Structure

```
minigpt/
├── configs/        # tiny.yaml, small.yaml, python_code.yaml, wikipedia.yaml
├── data/           # raw/, processed/ (.bin memory-mapped files)
├── models/         # checkpoints (best.pt, latest.pt, evaluation_report.md)
├── scripts/        # check_env.py, prepare_data.py, train.py, evaluate.py, generate.py
├── src/minigpt/    # model.py, tokenizer.py, data.py, sampling.py
├── app/            # gui.py (Tkinter Desktop Workstation)
├── DATASETS.md     # Comprehensive dataset directory, attribution & scaling theory
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

### Step 1: Prepare Data & Train Tokenizer (Multi-Domain Pipeline)

MiniGPT features an interactive dataset ingestion pipeline with `ftfy` text cleaning and streaming caps. Choose from Stories, Python Code, Simple Wikipedia, News, or FineWeb-Edu:

```bash
# Launch interactive domain selector menu:
python scripts/prepare_data.py --interactive

# Or directly ingest a specific domain:
python scripts/prepare_data.py --dataset stories --config configs/tiny.yaml
python scripts/prepare_data.py --dataset python --config configs/python_code.yaml --domain_subdir
```

For complete dataset licensing, papers, and parameters, see [DATASETS.md](DATASETS.md).

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
5. **Windows OS Note:** As highlighted in Karpathy's nanoGPT, `torch.compile` is bypassed on Windows environments for native stability.

---

## 📖 Academic References & Inspiration

- **nanoGPT & nanochat:** Andrej Karpathy ([nanoGPT GitHub](https://github.com/karpathy/nanoGPT), [nanochat](https://github.com/karpathy/nanochat))
- **Video Lecture:** Andrej Karpathy's *Neural Networks: Zero to Hero — "Let's build GPT: from scratch, in code"* ([YouTube](https://www.youtube.com/watch?v=kCc8FmEb1nY))
- **Attention Is All You Need:** Vaswani et al., 2017 ([arXiv:1706.03762](https://arxiv.org/abs/1706.03762))
- **TinyStories Paper:** Eldan & Li, 2023 ([arXiv:2305.07759](https://arxiv.org/abs/2305.07759))
- **FineWeb Paper:** Penedo et al., 2024 ([arXiv:2406.17557](https://arxiv.org/abs/2406.17557))
- **Chinchilla Scaling Laws:** Hoffmann et al., 2022 ([arXiv:2203.15556](https://arxiv.org/abs/2203.15556))

