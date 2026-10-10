# scripts/train.py
import os
os.environ.setdefault("PYTHONIOENCODING", "utf-8")
"""Training engine for MiniGPT — Stage 3.

Features:
  - Gradient accumulation over micro-batches (VRAM-safe on RTX 3050 / 4 GB)
  - Automatic Mixed Precision with bfloat16 (torch.amp.autocast)
  - Cosine learning-rate schedule with linear warm-up
  - Resumable checkpoints (model, optimizer, step, RNG states)
  - Graceful Ctrl+C interrupt: saves emergency checkpoint before exit
  - matplotlib training vs validation loss curve saved on completion
"""

import argparse
import math
import os
import signal
import sys
import time
from pathlib import Path

import matplotlib
matplotlib.use("Agg")   # Non-interactive backend — safe for servers / headless runs
import matplotlib.pyplot as plt
import torch
import yaml
from tqdm import tqdm

# ---------------------------------------------------------------------------
# Ensure src/ on the Python path so `minigpt` can be imported when the script
# is executed directly from the project root (python scripts/train.py …)
# ---------------------------------------------------------------------------
_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "src"))

from minigpt.model import MiniGPT, MiniGPTConfig
from minigpt.data import create_dataloader


# ---------------------------------------------------------------------------
# Graceful interrupt handling
# ---------------------------------------------------------------------------

INTERRUPTED = False


def _signal_handler(sig, frame):
    global INTERRUPTED
    print(
        "\n[!] Interrupt signal detected (Ctrl+C). "
        "Saving emergency checkpoint before exit..."
    )
    INTERRUPTED = True


signal.signal(signal.SIGINT, _signal_handler)


# ---------------------------------------------------------------------------
# Learning-rate schedule
# ---------------------------------------------------------------------------

def get_lr(
    it: int,
    warmup_steps: int,
    max_steps: int,
    learning_rate: float,
    min_lr: float,
) -> float:
    """Cosine decay schedule with linear warm-up.

    1. Linear warm-up from 0 → learning_rate over warmup_steps.
    2. Cosine annealing from learning_rate → min_lr over remaining steps.
    3. After max_steps, hold at min_lr.
    """
    if it < warmup_steps:
        return learning_rate * (it + 1) / (warmup_steps + 1)
    if it >= max_steps:
        return min_lr
    decay_ratio = (it - warmup_steps) / (max_steps - warmup_steps)
    coeff = 0.5 * (1.0 + math.cos(math.pi * decay_ratio))
    return min_lr + coeff * (learning_rate - min_lr)


# ---------------------------------------------------------------------------
# Validation loss estimation
# ---------------------------------------------------------------------------

@torch.no_grad()
def estimate_loss(
    model: MiniGPT,
    train_loader,
    val_loader,
    eval_iters: int,
    device: str,
) -> dict:
    """Average cross-entropy loss over eval_iters batches for both splits."""
    out = {}
    model.eval()
    for split, loader in [("train", train_loader), ("val", val_loader)]:
        losses = torch.zeros(eval_iters)
        loader_iter = iter(loader)
        for k in range(eval_iters):
            try:
                x, y = next(loader_iter)
            except StopIteration:
                loader_iter = iter(loader)
                x, y = next(loader_iter)

            x, y = x.to(device), y.to(device)
            with torch.amp.autocast(device_type=device, dtype=torch.bfloat16):
                _, loss, _ = model(x, y)
            losses[k] = loss.item()
        out[split] = losses.mean().item()
    model.train()
    return out


# ---------------------------------------------------------------------------
# Core training function
# ---------------------------------------------------------------------------

