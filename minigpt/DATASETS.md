# MiniGPT Dataset Registry & Attribution Guide 📚

This guide details the curated datasets supported by the **MiniGPT** pipeline in GenZ AI, along with licensing, hardware budget recommendations, and academic citations for viva defenses.

---

## 🎯 Architecture Context: One Domain per Model

Large Language Models like LLaMA-3 (8B) or Mistral (7B) require trillions of tokens and supercomputing clusters to achieve general multi-domain capabilities. On a consumer laptop GPU (such as an NVIDIA RTX 3050 with 4GB VRAM), the optimal machine learning engineering strategy is **Domain Specialization**:

$$\text{Domain Specialization} \gg \text{"All-of-the-above" Generalization}$$

Training a compact 17M parameter model on a single focused domain yields coherent and capable output, whereas attempting to train on all domains simultaneously with limited parameters leads to underfitting and degraded generation quality.

---

## 📊 Dataset Catalog & Licensing Table

| Domain | Dataset Name & Hugging Face Hub | License | Subset / Config | Streaming Mode | Cap Budget | Paper / Primary Source |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Readable Stories** | [`roneneldan/TinyStories`](https://huggingface.co/datasets/roneneldan/TinyStories) | CDLA-Sharing-1.0 | Default (Parquet) | Optional | 100k docs (~150 MB) | *TinyStories: How Small Can Language Models Be and Still Speak Coherent English?* ([arXiv:2305.07759](https://arxiv.org/abs/2305.07759)) |
| **Python Code** | [`codeparrot/github-code-clean`](https://huggingface.co/datasets/codeparrot/github-code-clean) | MIT (Per-file) | `Python-mit` | **Required** (`streaming=True`) | 50k files (~250 MB) | *CodeParrot: Cleaned GitHub Python Repository* |
| **Simple Facts** | [`wikimedia/wikipedia`](https://huggingface.co/datasets/wikimedia/wikipedia) | CC BY-SA 3.0 / GFDL | `20231101.simple` | Recommended | 80k articles (~150 MB) | *Wikimedia Wikipedia Dumps (Nov 2023 snapshot)* |
| **Educational Web** | [`HuggingFaceFW/fineweb-edu`](https://huggingface.co/datasets/HuggingFaceFW/fineweb-edu) | ODC-By | `sample-10BT` | **Required** (`streaming=True`) | 50k docs (~200 MB) | *The FineWeb Datasets: Decanting the Web for the Finest Text Data* ([arXiv:2406.17557](https://arxiv.org/abs/2406.17557)) |
| **News Articles** | [`vblagoje/cc_news`](https://huggingface.co/datasets/vblagoje/cc_news) | Academic Research | Default | Recommended | 60k articles (~200 MB) | *Common Crawl News Dataset* |
| **Gated Code (Alternative)** | [`bigcode/the-stack-smol`](https://huggingface.co/datasets/bigcode/the-stack-smol) | Per-file Open Source | e.g. `python` | Optional | Gated (Requires HF Login) | *The Stack: 3 TB of Permissively Licensed Source Code* ([arXiv:2211.15533](https://arxiv.org/abs/2211.15533)) |

---

## 🧠 Why RAG Beats Pre-training for Facts (Viva Q&A)

### The Question:
> *"Why not train MiniGPT on Wikipedia to answer general factual questions?"*

### The Scientific Answer:
1. **Parametric Memory Limits:** A 17M parameter model has roughly 34 MB of raw weight representation. Deep knowledge storage requires billions of parameters.
2. **Chinchilla Compute-Optimal Scaling:** According to Hoffmann et al. (*Chinchilla: Training Compute-Optimal Large Language Models*, [arXiv:2203.15556](https://arxiv.org/abs/2203.15556)), model size and training data should scale in equal proportion:
   $$N_{\text{tokens}} \approx 20 \times N_{\text{params}}$$
   For 17M parameters, optimal training is $\approx 340\text{M}$ tokens. Storing encyclopedic world knowledge in 17M parameters leads to severe hallucinations and degraded perplexity.
3. **Retrieval-Augmented Generation (RAG):** For factual accuracy, the **GenZ AI platform RAG architecture** (Spring Boot + FastAPI + pgvector / vector embeddings) decouples knowledge retrieval from text generation. MiniGPT serves as an autoregressive generator, while RAG provides real-time, non-hallucinated grounded context.

---

## 🧹 Preprocessing & Text Cleaning

### Encoding Repair (`ftfy`)
Raw web scrapes (including subsets of TinyStories and news data) frequently contain broken UTF-8 mojibake (e.g., `â€™` instead of `'`, `â€œ` instead of `“`).
MiniGPT integrates `ftfy.fix_text()` in `prepare_data.py`:
```python
import ftfy

cleaned_text = ftfy.fix_text(raw_text)
```
This guarantees clean byte sequences for the Custom BPE Tokenizer and avoids inflating the vocabulary with garbage subwords.

### Streaming with Byte Quotas
Datasets like `codeparrot/github-code-clean` total over **314 GB** (11 million files). The ingestion pipeline uses Hugging Face streaming iterators (`streaming=True`) combined with document and byte caps (`--max_docs`, `--max_mb`) so data is processed on-the-fly without exhausting laptop disk space.

---

## 🚀 Running the Ingestion Pipeline

### 1. Interactive Menu (Recommended)
Prompt the dataset selection menu directly in the console:
```bash
python scripts/prepare_data.py --interactive
```

### 2. Direct Domain Commands

**Readable Stories (Default):**
```bash
python scripts/prepare_data.py --dataset stories --config configs/tiny.yaml
```

**Python Code (MIT):**
```bash
python scripts/prepare_data.py --dataset python --config configs/python_code.yaml --domain_subdir
```

**Simple Wikipedia:**
```bash
python scripts/prepare_data.py --dataset wikipedia --config configs/wikipedia.yaml --domain_subdir
```

**Educational Web (FineWeb-Edu):**
```bash
python scripts/prepare_data.py --dataset fineweb --max_docs 50000 --max_mb 200
```
