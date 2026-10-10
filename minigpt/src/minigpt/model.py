# src/minigpt/model.py
"""GPT model architecture implementation from scratch.

Implements a decoder-only Transformer (GPT-style) with:
  - Multi-Head Causal Self-Attention (Flash Attention when available)
  - LayerNorm (with optional bias)
  - Feed-Forward MLP with GELU activation
  - Weight tying between token embedding and output projection
  - AdamW optimizer with fused kernel path (when on CUDA)
"""

import math
import inspect
from dataclasses import dataclass
from typing import Optional, Tuple

import torch
import torch.nn as nn
import torch.nn.functional as F


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

@dataclass
class MiniGPTConfig:
    block_size: int = 256        # Maximum sequence length (context window)
    vocab_size: int = 16000      # BPE vocabulary size from tokenizer
    n_layer: int = 6             # Number of Transformer decoder blocks
    n_head: int = 6              # Number of attention heads
    n_embd: int = 384            # Embedding dimension (d_model)
    dropout: float = 0.1         # Dropout probability applied globally
    bias: bool = False           # Whether to use bias in Linear and LayerNorm layers


# ---------------------------------------------------------------------------
# Building Blocks
# ---------------------------------------------------------------------------

class LayerNorm(nn.Module):
    """LayerNorm with optional bias.

    PyTorch built-in LayerNorm always adds a bias; this wrapper allows
    bias-free operation as used in GPT-2/3-style models.
    """

    def __init__(self, ndim: int, bias: bool = False):
        super().__init__()
        self.weight = nn.Parameter(torch.ones(ndim))
        self.bias = nn.Parameter(torch.zeros(ndim)) if bias else None

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return F.layer_norm(x, self.weight.shape, self.weight, self.bias, eps=1e-5)


class CausalSelfAttention(nn.Module):
    """Multi-head causal (masked) self-attention.

    Uses torch.nn.functional.scaled_dot_product_attention which automatically
    dispatches to Flash Attention on supported hardware.
    """

    def __init__(self, config: MiniGPTConfig):
        super().__init__()
        assert config.n_embd % config.n_head == 0, (
            f"n_embd ({config.n_embd}) must be divisible by n_head ({config.n_head})"
        )
        self.n_head = config.n_head
        self.n_embd = config.n_embd
        self.head_dim = config.n_embd // config.n_head
        self.dropout_p = config.dropout

        # Fused QKV projection: 3 x n_embd in a single Linear for efficiency
        self.c_attn = nn.Linear(config.n_embd, 3 * config.n_embd, bias=config.bias)
        self.c_proj = nn.Linear(config.n_embd, config.n_embd, bias=config.bias)

        self.attn_dropout = nn.Dropout(config.dropout)
        self.resid_dropout = nn.Dropout(config.dropout)

        # Flash Attention availability flag (PyTorch >= 2.0)
        self.flash = hasattr(F, "scaled_dot_product_attention")

        if not self.flash:
            # Fallback: register a causal mask buffer
            self.register_buffer(
                "bias",
                torch.tril(torch.ones(config.block_size, config.block_size)).view(
                    1, 1, config.block_size, config.block_size
                ),
            )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        B, T, C = x.shape  # (batch, seq_len, n_embd)

        # Compute Q, K, V all at once then split
        q, k, v = self.c_attn(x).split(self.n_embd, dim=2)

        # Reshape to (B, n_head, T, head_dim)
        q = q.view(B, T, self.n_head, self.head_dim).transpose(1, 2)
        k = k.view(B, T, self.n_head, self.head_dim).transpose(1, 2)
        v = v.view(B, T, self.n_head, self.head_dim).transpose(1, 2)

        if self.flash:
            # Flash Attention - handles masking, scaling, and softmax internally
            y = F.scaled_dot_product_attention(
                q, k, v,
                attn_mask=None,
                dropout_p=self.dropout_p if self.training else 0.0,
                is_causal=True,
            )
        else:
            # Manual scaled dot-product attention with causal mask
            scale = 1.0 / math.sqrt(self.head_dim)
            att = (q @ k.transpose(-2, -1)) * scale
            att = att.masked_fill(self.bias[:, :, :T, :T] == 0, float("-inf"))
            att = F.softmax(att, dim=-1)
            att = self.attn_dropout(att)
            y = att @ v  # (B, n_head, T, head_dim)

        # Re-assemble heads: (B, n_head, T, head_dim) -> (B, T, C)
        y = y.transpose(1, 2).contiguous().view(B, T, C)
        return self.resid_dropout(self.c_proj(y))


