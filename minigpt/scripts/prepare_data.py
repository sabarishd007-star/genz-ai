# scripts/prepare_data.py
import argparse
import os
import sys
from pathlib import Path
import yaml
import numpy as np
from datasets import load_dataset
from tqdm import tqdm

# Add src to python path for modular import
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

from minigpt.tokenizer import CustomBPETokenizer

def prepare_data(config_path: str):
    # 1. Load Configuration
    with open(config_path, "r", encoding="utf-8") as f:
        config = yaml.safe_load(f)
        
    dataset_name = config["data"].get("dataset_name", "roneneldan/TinyStories")
    train_ratio = config["data"]["train_split_ratio"]
    vocab_size = config["model"]["vocab_size"]
    
    # Resolve paths relative to minigpt project root
    project_root = Path(__file__).resolve().parent.parent
    processed_dir = project_root / config["data"]["processed_dir"]
    tokenizer_path = project_root / config["data"]["tokenizer_path"]
    
    processed_dir.mkdir(parents=True, exist_ok=True)
    tokenizer_path.parent.mkdir(parents=True, exist_ok=True)
    
    print(f"--> Streaming dataset: {dataset_name}...", flush=True)
    try:
        dataset = load_dataset(dataset_name, data_files="data/train-00000-of-00004-*.parquet", split="train")
    except Exception:
        dataset = load_dataset(dataset_name, split="train")
    
    # Limit to first 100,000 stories for fast processing (~150MB raw text)
    max_docs = 100000
    docs = dataset.select(range(min(len(dataset), max_docs)))["text"]
    
    total_docs = len(docs)
    train_doc_count = int(total_docs * train_ratio)
    
    train_docs = docs[:train_doc_count]
    val_docs = docs[train_doc_count:]
    
    print(f"[✓] Split Dataset: {len(train_docs):,} train stories | {len(val_docs):,} val stories", flush=True)
    
    # 2. Train Tokenizer
    tokenizer = CustomBPETokenizer(vocab_size=vocab_size)
    if not tokenizer_path.exists():
        print("--> Training Custom BPE Tokenizer...", flush=True)
        tokenizer.train_from_iterator(train_docs[:20000], save_path=tokenizer_path)
    else:
        tokenizer.load(tokenizer_path)
        
    eot_id = tokenizer.eot_token_id
    
    # 3. Process to Binary np.uint16 Files
    def encode_and_write(documents, output_bin_path):
        print(f"--> Tokenizing & writing to {output_bin_path}...", flush=True)
        
        # Estimate total tokens to allocate array
        all_token_ids = []
        for doc in tqdm(documents, desc="Encoding Stories"):
            ids = tokenizer.encode(doc)
            ids.append(eot_id) # Append <|endoftext|> between documents
            all_token_ids.extend(ids)
            
        arr = np.array(all_token_ids, dtype=np.uint16)
        
        # Save array to disk
        with open(output_bin_path, "wb") as f:
            f.write(arr.tobytes())
            
        return len(arr)

    train_bin_path = processed_dir / "train.bin"
    val_bin_path = processed_dir / "val.bin"
    
    train_tokens = encode_and_write(train_docs, train_bin_path)
    val_tokens = encode_and_write(val_docs, val_bin_path)
    
    print("=" * 60, flush=True)
    print(f"[✓] Data Processing Complete!", flush=True)
    print(f"    Train Tokens: {train_tokens:,} ({train_bin_path.stat().st_size / (1024**2):.2f} MB)", flush=True)
    print(f"    Val Tokens:   {val_tokens:,} ({val_bin_path.stat().st_size / (1024**2):.2f} MB)", flush=True)
    print("=" * 60, flush=True)
    
    # 4. Round-Trip Sanity Verification
    sample_text = train_docs[0][:150]
    sample_ids = tokenizer.encode(sample_text)
    decoded_text = tokenizer.decode(sample_ids)
    
    print("\n--- TOKENIZER SANITY CHECK (Round-Trip) ---", flush=True)
    print(f"Original Text:  {sample_text!r}", flush=True)
    print(f"Token IDs ({len(sample_ids)}): {sample_ids[:15]}...", flush=True)
    print(f"Decoded Text:   {decoded_text!r}", flush=True)
    print("--------------------------------------------\n", flush=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="configs/tiny.yaml")
    args = parser.parse_args()
    
    prepare_data(args.config)
