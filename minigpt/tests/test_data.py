from pathlib import Path
import torch
from minigpt.data import MemmapTextDataset, create_dataloader

def test_dataloader_shapes():
    processed_dir = Path(__file__).resolve().parent.parent / "data" / "processed"
    train_bin = processed_dir / "train.bin"
    if not train_bin.exists():
        print("train.bin not generated yet")
        return
        
    loader = create_dataloader(train_bin, block_size=256, micro_batch_size=8, shuffle=False)
    x, y = next(iter(loader))
    
    assert x.shape == (8, 256), f"Expected x shape (8, 256), got {x.shape}"
    assert y.shape == (8, 256), f"Expected y shape (8, 256), got {y.shape}"
    assert x.dtype == torch.long
    assert y.dtype == torch.long
    
    # Check shift: y[0, :-1] should match x[0, 1:] for consecutive sequence tokens
    assert torch.equal(x[0, 1:], y[0, :-1]), "y should be exactly x shifted by 1 position"
