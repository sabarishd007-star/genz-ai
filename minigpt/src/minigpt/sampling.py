# src/minigpt/sampling.py
from typing import Generator
import torch
import torch.nn.functional as F

def sample_next_token(
    logits: torch.Tensor,
    temperature: float = 1.0,
    top_k: int = 50,
    top_p: float = 0.9,
    repetition_penalty: float = 1.1,
    generated_tokens: list = None
) -> torch.Tensor:
    # logits shape: (1, vocab_size)
    logits = logits.clone()
    logits = logits / max(temperature, 1e-5)
    
    # Apply repetition penalty
    if repetition_penalty != 1.0 and generated_tokens:
        for token_id in set(generated_tokens):
            if logits[0, token_id] < 0:
                logits[0, token_id] *= repetition_penalty
            else:
                logits[0, token_id] /= repetition_penalty

    # Top-K filtering
    if top_k > 0:
        values, _ = torch.topk(logits, min(top_k, logits.size(-1)))
        logits[logits < values[:, [-1]]] = float('-inf')

    # Top-P (Nucleus) filtering
    if top_p < 1.0:
        sorted_logits, sorted_indices = torch.sort(logits, descending=True)
        cumulative_probs = torch.cumsum(F.softmax(sorted_logits, dim=-1), dim=-1)
        
        # Remove tokens with cumulative probability above the threshold
        sorted_indices_to_remove = cumulative_probs > top_p
        # Shift the indices to the right to keep also the first token above the threshold
        sorted_indices_to_remove[..., 1:] = sorted_indices_to_remove[..., :-1].clone()
        sorted_indices_to_remove[..., 0] = 0

        indices_to_remove = sorted_indices[sorted_indices_to_remove]
        logits[0, indices_to_remove] = float('-inf')

    # Sample from the filtered distribution
    probs = F.softmax(logits, dim=-1)
    next_token = torch.multinomial(probs, num_samples=1)
    return next_token


@torch.no_grad()
def generate(
    model,
    tokenizer,
    prompt: str,
    max_new_tokens: int = 150,
    temperature: float = 0.8,
    top_k: int = 50,
    top_p: float = 0.9,
    repetition_penalty: float = 1.1,
    device: str = "cuda"
) -> Generator[str, None, None]:
    model.eval()
    
    # Tokenize input prompt
    encoded_prompt = tokenizer.encode(prompt)
    idx = torch.tensor([encoded_prompt], dtype=torch.long, device=device)
    
    generated_tokens = list(encoded_prompt)
    kv_caches = None
    use_cache = True

    for _ in range(max_new_tokens):
        # Crop context if sequence exceeds block size
        idx_cond = idx if idx.size(1) <= model.config.block_size else idx[:, -model.config.block_size:]
        
        if kv_caches is None:
            logits, _, kv_caches = model(idx_cond, use_cache=use_cache)
        else:
            # Pass only the last generated token when KV-cache is active
            logits, _, kv_caches = model(idx[:, [-1]], kv_caches=kv_caches, use_cache=use_cache)

        # Get logits for the last token
        next_logits = logits[:, -1, :]
        
        next_token = sample_next_token(
            next_logits,
            temperature=temperature,
            top_k=top_k,
            top_p=top_p,
            repetition_penalty=repetition_penalty,
            generated_tokens=generated_tokens
        )
        
        idx = torch.cat([idx, next_token], dim=1)
        token_id = next_token.item()
        generated_tokens.append(token_id)
        
        # Stop if EOT token is reached
        if token_id == tokenizer.eot_token_id:
            break
            
        # Yield newly decoded token chunk
        chunk = tokenizer.decode([token_id])
        yield chunk