class MLP(nn.Module):
    """Position-wise Feed-Forward Network with GELU activation.

    Expands n_embd by 4x (standard GPT ratio), applies GELU, then projects back.
    """

    def __init__(self, config: MiniGPTConfig):
        super().__init__()
        self.c_fc   = nn.Linear(config.n_embd, 4 * config.n_embd, bias=config.bias)
        self.gelu   = nn.GELU()
        self.c_proj = nn.Linear(4 * config.n_embd, config.n_embd, bias=config.bias)
        self.dropout = nn.Dropout(config.dropout)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.dropout(self.c_proj(self.gelu(self.c_fc(x))))


class Block(nn.Module):
    """Single Transformer decoder block: Pre-LayerNorm -> Attention -> Pre-LayerNorm -> MLP."""

    def __init__(self, config: MiniGPTConfig):
        super().__init__()
        self.ln_1 = LayerNorm(config.n_embd, bias=config.bias)
        self.attn = CausalSelfAttention(config)
        self.ln_2 = LayerNorm(config.n_embd, bias=config.bias)
        self.mlp  = MLP(config)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + self.attn(self.ln_1(x))  # Residual connection around attention
        x = x + self.mlp(self.ln_2(x))   # Residual connection around MLP
        return x


# ---------------------------------------------------------------------------
# Main Model
# ---------------------------------------------------------------------------

