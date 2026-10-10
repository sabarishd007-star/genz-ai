# src/minigpt/data.py
from pathlib import Path
import numpy as np
import torch
from torch.utils.data import Dataset, DataLoader

class MemmapTextDataset(Dataset):
    """Memory-mapped binary token dataset producing shifted (x, y) target sequence pairs."""
    
    def __init__(self, bin_path: Path, block_size: int):
        self.bin_path = Path(bin_path)
        self.block_size = block_size
        
        # Load binary tokens via np.memmap (Zero system RAM footprint)
        self.data = np.memmap(str(self.bin_path), dtype=np.uint16, mode="r")
        self.total_samples = len(self.data) - self.block_size

    def __len__(self):
        return self.total_samples

    def __getitem__(self, idx):
        # x is sequence of length block_size
        # y is targets shifted by 1 position: y[i] = x[i + 1]
        chunk = self.data[idx : idx + self.block_size + 1].astype(np.int64)
        x = torch.tensor(chunk[:-1], dtype=torch.long)
        y = torch.tensor(chunk[1:], dtype=torch.long)
        return x, y

def create_dataloader(bin_path: Path, block_size: int, micro_batch_size: int, shuffle: bool = True) -> DataLoader:
    dataset = MemmapTextDataset(bin_path, block_size)
    # num_workers=0 avoids Windows multiprocessing spawning overhead
    return DataLoader(
        dataset,
        batch_size=micro_batch_size,
        shuffle=shuffle,
        num_workers=0,
        pin_memory=True
    )