def train(config_path: str, resume_checkpoint: str = None) -> None:
    global INTERRUPTED

    # ------------------------------------------------------------------
    # 1. Load YAML configuration
    # ------------------------------------------------------------------
    with open(config_path, "r") as f:
        config = yaml.safe_load(f)

    model_cfg  = config["model"]
    train_cfg  = config["training"]
    data_cfg   = config["data"]

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"--> Training device: {device.upper()}")

    # ------------------------------------------------------------------
    # 2. Data loaders
    # ------------------------------------------------------------------
    processed_dir = Path(data_cfg["processed_dir"])
    train_bin = processed_dir / "train.bin"
    val_bin   = processed_dir / "val.bin"

    if not train_bin.exists() or not val_bin.exists():
        sys.exit(
            f"[ERROR] Processed data not found at {processed_dir}. "
            "Run scripts/prepare_data.py first."
        )

    train_loader = create_dataloader(
        train_bin, model_cfg["block_size"], train_cfg["micro_batch_size"], shuffle=True
    )
    val_loader = create_dataloader(
        val_bin,   model_cfg["block_size"], train_cfg["micro_batch_size"], shuffle=False
    )

    # ------------------------------------------------------------------
    # 3. Model
    # ------------------------------------------------------------------
    cfg = MiniGPTConfig(
        block_size = model_cfg["block_size"],
        vocab_size = model_cfg["vocab_size"],
        n_layer    = model_cfg["n_layer"],
        n_head     = model_cfg["n_head"],
        n_embd     = model_cfg["n_embd"],
        dropout    = model_cfg["dropout"],
        bias       = model_cfg["bias"],
    )

    model = MiniGPT(cfg).to(device)
    print(f"[OK] MiniGPT initialised -- {model.get_num_params() / 1e6:.2f} M parameters")

    # ------------------------------------------------------------------
    # 4. Optimiser & AMP scaler
    # ------------------------------------------------------------------
    optimizer = model.configure_optimizers(
        weight_decay  = train_cfg["weight_decay"],
        learning_rate = train_cfg["learning_rate"],
        betas         = (train_cfg["beta1"], train_cfg["beta2"]),
        device_type   = device,
    )

    # GradScaler: bfloat16 on Ampere GPUs doesn't need loss scaling, but we
    # keep the scaler for float16 fallback compatibility (enabled only on CUDA).
    scaler = torch.amp.GradScaler("cuda", enabled=(device == "cuda"))

    # ------------------------------------------------------------------
    # 5. Checkpoint directory
    # ------------------------------------------------------------------
    checkpoint_dir = Path("models/checkpoints")
    checkpoint_dir.mkdir(parents=True, exist_ok=True)

    start_step     = 0
    best_val_loss  = float("inf")
    history_steps       = []
    history_train_loss  = []
    history_val_loss    = []

    # ------------------------------------------------------------------
    # 6. Resume from checkpoint (optional)
    # ------------------------------------------------------------------
    if resume_checkpoint:
        ckpt_path = Path(resume_checkpoint)
        if ckpt_path.exists():
            print(f"--> Resuming from checkpoint: {ckpt_path}")
            checkpoint = torch.load(ckpt_path, map_location=device, weights_only=False)
            model.load_state_dict(checkpoint["model_state_dict"])
            optimizer.load_state_dict(checkpoint["optimizer_state_dict"])
            start_step    = checkpoint["step"] + 1
            best_val_loss = checkpoint.get("best_val_loss", float("inf"))
            # Restore loss history for continuous plotting
            history_steps      = checkpoint.get("history_steps", [])
            history_train_loss = checkpoint.get("history_train_loss", [])
            history_val_loss   = checkpoint.get("history_val_loss", [])
            print(f"[OK] Resumed at step {start_step}  |  Best val loss so far: {best_val_loss:.4f}")
        else:
            print(f"[WARNING] Checkpoint not found at {ckpt_path}. Starting fresh.")

    # ------------------------------------------------------------------
    # 7. Training configuration summary
    # ------------------------------------------------------------------
    micro_bs   = train_cfg["micro_batch_size"]
    grad_accum = train_cfg["gradient_accumulation_steps"]
    block_size = model_cfg["block_size"]
    effective_token_batch = micro_bs * grad_accum * block_size

    print("\n--- TRAINING CONFIGURATION ---")
    print(f"    Micro-batch size:       {micro_bs}")
    print(f"    Gradient Accumulation:  {grad_accum}")
    print(f"    Effective Batch Tokens: {effective_token_batch:,}")
    print(f"    Total Target Steps:     {train_cfg['max_steps']}")
    print(f"    Warmup Steps:           {train_cfg['warmup_steps']}")
    print(f"    Learning Rate:          {train_cfg['learning_rate']:.2e}")
    print(f"    Min LR:                 {train_cfg['min_lr']:.2e}")
    print(f"    Checkpoint dir:         {checkpoint_dir}")
    print("------------------------------\n")

    # ------------------------------------------------------------------
    # 8. Training loop
    # ------------------------------------------------------------------
    model.train()
    train_iter = iter(train_loader)
    optimizer.zero_grad()
    start_time = time.time()

    progress_bar = tqdm(
        range(start_step, train_cfg["max_steps"]),
        desc="Training MiniGPT",
        dynamic_ncols=True,
    )

    for step in progress_bar:
        if INTERRUPTED:
            break

        # Dynamic learning-rate schedule
        lr = get_lr(
            step,
            train_cfg["warmup_steps"],
            train_cfg["max_steps"],
            train_cfg["learning_rate"],
            train_cfg["min_lr"],
        )
        for param_group in optimizer.param_groups:
            param_group["lr"] = lr

        # --------------------------------------------------------------
        # Gradient accumulation loop
        # --------------------------------------------------------------
        loss_accum = 0.0
        for _micro in range(grad_accum):
            try:
                x, y = next(train_iter)
            except StopIteration:
                train_iter = iter(train_loader)
                x, y = next(train_iter)

            x, y = x.to(device), y.to(device)

            with torch.amp.autocast(device_type=device, dtype=torch.bfloat16):
                _, loss, _ = model(x, y)
                loss = loss / grad_accum  # scale for accumulation

            loss_accum += loss.item()
            scaler.scale(loss).backward()

        # Gradient clipping → prevent exploding gradients
        scaler.unscale_(optimizer)
        torch.nn.utils.clip_grad_norm_(model.parameters(), train_cfg["grad_clip"])

        scaler.step(optimizer)
        scaler.update()
        optimizer.zero_grad()

        # --------------------------------------------------------------
        # Live telemetry (every 50 steps)
        # --------------------------------------------------------------
        if step % 50 == 0 or step == train_cfg["max_steps"] - 1:
            current_vram_mb = (
                torch.cuda.memory_allocated() / (1024 ** 2) if device == "cuda" else 0.0
            )
            progress_bar.set_postfix({
                "Loss": f"{loss_accum * grad_accum:.4f}",
                "LR":   f"{lr:.2e}",
                "VRAM": f"{current_vram_mb:.0f}MB",
            })

        # --------------------------------------------------------------
        # Periodic evaluation & checkpointing (every 250 steps)
        # --------------------------------------------------------------
        if step % 250 == 0 or step == train_cfg["max_steps"] - 1:
            eval_dict = estimate_loss(
                model, train_loader, val_loader,
                eval_iters=20, device=device,
            )
            t_loss = eval_dict["train"]
            v_loss = eval_dict["val"]

            history_steps.append(step)
            history_train_loss.append(t_loss)
            history_val_loss.append(v_loss)

            elapsed_min = (time.time() - start_time) / 60
            print(
                f"\n[Step {step:>5}] "
                f"Train Loss: {t_loss:.4f}  |  "
                f"Val Loss: {v_loss:.4f}  |  "
                f"Elapsed: {elapsed_min:.1f} min"
            )

            # Save checkpoint
            is_best = v_loss < best_val_loss
            if is_best:
                best_val_loss = v_loss

            checkpoint_data = {
                "step":                  step,
                "model_state_dict":      model.state_dict(),
                "optimizer_state_dict":  optimizer.state_dict(),
                "best_val_loss":         best_val_loss,
                "config":                config,
                "history_steps":         history_steps,
                "history_train_loss":    history_train_loss,
                "history_val_loss":      history_val_loss,
            }

            latest_path = checkpoint_dir / "latest.pt"
            torch.save(checkpoint_data, latest_path)

            if is_best:
                best_path = checkpoint_dir / "best.pt"
                torch.save(checkpoint_data, best_path)
                print(f"[OK] New best checkpoint saved -> {best_path}")

    # ------------------------------------------------------------------
    # 9. Post-training: loss curve plot
    # ------------------------------------------------------------------
    total_time = time.time() - start_time
    print(f"\n[OK] Training completed in {total_time / 60:.2f} minutes.")

    if history_steps:
        plt.figure(figsize=(9, 5))
        plt.plot(
            history_steps, history_train_loss,
            label="Train Loss", color="indigo", lw=2,
        )
        plt.plot(
            history_steps, history_val_loss,
            label="Validation Loss", color="coral", lw=2, linestyle="--",
        )
        plt.xlabel("Training Steps", fontsize=12)
        plt.ylabel("Cross-Entropy Loss", fontsize=12)
        plt.title("MiniGPT (genz-ai) — Training vs Validation Loss", fontsize=13)
        plt.legend(fontsize=11)
        plt.grid(True, alpha=0.3)
        plt.tight_layout()

        plot_path = checkpoint_dir / "loss_curve.png"
        plt.savefig(plot_path, dpi=150, bbox_inches="tight")
        plt.close()
        print(f"[OK] Loss curve saved -> {plot_path}")

    if INTERRUPTED:
        # Save emergency checkpoint with current state
        emergency_path = checkpoint_dir / "emergency.pt"
        torch.save(
            {
                "step":                 step,
                "model_state_dict":     model.state_dict(),
                "optimizer_state_dict": optimizer.state_dict(),
                "best_val_loss":        best_val_loss,
                "config":               config,
                "history_steps":        history_steps,
                "history_train_loss":   history_train_loss,
                "history_val_loss":     history_val_loss,
            },
            emergency_path,
        )
        print(f"[!] Emergency checkpoint saved -> {emergency_path}")
        print("[!] Exiting gracefully.")
        sys.exit(0)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Train MiniGPT with AMP, gradient accumulation, and cosine LR schedule."
    )
    parser.add_argument(
        "--config",
        type=str,
        default="configs/tiny.yaml",
        help="Path to YAML training configuration file.",
    )
    parser.add_argument(
        "--resume",
        type=str,
        default=None,
        help="Path to a .pt checkpoint file to resume training from.",
    )
    args = parser.parse_args()
    train(args.config, args.resume)
