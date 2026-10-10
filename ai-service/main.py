# ai-service/main.py
"""FastAPI microservice serving MiniGPT (genz-ai).

Features:
  - OpenAI-compatible `/v1/chat/completions` endpoint for Spring AI & standard LLM clients
  - Direct streaming `/api/minigpt/generate` endpoint for Web UI playground
  - Hardware & model telemetry `/api/minigpt/status`
  - Zero-overhead streaming via Server-Sent Events (SSE)
"""

import asyncio
import json
import os
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

import torch
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Setup path resolution to load minigpt package
# ---------------------------------------------------------------------------
_CURRENT_DIR = Path(__file__).resolve().parent
_REPO_ROOT = _CURRENT_DIR.parent
sys.path.insert(0, str(_REPO_ROOT / "minigpt" / "src"))

from minigpt.model import MiniGPT, MiniGPTConfig
from minigpt.tokenizer import CustomBPETokenizer
from minigpt.sampling import generate

# ---------------------------------------------------------------------------
# Global Model State
# ---------------------------------------------------------------------------
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
model: Optional[MiniGPT] = None
tokenizer: Optional[CustomBPETokenizer] = None
model_config_dict: Dict[str, Any] = {}

def load_minigpt():
    global model, tokenizer, model_config_dict
    ckpt_path = _REPO_ROOT / "minigpt" / "models" / "checkpoints" / "best.pt"
    if not ckpt_path.exists():
        ckpt_path = _REPO_ROOT / "minigpt" / "models" / "checkpoints" / "latest.pt"

    if not ckpt_path.exists():
        print(f"[!] Warning: No checkpoint found at {ckpt_path}. Model not loaded.")
        return False

    print(f"--> Loading MiniGPT checkpoint: {ckpt_path} onto {DEVICE.upper()}...")
    checkpoint = torch.load(str(ckpt_path), map_location=DEVICE, weights_only=False)
    config = checkpoint["config"]
    model_config_dict = config
    model_cfg = config["model"]
    data_cfg = config.get("data", {})

    tok_path = model_cfg.get("tokenizer_path") or data_cfg.get("tokenizer_path", "models/tokenizer.json")
    tok_file = Path(tok_path)
    if not tok_file.is_absolute():
        tok_file = _REPO_ROOT / "minigpt" / tok_file

    tokenizer = CustomBPETokenizer(vocab_size=model_cfg["vocab_size"])
    tokenizer.load(str(tok_file))

    cfg = MiniGPTConfig(
        block_size=model_cfg["block_size"],
        vocab_size=model_cfg["vocab_size"],
        n_layer=model_cfg["n_layer"],
        n_head=model_cfg["n_head"],
        n_embd=model_cfg["n_embd"],
        dropout=model_cfg.get("dropout", 0.0),
        bias=model_cfg.get("bias", False),
    )
    model = MiniGPT(cfg).to(DEVICE)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    num_params = model.get_num_params() / 1e6
    print(f"[OK] MiniGPT loaded successfully ({num_params:.2f}M parameters on {DEVICE.upper()})")
    return True

