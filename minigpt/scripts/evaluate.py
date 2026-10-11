# scripts/evaluate.py
import argparse
import math
import os
import sys
from pathlib import Path
import torch
import yaml
from tqdm import tqdm

# Ensure UTF-8 output encoding for Windows terminals
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass
os.environ.setdefault("PYTHONIOENCODING", "utf-8")

# Ensure src/ is on python path
_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "src"))

from minigpt.model import MiniGPT, MiniGPTConfig
from minigpt.tokenizer import CustomBPETokenizer
from minigpt.sampling import generate
from minigpt.data import create_dataloader

FIXED_PROMPTS = [
    "Once upon a time",
    "The little dog barked at",
    "In a galaxy far away",
    "She opened the magic door",
    "Tom went to the park to",
    "The sun was shining bright",
    "A brave knight found a",
    "Deep in the dark forest",
    "The blue bird flew across",
    "Every morning, Lily woke up"
]

def _resolve_path(p: str | Path) -> Path:
    p = Path(p)
    if p.exists():
        return p
    repo_p = _REPO_ROOT / p
    if repo_p.exists():
        return repo_p
    return p

def evaluate():
    parser = argparse.ArgumentParser(description="Evaluate MiniGPT model checkpoint")
    parser.add_argument("--config", type=str, default="configs/tiny.yaml")
    parser.add_argument("--checkpoint", type=str, default="models/checkpoints/best.pt")
    parser.add_argument("--output_report", type=str, default="models/evaluation_report.md")
    args = parser.parse_args()

    config_path = _resolve_path(args.config)
    with open(config_path, "r") as f:
        config = yaml.safe_load(f)

    model_cfg = config["model"]
    data_cfg = config.get("data", {})
    device = "cuda" if torch.cuda.is_available() else "cpu"

    checkpoint_path = _resolve_path(args.checkpoint)
    raw_tok_path = model_cfg.get("tokenizer_path") or data_cfg.get("tokenizer_path", "models/tokenizer.json")
    tokenizer_path = _resolve_path(raw_tok_path)

    print(f"--> Loading tokenizer and model from {checkpoint_path}...")
    tokenizer = CustomBPETokenizer(vocab_size=model_cfg["vocab_size"])
    tokenizer.load(tokenizer_path)

    checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)
    cfg = MiniGPTConfig(
        block_size=model_cfg["block_size"],
        vocab_size=model_cfg["vocab_size"],
        n_layer=model_cfg["n_layer"],
        n_head=model_cfg["n_head"],
        n_embd=model_cfg["n_embd"],
        dropout=model_cfg.get("dropout", 0.0),
        bias=model_cfg.get("bias", False)
    )
    model = MiniGPT(cfg).to(device)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    # 1. Compute Validation Loss & Perplexity
    processed_dir = _resolve_path(data_cfg.get("processed_dir", "data/processed"))
    val_bin = processed_dir / "val.bin"
    val_loader = create_dataloader(val_bin, model_cfg["block_size"], micro_batch_size=16, shuffle=False)
    
    total_loss = 0.0
    steps = 0
    with torch.no_grad():
        for x, y in tqdm(val_loader, desc="Evaluating Validation Set"):
            x, y = x.to(device), y.to(device)
            with torch.amp.autocast(device_type=device, dtype=torch.bfloat16):
                _, loss, _ = model(x, y)
            total_loss += loss.item()
            steps += 1
            if steps >= 200: # Limit evaluation batches for speed
                break

    mean_val_loss = total_loss / steps
    perplexity = math.exp(mean_val_loss)

    print(f"\n[✓] Validation Loss: {mean_val_loss:.4f} | Perplexity: {perplexity:.2f}")

    # 2. Generate Generations across Temperatures
    temperatures = [0.5, 0.8, 1.2]
    report_lines = [
        "# MiniGPT (genzai) — Evaluation Report",
        f"**Checkpoint:** `{args.checkpoint}`  ",
        f"**Validation Loss:** `{mean_val_loss:.4f}`  ",
        f"**Perplexity:** `{perplexity:.2f}`  ",
        "\n---",
        "\n## Qualitative Sample Generations\n"
    ]

    for prompt in FIXED_PROMPTS:
        report_lines.append(f"### Prompt: *\"{prompt}\"*")
        for temp in temperatures:
            chunks = list(generate(model, tokenizer, prompt, max_new_tokens=60, temperature=temp, device=device))
            generated_text = prompt + "".join(chunks)
            report_lines.append(f"- **Temp {temp}:** {generated_text}")
        report_lines.append("")

    report_path = Path(args.output_report)
    if not report_path.is_absolute() and not report_path.parent.exists():
        report_path = _REPO_ROOT / report_path
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(report_lines), encoding="utf-8")
    print(f"[✓] Comprehensive evaluation report saved to {report_path}")

if __name__ == "__main__":
    evaluate()