class MiniGPT(nn.Module):
    """Decoder-only GPT language model (~17M parameters with default tiny config).

    Architecture:
        Token Embedding + Positional Embedding
        -> N x Transformer Block (Attn + MLP, pre-LN)
        -> Final LayerNorm
        -> Linear head (weight-tied to token embedding)

    Returns (logits, loss, perplexity) when targets are provided,
    or (logits, None, None) for inference-only forward passes.
    """

    def __init__(self, config: MiniGPTConfig):
        super().__init__()
        self.config = config

        self.transformer = nn.ModuleDict(dict(
            wte  = nn.Embedding(config.vocab_size, config.n_embd),   # Token embeddings
            wpe  = nn.Embedding(config.block_size, config.n_embd),   # Position embeddings
            drop = nn.Dropout(config.dropout),
            h    = nn.ModuleList([Block(config) for _ in range(config.n_layer)]),
            ln_f = LayerNorm(config.n_embd, bias=config.bias),       # Final layer norm
        ))

        # Output projection (no bias; weight-tied to wte)
        self.lm_head = nn.Linear(config.n_embd, config.vocab_size, bias=False)

        # Weight tying: share parameters between token embedding and output logit layer
        # Saves ~vocab_size x n_embd parameters and improves generalisation
        self.transformer.wte.weight = self.lm_head.weight

        # Initialise all weights
        self.apply(self._init_weights)

        # Scale residual projections by 1/sqrt(2 * n_layer) following GPT-2 paper
        for pn, p in self.named_parameters():
            if pn.endswith("c_proj.weight"):
                nn.init.normal_(p, mean=0.0, std=0.02 / math.sqrt(2 * config.n_layer))

    # ------------------------------------------------------------------
    # Weight Initialisation
    # ------------------------------------------------------------------

    def _init_weights(self, module: nn.Module) -> None:
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    # ------------------------------------------------------------------
    # Forward Pass
    # ------------------------------------------------------------------

    def forward(
        self,
        idx: torch.Tensor,
        targets: Optional[torch.Tensor] = None,
    ) -> Tuple[torch.Tensor, Optional[torch.Tensor], Optional[torch.Tensor]]:
        """Forward pass.

        Args:
            idx:     (B, T) integer token indices.
            targets: (B, T) integer target indices for loss computation.

        Returns:
            logits:     (B, T, vocab_size) unnormalised log probabilities.
            loss:       Scalar cross-entropy loss (None if targets not given).
            perplexity: exp(loss) as a convenience metric (None if no loss).
        """
        device = idx.device
        B, T = idx.shape
        assert T <= self.config.block_size, (
            f"Sequence length {T} exceeds model block_size {self.config.block_size}"
        )

        # Token + positional embeddings
        pos = torch.arange(0, T, dtype=torch.long, device=device)  # (T,)
        tok_emb = self.transformer.wte(idx)   # (B, T, n_embd)
        pos_emb = self.transformer.wpe(pos)   # (T, n_embd) - broadcast over B
        x = self.transformer.drop(tok_emb + pos_emb)

        # Pass through all Transformer blocks
        for block in self.transformer.h:
            x = block(x)

        x = self.transformer.ln_f(x)

        if targets is not None:
            # Training / evaluation: compute loss over all positions
            logits = self.lm_head(x)  # (B, T, vocab_size)
            loss = F.cross_entropy(
                logits.view(-1, logits.size(-1)),
                targets.view(-1),
                ignore_index=-1,
            )
            perplexity = torch.exp(loss)
            return logits, loss, perplexity
        else:
            # Inference: only compute logits for the last position (efficiency)
            logits = self.lm_head(x[:, [-1], :])  # (B, 1, vocab_size)
            return logits, None, None

    # ------------------------------------------------------------------
    # Parameter Count Helper
    # ------------------------------------------------------------------

    def get_num_params(self, non_embedding: bool = True) -> int:
        """Return the number of parameters in the model.

        Args:
            non_embedding: If True, subtract position embedding params.
        """
        n_params = sum(p.numel() for p in self.parameters())
        if non_embedding:
            n_params -= self.transformer.wpe.weight.numel()
        return n_params

    # ------------------------------------------------------------------
    # Optimiser Configuration
    # ------------------------------------------------------------------

    def configure_optimizers(
        self,
        weight_decay: float,
        learning_rate: float,
        betas: Tuple[float, float],
        device_type: str,
    ) -> torch.optim.AdamW:
        """Create AdamW optimiser with weight-decay applied only to 2-D tensors.

        Following GPT-3/nanoGPT convention:
          - 2-D parameters (weight matrices): apply weight decay
          - 1-D parameters (biases, LayerNorm weights): no weight decay
        Also enables the fused AdamW kernel on CUDA for ~15% speed-up.
        """
        param_dict = {pn: p for pn, p in self.named_parameters() if p.requires_grad}

        decay_params   = [p for p in param_dict.values() if p.dim() >= 2]
        nodecay_params = [p for p in param_dict.values() if p.dim() < 2]

        optim_groups = [
            {"params": decay_params,   "weight_decay": weight_decay},
            {"params": nodecay_params, "weight_decay": 0.0},
        ]

        # Use fused AdamW if available (CUDA only, PyTorch >= 2.0)
        fused_available = "fused" in inspect.signature(torch.optim.AdamW).parameters
        use_fused = fused_available and device_type == "cuda"

        optimizer = torch.optim.AdamW(
            optim_groups,
            lr=learning_rate,
            betas=betas,
            fused=use_fused,
        )

        n_decay   = sum(p.numel() for p in decay_params)
        n_nodecay = sum(p.numel() for p in nodecay_params)
        print(
            f"[Optimiser] Decay params: {n_decay:,}  |  "
            f"No-decay params: {n_nodecay:,}  |  "
            f"Fused AdamW: {use_fused}"
        )
        return optimizer

    # ------------------------------------------------------------------
    # Text Generation (Autoregressive Sampling)
    # ------------------------------------------------------------------

    @torch.no_grad()
    def generate(
        self,
        idx: torch.Tensor,
        max_new_tokens: int,
        temperature: float = 1.0,
        top_k: Optional[int] = None,
    ) -> torch.Tensor:
        """Autoregressively generate tokens from a prompt.

        Args:
            idx:            (B, T) prompt token indices.
            max_new_tokens: Number of new tokens to generate.
            temperature:    Softmax temperature (< 1 = sharper, > 1 = more random).
            top_k:          If set, restrict sampling to the top-k logits.

        Returns:
            (B, T + max_new_tokens) tensor of token indices.
        """
        for _ in range(max_new_tokens):
            # Crop context to block_size (sliding window)
            idx_cond = idx if idx.size(1) <= self.config.block_size else idx[:, -self.config.block_size:]

            logits, _, _ = self(idx_cond)           # (B, 1, vocab_size)
            logits = logits[:, -1, :] / temperature  # (B, vocab_size)

            if top_k is not None:
                v, _ = torch.topk(logits, min(top_k, logits.size(-1)))
                logits[logits < v[:, [-1]]] = float("-inf")

            probs = F.softmax(logits, dim=-1)
            idx_next = torch.multinomial(probs, num_samples=1)  # (B, 1)
            idx = torch.cat([idx, idx_next], dim=1)

        return idx


__all__ = ["MiniGPTConfig", "MiniGPT"]