# ---------------------------------------------------------------------------
# FastAPI App Initialization & CORS
# ---------------------------------------------------------------------------
app = FastAPI(
    title="GenZ AI — MiniGPT Microservice",
    description="High-performance inference microservice serving from-scratch MiniGPT model.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def startup_event():
    load_minigpt()

# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------
class GenerateRequest(BaseModel):
    prompt: str = Field(..., example="Once upon a time, in a cozy little house,")
    max_tokens: int = Field(150, ge=1, le=500)
    temperature: float = Field(0.8, ge=0.01, le=2.0)
    top_k: int = Field(50, ge=1, le=200)
    top_p: float = Field(0.9, ge=0.01, le=1.0)
    repetition_penalty: float = Field(1.1, ge=1.0, le=2.0)
    stream: bool = Field(True, description="Whether to stream response tokens via SSE")

class ChatMessage(BaseModel):
    role: str
    content: str

class OpenAIChatRequest(BaseModel):
    model: Optional[str] = "minigpt"
    messages: List[ChatMessage]
    temperature: Optional[float] = 0.8
    top_p: Optional[float] = 0.9
    max_tokens: Optional[int] = 150
    stream: Optional[bool] = False

# ---------------------------------------------------------------------------
# Health & Status Endpoints
# ---------------------------------------------------------------------------
@app.get("/health")
def health():
    return {
        "status": "healthy",
        "service": "ai-service",
        "model_loaded": model is not None,
        "device": DEVICE,
    }

@app.get("/api/minigpt/status")
def minigpt_status():
    vram_mb = 0.0
    gpu_name = "CPU"
    if torch.cuda.is_available():
        vram_mb = torch.cuda.memory_allocated() / (1024 ** 2)
        gpu_name = torch.cuda.get_device_name(0)

    num_params = model.get_num_params() if model else 0

    return {
        "status": "online" if model is not None else "offline",
        "model_name": "MiniGPT (genzai)",
        "parameters": f"{num_params / 1e6:.2f}M",
        "device": DEVICE.upper(),
        "gpu_name": gpu_name,
        "vram_allocated_mb": round(vram_mb, 2),
        "context_window": model.config.block_size if model else 256,
        "vocab_size": model.config.vocab_size if model else 16000,
    }

# ---------------------------------------------------------------------------
# Web UI Streaming Generation Endpoint
# ---------------------------------------------------------------------------
@app.post("/api/minigpt/generate")
async def generate_text(req: GenerateRequest):
    if model is None or tokenizer is None:
        raise HTTPException(status_code=503, detail="MiniGPT model is not loaded.")

    if not req.stream:
        # Non-streaming full generation
        start_time = time.time()
        tokens = []
        for chunk in generate(
            model,
            tokenizer,
            prompt=req.prompt,
            max_new_tokens=req.max_tokens,
            temperature=req.temperature,
            top_k=req.top_k,
            top_p=req.top_p,
            repetition_penalty=req.repetition_penalty,
            device=DEVICE,
        ):
            tokens.append(chunk)

        elapsed = time.time() - start_time
        full_text = req.prompt + "".join(tokens)
        tps = len(tokens) / elapsed if elapsed > 0 else 0

        return {
            "prompt": req.prompt,
            "completion": "".join(tokens),
            "full_text": full_text,
            "tokens_generated": len(tokens),
            "elapsed_seconds": round(elapsed, 3),
            "tokens_per_second": round(tps, 2),
        }

    # Streaming via Server-Sent Events (SSE)
    async def token_generator():
        start_time = time.time()
        token_count = 0
        try:
            for chunk in generate(
                model,
                tokenizer,
                prompt=req.prompt,
                max_new_tokens=req.max_tokens,
                temperature=req.temperature,
                top_k=req.top_k,
                top_p=req.top_p,
                repetition_penalty=req.repetition_penalty,
                device=DEVICE,
            ):
                token_count += 1
                elapsed = time.time() - start_time
                tps = token_count / elapsed if elapsed > 0 else 0
                payload = {
                    "chunk": chunk,
                    "count": token_count,
                    "tps": round(tps, 2),
                }
                yield f"data: {json.dumps(payload)}\n\n"
                await asyncio.sleep(0.001)  # Yield control to event loop

            yield "data: [DONE]\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"

    return StreamingResponse(
        token_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )

# ---------------------------------------------------------------------------
# OpenAI-Compatible Chat Completions Endpoint (/v1/chat/completions)
# Used by Spring AI (ChatClient) & standard OpenAI client libraries
# ---------------------------------------------------------------------------
@app.post("/v1/chat/completions")
async def chat_completions(req: OpenAIChatRequest):
    if model is None or tokenizer is None:
        raise HTTPException(status_code=503, detail="MiniGPT model is not loaded.")

    # Flatten conversation messages into a single prompt
    prompt_parts = []
    for m in req.messages:
        if m.role in ("user", "system"):
            prompt_parts.append(m.content)
    full_prompt = " ".join(prompt_parts).strip()
    if not full_prompt:
        full_prompt = "Once upon a time"

    max_toks = req.max_tokens or 150
    temp = req.temperature or 0.8
    p = req.top_p or 0.9

    completion_id = f"chatcmpl-{int(time.time() * 1000)}"

    if req.stream:
        # Streaming OpenAI SSE format
        async def openai_sse():
            for chunk in generate(
                model,
                tokenizer,
                prompt=full_prompt,
                max_new_tokens=max_toks,
                temperature=temp,
                top_p=p,
                device=DEVICE,
            ):
                chunk_data = {
                    "id": completion_id,
                    "object": "chat.completion.chunk",
                    "created": int(time.time()),
                    "model": "minigpt",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {"content": chunk},
                            "finish_reason": None,
                        }
                    ],
                }
                yield f"data: {json.dumps(chunk_data)}\n\n"
                await asyncio.sleep(0.001)

            yield "data: [DONE]\n\n"

        return StreamingResponse(
            openai_sse(),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )

    # Non-streaming standard response
    tokens = []
    for chunk in generate(
        model,
        tokenizer,
        prompt=full_prompt,
        max_new_tokens=max_toks,
        temperature=temp,
        top_p=p,
        device=DEVICE,
    ):
        tokens.append(chunk)

    generated_text = "".join(tokens)

    return {
        "id": completion_id,
        "object": "chat.completion",
        "created": int(time.time()),
        "model": "minigpt",
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": generated_text,
                },
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": len(tokenizer.encode(full_prompt)),
            "completion_tokens": len(tokens),
            "total_tokens": len(tokenizer.encode(full_prompt)) + len(tokens),
        },
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
