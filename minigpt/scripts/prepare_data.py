# scripts/prepare_data.py
"""Data Preparation & Multi-Domain Ingestion Pipeline for MiniGPT.

Supports multiple curated domains:
  1. Stories: roneneldan/TinyStories (Default - best for 17M laptop training)
  2. Python Code: codeparrot/github-code-clean (Python-mit subset, streamed)
  3. General Facts: wikimedia/wikipedia (20231101.simple snapshot)
  4. News Articles: vblagoje/cc_news (streamed)
  5. Educational: HuggingFaceFW/fineweb-edu (sample-10BT, streamed)

Features:
  - Text cleaning with ftfy (fixes broken UTF-8 encoding, mojibake, curly quotes)
  - Memory-safe streaming with document and byte capping
  - Trains or loads CustomBPETokenizer (16k vocab)
  - Exports memory-mapped uint16 binary token arrays (train.bin, val.bin)
  - Generates dataset_info.json with attribution and statistics
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path
import yaml
import numpy as np
from datasets import load_dataset
from tqdm import tqdm

try:
    import ftfy
except ImportError:
    ftfy = None

# Add src to python path for modular import
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

from minigpt.tokenizer import CustomBPETokenizer

# Predefined dataset registry with attribution and defaults
DATASET_REGISTRY = {
    "stories": {
        "title": "TinyStories (Children's Stories & Coherent English)",
        "hf_id": "roneneldan/TinyStories",
        "subset": None,
        "text_field": "text",
        "stream": False,
        "split": "train",
        "license": "CDLA-Sharing-1.0",
        "url": "https://huggingface.co/datasets/roneneldan/TinyStories",
        "paper": "TinyStories: How Small Can Language Models Be and Still Speak Coherent English? (arXiv:2305.07759)",
        "default_max_docs": 100000,
        "default_max_mb": 150,
    },
    "python": {
        "title": "GitHub Clean Python Code (MIT licensed)",
        "hf_id": "codeparrot/github-code-clean",
        "subset": "Python-mit",
        "text_field": "code",
        "stream": True,
        "split": "train",
        "license": "MIT (per-file)",
        "url": "https://huggingface.co/datasets/codeparrot/github-code-clean",
        "paper": "CodeParrot: GitHub Code Cleaned Dataset",
        "default_max_docs": 50000,
        "default_max_mb": 250,
    },
    "wikipedia": {
        "title": "Simple English Wikipedia (General Knowledge)",
        "hf_id": "wikimedia/wikipedia",
        "subset": "20231101.simple",
        "text_field": "text",
        "stream": True,
        "split": "train",
        "license": "CC BY-SA 3.0 / GFDL",
        "url": "https://huggingface.co/datasets/wikimedia/wikipedia",
        "paper": "Wikimedia Wikipedia Dumps",
        "default_max_docs": 80000,
        "default_max_mb": 150,
    },
    "news": {
        "title": "CC-News (Common Crawl News Articles)",
        "hf_id": "vblagoje/cc_news",
        "subset": None,
        "text_field": "text",
        "stream": True,
        "split": "train",
        "license": "Unknown / Academic Research Only",
        "url": "https://huggingface.co/datasets/vblagoje/cc_news",
        "paper": "CC-News Dataset",
        "default_max_docs": 60000,
        "default_max_mb": 200,
    },
    "fineweb": {
        "title": "FineWeb-Edu (Educational Web Text)",
        "hf_id": "HuggingFaceFW/fineweb-edu",
        "subset": "sample-10BT",
        "text_field": "text",
        "stream": True,
        "split": "train",
        "license": "ODC-By",
        "url": "https://huggingface.co/datasets/HuggingFaceFW/fineweb-edu",
        "paper": "The FineWeb Datasets: Decanting the Web for the Finest Text Data (arXiv:2406.17557)",
        "default_max_docs": 50000,
        "default_max_mb": 200,
    },
}


def clean_text(raw_text: str) -> str:
    """Clean text by fixing mojibake and unicode anomalies using ftfy."""
    if not raw_text or not isinstance(raw_text, str):
        return ""
    if ftfy is not None:
        try:
            return ftfy.fix_text(raw_text)
        except Exception:
            return raw_text
    return raw_text


def prompt_dataset_menu() -> str:
    """Displays an interactive console menu to choose a dataset domain."""
    print("=" * 65)
    print("         MINIGPT MULTI-DOMAIN DATASET SELECTION MENU")
    print("=" * 65)
    print("Select a target domain to prepare for your MiniGPT model:\n")
    keys = list(DATASET_REGISTRY.keys())
    for idx, key in enumerate(keys, 1):
        info = DATASET_REGISTRY[key]
        rec = " [RECOMMENDED FOR FIRST MODEL]" if key == "stories" else ""
        print(f"  [{idx}] {key.upper():<10} - {info['title']}{rec}")
        print(f"       License: {info['license']} | Streamed: {info['stream']}")
        print()
    print("=" * 65)
    
    choice = input("Enter option number (1-5) [Default 1 - Stories]: ").strip()
    if not choice:
        return "stories"
    try:
        choice_idx = int(choice) - 1
        if 0 <= choice_idx < len(keys):
            return keys[choice_idx]
    except ValueError:
        pass
    print("Invalid choice, defaulting to 'stories'.")
    return "stories"


def fetch_documents(dataset_key: str, max_docs: int, max_mb: float) -> list:
    """Streams or downloads documents up to max_docs or max_mb."""
    meta = DATASET_REGISTRY[dataset_key]
    hf_id = meta["hf_id"]
    subset = meta["subset"]
    stream = meta["stream"]
    field = meta["text_field"]
    max_bytes = int(max_mb * 1024 * 1024) if max_mb else None

    print(f"\n--> Ingesting domain: [{dataset_key.upper()}] ({meta['title']})")
    print(f"    Source: {hf_id} (Subset: {subset or 'default'})")
    print(f"    Streaming: {stream} | Document cap: {max_docs:,} | Byte cap: {max_mb} MB")

    docs = []
    total_bytes = 0

    if stream:
        print(f"--> Initializing streaming iterator from Hugging Face Hub...", flush=True)
        try:
            if subset:
                ds = load_dataset(hf_id, subset, split="train", streaming=True)
            else:
                ds = load_dataset(hf_id, split="train", streaming=True)
        except Exception as e:
            print(f"[!] Streaming failed ({e}). Attempting non-streaming fallback...", flush=True)
            if subset:
                ds = load_dataset(hf_id, subset, split="train")
            else:
                ds = load_dataset(hf_id, split="train")

        pbar = tqdm(total=max_docs, desc=f"Streaming {dataset_key}")
        for item in ds:
            text = item.get(field, "")
            if not text or len(text.strip()) < 20:
                continue

            cleaned = clean_text(text)
            text_size = len(cleaned.encode("utf-8", errors="ignore"))
            docs.append(cleaned)
            total_bytes += text_size
            pbar.update(1)

            if len(docs) >= max_docs:
                break
            if max_bytes and total_bytes >= max_bytes:
                print(f"\n[i] Reached raw text size cap of {max_mb} MB.")
                break
        pbar.close()

    else:
        # Standard download (e.g. TinyStories parquet slice)
        print(f"--> Loading dataset from Hugging Face...", flush=True)
        try:
            dataset = load_dataset(hf_id, data_files="data/train-00000-of-00004-*.parquet", split="train")
        except Exception:
            dataset = load_dataset(hf_id, split="train")

        num_to_take = min(len(dataset), max_docs)
        print(f"--> Extracting and cleaning {num_to_take:,} documents...", flush=True)
        raw_docs = dataset.select(range(num_to_take))[field]
        for text in tqdm(raw_docs, desc=f"Cleaning {dataset_key}"):
            cleaned = clean_text(text)
            if cleaned and len(cleaned.strip()) >= 20:
                docs.append(cleaned)
                total_bytes += len(cleaned.encode("utf-8", errors="ignore"))
                if max_bytes and total_bytes >= max_bytes:
                    print(f"\n[i] Reached raw text size cap of {max_mb} MB.")
                    break

    print(f"[✓] Collected {len(docs):,} cleaned documents ({total_bytes / (1024**2):.2f} MB raw text)")
    return docs


def prepare_data(
    config_path: str = "configs/tiny.yaml",
    dataset_key: str = "stories",
    max_docs: int = None,
    max_mb: float = None,
    interactive: bool = False,
    domain_subdir: bool = False,
):
    # 1. Resolve domain choice
    if interactive:
        dataset_key = prompt_dataset_menu()

    if dataset_key not in DATASET_REGISTRY:
        print(f"[!] Warning: Unknown dataset key '{dataset_key}'. Using 'stories'.")
        dataset_key = "stories"

    dataset_meta = DATASET_REGISTRY[dataset_key]

    if max_docs is None:
        max_docs = dataset_meta["default_max_docs"]
    if max_mb is None:
        max_mb = dataset_meta["default_max_mb"]

    # 2. Load Configuration
    project_root = Path(__file__).resolve().parent.parent
    config_file = project_root / config_path
    if not config_file.exists():
        # Fallback to default
        config_file = project_root / "configs" / "tiny.yaml"

    with open(config_file, "r", encoding="utf-8") as f:
        config = yaml.safe_load(f)

    train_ratio = config["data"].get("train_split_ratio", 0.8)
    vocab_size = config["model"].get("vocab_size", 16000)

    # 3. Setup paths (prevent redundant nested domain directory)
    base_processed = Path(config["data"]["processed_dir"])
    base_tokenizer = Path(config["data"]["tokenizer_path"])

    if domain_subdir and base_processed.name != dataset_key:
        processed_dir = project_root / base_processed / dataset_key
        tokenizer_path = project_root / base_tokenizer.parent / f"{dataset_key}_tokenizer.json"
    else:
        processed_dir = project_root / base_processed
        tokenizer_path = project_root / base_tokenizer

    processed_dir.mkdir(parents=True, exist_ok=True)
    tokenizer_path.parent.mkdir(parents=True, exist_ok=True)

    # 4. Fetch and clean documents
    docs = fetch_documents(dataset_key, max_docs=max_docs, max_mb=max_mb)
    if not docs:
        sys.exit("[ERROR] No documents were fetched. Check internet connection or dataset name.")

    total_docs = len(docs)
    train_doc_count = int(total_docs * train_ratio)
    train_docs = docs[:train_doc_count]
    val_docs = docs[train_doc_count:]

    print(f"[✓] Split: {len(train_docs):,} train documents | {len(val_docs):,} validation documents")

    # 5. Train or Load Tokenizer
    tokenizer = CustomBPETokenizer(vocab_size=vocab_size)
    if not tokenizer_path.exists():
        print(f"--> Training Custom BPE Tokenizer ({vocab_size:,} vocab) on {dataset_key} sample...", flush=True)
        sample_size = min(len(train_docs), 25000)
        tokenizer.train_from_iterator(train_docs[:sample_size], save_path=tokenizer_path)
    else:
        print(f"--> Reusing existing tokenizer at {tokenizer_path}", flush=True)
        tokenizer.load(tokenizer_path)

    eot_id = tokenizer.eot_token_id

    # 6. Encode and Write Binary Files
    def encode_and_write(documents, output_bin_path):
        print(f"--> Tokenizing and serializing to {output_bin_path}...", flush=True)
        all_token_ids = []
        for doc in tqdm(documents, desc=f"Encoding -> {output_bin_path.name}"):
            ids = tokenizer.encode(doc)
            ids.append(eot_id)
            all_token_ids.extend(ids)

        arr = np.array(all_token_ids, dtype=np.uint16)
        with open(output_bin_path, "wb") as f:
            f.write(arr.tobytes())
        return len(arr)

    train_bin_path = processed_dir / "train.bin"
    val_bin_path = processed_dir / "val.bin"

    train_tokens = encode_and_write(train_docs, train_bin_path)
    val_tokens = encode_and_write(val_docs, val_bin_path)

    # 7. Write Dataset Metadata & Attribution
    meta_record = {
        "domain": dataset_key,
        "title": dataset_meta["title"],
        "hf_source": dataset_meta["hf_id"],
        "subset": dataset_meta["subset"],
        "license": dataset_meta["license"],
        "url": dataset_meta["url"],
        "paper_citation": dataset_meta["paper"],
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "statistics": {
            "total_documents": total_docs,
            "train_documents": len(train_docs),
            "val_documents": len(val_docs),
            "train_tokens": train_tokens,
            "val_tokens": val_tokens,
            "train_size_mb": round(train_bin_path.stat().st_size / (1024**2), 2),
            "val_size_mb": round(val_bin_path.stat().st_size / (1024**2), 2),
            "vocab_size": vocab_size,
        },
    }

    info_path = processed_dir / "dataset_info.json"
    with open(info_path, "w", encoding="utf-8") as f:
        json.dump(meta_record, f, indent=2)

    print("=" * 65, flush=True)
    print(f"[✓] Data Pipeline Complete for [{dataset_key.upper()}]!", flush=True)
    print(f"    Train Tokens: {train_tokens:,} ({train_bin_path.stat().st_size / (1024**2):.2f} MB)", flush=True)
    print(f"    Val Tokens:   {val_tokens:,} ({val_bin_path.stat().st_size / (1024**2):.2f} MB)", flush=True)
    print(f"    Metadata Log: {info_path}", flush=True)
    print(f"    License:      {dataset_meta['license']}", flush=True)
    print("=" * 65, flush=True)

    # 8. Tokenizer Round-Trip Sanity Verification
    sample_text = train_docs[0][:160] if train_docs else "Once upon a time in a tiny world."
    sample_ids = tokenizer.encode(sample_text)
    decoded_text = tokenizer.decode(sample_ids)

    print("\n--- TOKENIZER SANITY CHECK (Round-Trip) ---", flush=True)
    print(f"Original Text:  {sample_text!r}", flush=True)
    print(f"Token IDs ({len(sample_ids)}): {sample_ids[:15]}...", flush=True)
    print(f"Decoded Text:   {decoded_text!r}", flush=True)
    print("--------------------------------------------\n", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MiniGPT Multi-Domain Data Ingestion Pipeline")
    parser.add_argument("--config", type=str, default="configs/tiny.yaml", help="Path to YAML model configuration")
    parser.add_argument(
        "--dataset",
        type=str,
        default="stories",
        choices=["stories", "python", "wikipedia", "news", "fineweb"],
        help="Target domain dataset to ingest",
    )
    parser.add_argument("--interactive", action="store_true", help="Launch interactive dataset selection menu")
    parser.add_argument("--max_docs", type=int, default=None, help="Maximum number of documents to collect")
    parser.add_argument("--max_mb", type=float, default=None, help="Maximum raw text budget in megabytes")
    parser.add_argument(
        "--domain_subdir",
        action="store_true",
        help="Store binary outputs in data/processed/<domain> instead of base processed dir",
    )
    args = parser.parse_args()

    prepare_data(
        config_path=args.config,
        dataset_key=args.dataset,
        max_docs=args.max_docs,
        max_mb=args.max_mb,
        interactive=args.interactive,
        domain_subdir=args.domain_subdir,
    )
