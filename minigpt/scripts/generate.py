# scripts/generate.py
import argparse
import sys
from pathlib import Path
import torch
import yaml

# Ensure src/ is on python path
_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "src"))

from minigpt.model import MiniGPT, MiniGPTConfig
from minigpt.tokenizer import CustomBPETokenizer
from minigpt.sampling import generate

def main():
    parser = argparse.ArgumentParser(description="Generate text using trained MiniGPT model")
    parser.add_argument("--checkpoint", type=str, default="models/checkpoints/best.pt")
    parser.add_argument("--prompt", type=str, default="Once upon a time")
    parser.add_argument("--max_tokens", type=int, default=150)
    parser.add_argument("--temperature", type=float, default=0.8)
    parser.add_argument("--top_k", type=int, default=50)
    parser.add_argument("--top_p", type=float, default=0.9)
    parser.add_argument("--rep_penalty", type=float, default=1.1)
    args = parser.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"--> Loading checkpoint from {args.checkpoint}...")
    
    ckpt_path = Path(args.checkpoint)
    if not ckpt_path.is_absolute():
        ckpt_path = _REPO_ROOT / ckpt_path

    if not ckpt_path.exists():
        sys.exit(f"[!] Checkpoint not found at {ckpt_path}")

    checkpoint = torch.load(ckpt_path, map_location=device, weights_only=False)
    config_dict = checkpoint["config"]
    model_cfg = config_dict["model"]
    data_cfg = config_dict.get("data", {})

    tokenizer_path = model_cfg.get("tokenizer_path") or data_cfg.get("tokenizer_path", "models/tokenizer.json")
    tokenizer_file = Path(tokenizer_path)
    if not tokenizer_file.is_absolute():
        tokenizer_file = _REPO_ROOT / tokenizer_file

    # Initialize Tokenizer
    tokenizer = CustomBPETokenizer(vocab_size=model_cfg["vocab_size"])
    tokenizer.load(str(tokenizer_file))

    # Initialize Model
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

    print(f"\nPrompt: '{args.prompt}'\n" + "="*50)
    print(args.prompt, end="", flush=True)

    for chunk in generate(
        model,
        tokenizer,
        args.prompt,
        max_new_tokens=args.max_tokens,
        temperature=args.temperature,
        top_k=args.top_k,
        top_p=args.top_p,
        repetition_penalty=args.rep_penalty,
        device=device
    ):
        print(chunk, end="", flush=True)
    print("\n" + "="*50)

if __name__ == "__main__":
    main()
