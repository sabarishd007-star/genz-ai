# app/gui.py
import os
os.environ.setdefault("PYTHONIOENCODING", "utf-8")
import queue
import sys
import threading
import time
import tkinter as tk
from tkinter import filedialog, scrolledtext, ttk
from pathlib import Path
import torch
import yaml

# Ensure src/ is on python path
_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "src"))

from minigpt.model import MiniGPT, MiniGPTConfig
from minigpt.tokenizer import CustomBPETokenizer
from minigpt.sampling import generate

class MiniGPTGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("MiniGPT (genzai) — Desktop Workstation")
        self.root.geometry("1000x700")
        self.root.configure(bg="#0f0f10")

        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model = None
        self.tokenizer = None
        self.config = None
        self.stop_event = threading.Event()
        self.q = queue.Queue()

        self._build_styles()
        self._build_ui()
        
        # Load default best checkpoint if available
        default_ckpt = _REPO_ROOT / "models" / "checkpoints" / "best.pt"
        if default_ckpt.exists():
            self.load_checkpoint(str(default_ckpt))
        self.root.after(100, self._poll_queue)

    def _build_styles(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure(".", background="#0f0f10", foreground="#ffffff", fieldbackground="#18181b")
        style.configure("TLabel", background="#0f0f10", foreground="#e4e4e7", font=("Segoe UI", 10))
        style.configure("Heading.TLabel", font=("Segoe UI", 11, "bold"), foreground="#a78bfa")
        style.configure("TButton", background="#27272a", foreground="#ffffff", bordercolor="#3f3f46", font=("Segoe UI", 10))
        style.map("TButton", background=[("active", "#3f3f46")])

    def _build_ui(self):
        # Main Layout Container
        main_frame = ttk.Frame(self.root, padding=15)
        main_frame.pack(fill=tk.BOTH, expand=True)

        # Top Control Bar (Checkpoint & Device Status)
        top_bar = ttk.Frame(main_frame)
        top_bar.pack(fill=tk.X, pady=(0, 10))

        ttk.Label(top_bar, text="Checkpoint:", style="Heading.TLabel").pack(side=tk.LEFT, padx=(0, 5))
        self.ckpt_label = ttk.Label(top_bar, text="None loaded", foreground="#71717a")
        self.ckpt_label.pack(side=tk.LEFT, padx=(0, 10))

        ttk.Button(top_bar, text="Browse...", command=self.browse_checkpoint).pack(side=tk.LEFT, padx=(0, 20))
        
        device_text = f"Device: {self.device.upper()}"
        if self.device == "cuda":
            device_text += f" ({torch.cuda.get_device_name(0)})"
        self.device_label = ttk.Label(top_bar, text=device_text, foreground="#4ade80")
        self.device_label.pack(side=tk.RIGHT)

        # Paned Window (Left: Controls, Right: Chat Output)
        paned = ttk.PanedWindow(main_frame, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True)

        # Left Panel: Parameters & Controls
        left_panel = ttk.Frame(paned, padding=10)
        paned.add(left_panel, weight=1)

        # Prompt Box
        ttk.Label(left_panel, text="Input Prompt", style="Heading.TLabel").pack(anchor=tk.W, pady=(0, 5))
        self.prompt_text = scrolledtext.ScrolledText(
            left_panel, height=5, bg="#18181b", fg="#ffffff", insertbackground="white",
            font=("Segoe UI", 10), relief=tk.FLAT, bd=1
        )
        self.prompt_text.pack(fill=tk.X, pady=(0, 15))
        self.prompt_text.insert(tk.END, "Once upon a time, in a cozy little house,")

        # Sliders Frame
        sliders_frame = ttk.LabelFrame(left_panel, text="Generation Parameters", padding=10)
        sliders_frame.pack(fill=tk.X, pady=(0, 15))

        self.temp_slider = self._create_slider(sliders_frame, "Temperature", 0.1, 2.0, 0.8, 0.05, 0)
        self.topk_slider = self._create_slider(sliders_frame, "Top-K", 1, 100, 50, 1, 1)
        self.topp_slider = self._create_slider(sliders_frame, "Top-P (Nucleus)", 0.1, 1.0, 0.9, 0.05, 2)
        self.max_tokens_slider = self._create_slider(sliders_frame, "Max New Tokens", 10, 500, 150, 10, 3)
        self.rep_penalty_slider = self._create_slider(sliders_frame, "Repetition Penalty", 1.0, 2.0, 1.1, 0.05, 4)

        # Action Buttons
        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=(10, 0))

        self.gen_btn = tk.Button(
            btn_frame, text="Generate", bg="#7c3aed", fg="#ffffff", font=("Segoe UI", 10, "bold"),
            relief=tk.FLAT, padx=15, pady=8, command=self.start_generation, cursor="hand2"
        )
        self.gen_btn.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 5))

        self.stop_btn = tk.Button(
            btn_frame, text="Stop", bg="#27272a", fg="#ef4444", font=("Segoe UI", 10, "bold"),
            relief=tk.FLAT, padx=15, pady=8, command=self.stop_generation, state=tk.DISABLED, cursor="hand2"
        )
        self.stop_btn.pack(side=tk.RIGHT, fill=tk.X, expand=True, padx=(5, 0))

        # Right Panel: Output & Telemetry
        right_panel = ttk.Frame(paned, padding=10)
        paned.add(right_panel, weight=2)

        ttk.Label(right_panel, text="Generated Output", style="Heading.TLabel").pack(anchor=tk.W, pady=(0, 5))
        
        self.output_text = scrolledtext.ScrolledText(
            right_panel, height=20, bg="#121214", fg="#f4f4f5", insertbackground="white",
            font=("Consolas", 11), relief=tk.FLAT, bd=1, wrap=tk.WORD
        )
        self.output_text.pack(fill=tk.BOTH, expand=True, pady=(0, 10))

        # Telemetry Bar
        telemetry_frame = ttk.Frame(right_panel)
        telemetry_frame.pack(fill=tk.X)

        self.stats_label = ttk.Label(telemetry_frame, text="Speed: 0.00 tokens/sec | Total Tokens: 0", foreground="#a1a1aa")
        self.stats_label.pack(side=tk.LEFT)

        ttk.Button(right_panel, text="Clear Output", command=lambda: self.output_text.delete(1.0, tk.END)).pack(anchor=tk.E, pady=(5,0))

    def _create_slider(self, parent, label_text, from_, to, default, resolution, row):
        ttk.Label(parent, text=label_text).grid(row=row, column=0, sticky=tk.W, pady=2)
        var = tk.DoubleVar(value=default)
        slider = ttk.Scale(parent, from_=from_, to=to, variable=var, orient=tk.HORIZONTAL)
        slider.grid(row=row, column=1, sticky=(tk.W, tk.E), padx=10, pady=2)
        val_label = ttk.Label(parent, text=str(default), width=6)
        val_label.grid(row=row, column=2, sticky=tk.E, pady=2)
        
        slider.configure(command=lambda v, vl=val_label: vl.configure(text=f"{float(v):.2f}" if resolution < 1 else str(int(float(v)))))
        parent.columnconfigure(1, weight=1)
        return var

    def browse_checkpoint(self):
        initial_dir = str(_REPO_ROOT / "models" / "checkpoints")
        filename = filedialog.askopenfilename(initialdir=initial_dir, title="Select Checkpoint", filetypes=[("PyTorch Checkpoints", "*.pt")])
        if filename:
            self.load_checkpoint(filename)

    def load_checkpoint(self, path):
        try:
            ckpt_path = Path(path)
            if not ckpt_path.is_absolute():
                ckpt_path = _REPO_ROOT / ckpt_path

            checkpoint = torch.load(str(ckpt_path), map_location=self.device, weights_only=False)
            self.config = checkpoint["config"]
            model_cfg = self.config["model"]
            data_cfg = self.config.get("data", {})

            tokenizer_path = model_cfg.get("tokenizer_path") or data_cfg.get("tokenizer_path", "models/tokenizer.json")
            tokenizer_file = Path(tokenizer_path)
            if not tokenizer_file.is_absolute():
                tokenizer_file = _REPO_ROOT / tokenizer_file

            self.tokenizer = CustomBPETokenizer(vocab_size=model_cfg["vocab_size"])
            self.tokenizer.load(str(tokenizer_file))

            cfg = MiniGPTConfig(
                block_size=model_cfg["block_size"],
                vocab_size=model_cfg["vocab_size"],
                n_layer=model_cfg["n_layer"],
                n_head=model_cfg["n_head"],
                n_embd=model_cfg["n_embd"],
                dropout=model_cfg.get("dropout", 0.0),
                bias=model_cfg.get("bias", False)
            )
            self.model = MiniGPT(cfg).to(self.device)
            self.model.load_state_dict(checkpoint["model_state_dict"])
            self.model.eval()

            self.ckpt_label.configure(text=str(ckpt_path.name), foreground="#4ade80")
        except Exception as e:
            self.ckpt_label.configure(text=f"Failed to load: {e}", foreground="#ef4444")

    def start_generation(self):
        if not self.model or not self.tokenizer:
            self.output_text.insert(tk.END, "[!] Error: No model checkpoint loaded.\n")
            return

        prompt = self.prompt_text.get("1.0", tk.END).strip()
        if not prompt:
            return

        self.output_text.delete(1.0, tk.END)
        self.output_text.insert(tk.END, prompt + " ")
        
        self.gen_btn.configure(state=tk.DISABLED)
        self.stop_btn.configure(state=tk.NORMAL)
        self.stop_event.clear()

        params = {
            "prompt": prompt,
            "max_tokens": int(self.max_tokens_slider.get()),
            "temperature": self.temp_slider.get(),
            "top_k": int(self.topk_slider.get()),
            "top_p": self.topp_slider.get(),
            "repetition_penalty": self.rep_penalty_slider.get()
        }

        threading.Thread(target=self._generation_worker, args=(params,), daemon=True).start()

    def _generation_worker(self, params):
        start_time = time.time()
        token_count = 0
        try:
            for chunk in generate(
                self.model,
                self.tokenizer,
                prompt=params["prompt"],
                max_new_tokens=params["max_tokens"],
                temperature=params["temperature"],
                top_k=params["top_k"],
                top_p=params["top_p"],
                repetition_penalty=params["repetition_penalty"],
                device=self.device
            ):
                if self.stop_event.is_set():
                    break
                token_count += 1
                elapsed = time.time() - start_time
                tps = token_count / elapsed if elapsed > 0 else 0
                self.q.put(("chunk", chunk, tps, token_count))
        except Exception as e:
            self.q.put(("error", str(e)))
        self.q.put(("done",))

    def stop_generation(self):
        self.stop_event.set()

    def _poll_queue(self):
        try:
            while True:
                msg = self.q.get_nowait()
                if msg[0] == "chunk":
                    _, chunk, tps, count = msg
                    self.output_text.insert(tk.END, chunk)
                    self.output_text.see(tk.END)
                    self.stats_label.configure(text=f"Speed: {tps:.2f} tokens/sec | Total Tokens: {count}")
                elif msg[0] == "error":
                    self.output_text.insert(tk.END, f"\n[!] Generation Error: {msg[1]}\n")
                elif msg[0] == "done":
                    self.gen_btn.configure(state=tk.NORMAL)
                    self.stop_btn.configure(state=tk.DISABLED)
                self.q.task_done()
        except queue.Empty:
            pass
        finally:
            self.root.after(100, self._poll_queue)


if __name__ == "__main__":
    root = tk.Tk()
    app = MiniGPTGUI(root)
    root.mainloop()
