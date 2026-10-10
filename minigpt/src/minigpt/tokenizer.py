# src/minigpt/tokenizer.py
import os
from pathlib import Path
from typing import List, Union
from tokenizers import Tokenizer, models, trainers, pre_tokenizers, decoders

SPECIAL_TOKENS = ["<|endoftext|>", "<|padding|>"]

class CustomBPETokenizer:
    """Byte-level BPE Tokenizer wrapper for MiniGPT training."""
    
    def __init__(self, vocab_size: int = 16000):
        self.vocab_size = vocab_size
        self.tokenizer = None
        
    def train_from_iterator(self, text_iterator, save_path: Union[str, Path] = None):
        # 1. Initialize Byte-Level BPE Model
        bpe_model = models.BPE(unk_token=None)
        tokenizer = Tokenizer(bpe_model)
        
        # 2. Add Byte-Level Pre-Tokenizer & Decoder
        tokenizer.pre_tokenizer = pre_tokenizers.ByteLevel(add_prefix_space=False)
        tokenizer.decoder = decoders.ByteLevel()
        
        # 3. Configure Trainer
        trainer = trainers.BpeTrainer(
            vocab_size=self.vocab_size,
            special_tokens=SPECIAL_TOKENS,
            min_frequency=2
        )
        
        # 4. Train Tokenizer
        tokenizer.train_from_iterator(text_iterator, trainer=trainer)
        self.tokenizer = tokenizer
        
        if save_path:
            save_path = Path(save_path)
            save_path.parent.mkdir(parents=True, exist_ok=True)
            self.tokenizer.save(str(save_path))
            print(f"[✓] Custom BPE Tokenizer saved to: {save_path}")

    def load(self, load_path: Union[str, Path]):
        self.tokenizer = Tokenizer.from_file(str(load_path))
        print(f"[✓] Tokenizer loaded from: {load_path}")

    def encode(self, text: str) -> List[int]:
        return self.tokenizer.encode(text).ids

    def decode(self, ids: List[int]) -> str:
        return self.tokenizer.decode(ids)

    @property
    def eot_token_id(self) -> int:
        return self.tokenizer.token_to_id("<|endoftext|>")

    @property
    def pad_token_id(self) -> int:
        return self.tokenizer.token_to_id("<|padding|>")
