#!/usr/bin/env python3
"""Offline seed pipeline for AQuA-RAT dataset.

Downloads public validation/train/test parquet slices from deepmind/aqua_rat on Hugging Face,
strictly sanitises and validates them, tags topics, and outputs ~500 clean quantitative questions.

Do NOT commit the raw downloaded parquet files.
"""

import hashlib
import io
import json
import re
import urllib.request
from collections import Counter
from pathlib import Path

import pyarrow.parquet as pq

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_FILE = REPO_ROOT / "backend" / "src" / "main" / "resources" / "aptitude" / "quantitative_aqua_rat.json"

TARGET_COUNT = 500

HF_PARQUET_URLS = [
    "https://huggingface.co/datasets/deepmind/aqua_rat/resolve/main/raw/validation-00000-of-00001.parquet",
    "https://huggingface.co/datasets/deepmind/aqua_rat/resolve/main/raw/test-00000-of-00001.parquet",
    "https://huggingface.co/datasets/deepmind/aqua_rat/resolve/main/raw/train-00000-of-00001.parquet",
]

TOPIC_KEYWORDS = {
    "PERCENTAGE": ["percent", "%", "percentage"],
    "RATIO_PROPORTION": ["ratio", "proportion", "proportional", "divided in the ratio"],
    "TIME_WORK": ["time and work", "piece of work", "days to complete", "work together", "work alone",
                  "pipe", "cistern", "fill a tank", "emptied in"],
    "SPEED_DISTANCE": ["speed", "distance", "train", "km/h", "kmph", "mph", "miles per hour",
                       "kilometers per hour", "boat", "stream", "upstream", "downstream"],
    "INTEREST": ["interest", "compound interest", "simple interest", "principal", "per annum",
                 "rate of interest"],
    "PROBABILITY": ["probability", "dice", "cards", "coin", "tossed", "drawn from", "marble"],
    "PERMUTATIONS_COMBINATIONS": ["permutation", "combination", "ways to arrange", "ways can",
                                  "different ways", "selections"],
    "AVERAGES": ["average", "mean weight", "average age", "mean of", "average score"],
    "PROFIT_LOSS": ["profit", "loss", "cost price", "selling price", "discount", "marked price",
                    "c.p.", "s.p."],
    "GEOMETRY_MENSURATION": ["area", "perimeter", "volume", "cylinder", "sphere", "cone", "radius",
                              "triangle", "rectangle", "square"],
    "ALGEBRA": ["equation", "algebra", "polynomial", "quadratic", "value of x", "root",
                "linear equation"],
}

# Fixed: no empty string alternatives; targets actual UTF-8 mojibake sequences
GARBLED_REGEX = re.compile(r"â\x80|Ã[^\s]|\ufffd|\\u00[89][0-9a-f]")


def clean_option_text(opt: str) -> str:
    # Strip leading option letter e.g. "A)", "B.", "C:"
    cleaned = re.sub(r"^[A-Ea-e]\s*[\)\.\:\-–—]\s*", "", opt.strip())
    return cleaned.strip()


def tag_topic(question: str) -> str:
    q_lower = question.lower()
    for topic, keywords in TOPIC_KEYWORDS.items():
        for kw in keywords:
            if kw in q_lower:
                return topic
    return "OTHER"


def check_rationale_match(rationale: str, correct_key: str) -> bool:
    """Return False if rationale explicitly states a different answer letter."""
    if not rationale:
        return True
    patterns = [
        r"(?:correct\s+(?:answer|choice|option)|answer\s*is|ans\s*is)\s*[:\-–—]?\s*([A-E])\b",
        r"\b([A-E])\s*(?:is\s+(?:the\s+)?correct\s+(?:answer|choice|option)|is\s+correct)\b",
        r"(?:answer|ans)\s*[:=]\s*([A-E])\b",
    ]
    for p in patterns:
        m = re.search(p, rationale, re.IGNORECASE)
        if m:
            stated = m.group(1).upper()
            if stated != correct_key:
                return False
    return True


def validate_and_clean_row(row: dict, drop_counters: Counter):
    q = (row.get("question") or "").strip()
    opts = row.get("options") or []
    rationale = (row.get("rationale") or "").strip()
    correct = (row.get("correct") or "").strip().upper()

    # Rule 1: Exactly 5 options
    if not isinstance(opts, list) or len(opts) != 5:
        drop_counters["rule_not_5_options"] += 1
        return None

    # Rule 2: Valid correct key A-E
    if correct not in {"A", "B", "C", "D", "E"}:
        drop_counters["rule_invalid_correct_key"] += 1
        return None

    # Clean options (strip leading letter prefix)
    cleaned_opts = [clean_option_text(str(o)) for o in opts]

    # Rule 3: Empty question or any empty option
    if not q or any(not opt for opt in cleaned_opts):
        drop_counters["rule_empty_options"] += 1
        return None

    # Rule 4: Duplicate options after normalization
    if len({o.lower() for o in cleaned_opts}) < 5:
        drop_counters["rule_duplicate_options"] += 1
        return None

    # Rule 5: Single-char placeholder options
    if any(len(o) == 1 for o in cleaned_opts):
        drop_counters["rule_single_letter_placeholder"] += 1
        return None

    # Rule 6: Garbled unicode / mojibake
    all_text = q + " " + " ".join(cleaned_opts) + " " + rationale
    if GARBLED_REGEX.search(all_text):
        drop_counters["rule_garbled_unicode"] += 1
        return None

    # Rule 7: Rationale explicitly states a mismatching correct letter
    if not check_rationale_match(rationale, correct):
        drop_counters["rule_rationale_letter_mismatch"] += 1
        return None

    topic = tag_topic(q)
    content_hash = hashlib.sha256((q + "".join(cleaned_opts)).encode()).hexdigest()

    return {
        "contentHash": content_hash,
        "question": q,
        "options": cleaned_opts,
        "correct": correct,
        "rationale": rationale,
        "track": "QUANTITATIVE",
        "topic": topic,
        "difficulty": "MEDIUM",
        "difficultyApproximate": True,
        "source": "AQUA_RAT",
        "flaggedForReview": False,
    }


def main():
    print("=== AQuA-RAT Cleaning & Curation Pipeline ===")
    drop_counters: Counter = Counter()
    seen_hashes: set = set()
    clean_questions = []

    for url in HF_PARQUET_URLS:
        print(f"\nFetching: {url}")
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (genz-ai-seeder)"})
        with urllib.request.urlopen(req, timeout=60) as response:
            content = response.read()

        table = pq.read_table(io.BytesIO(content))
        d = table.to_pydict()
        num_rows = len(d["question"])
        print(f"Loaded {num_rows} rows.")

        for i in range(num_rows):
            row = {
                "question": d["question"][i],
                "options": list(d["options"][i]),
                "rationale": d["rationale"][i],
                "correct": d["correct"][i],
            }
            cleaned = validate_and_clean_row(row, drop_counters)
            if cleaned is None:
                continue
            if cleaned["contentHash"] in seen_hashes:
                drop_counters["rule_duplicate_content"] += 1
                continue
            seen_hashes.add(cleaned["contentHash"])
            clean_questions.append(cleaned)
            if len(clean_questions) >= TARGET_COUNT:
                break

        if len(clean_questions) >= TARGET_COUNT:
            break

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(clean_questions, f, indent=2, ensure_ascii=False)

    print(f"\n[+] Curated seed → {OUTPUT_FILE}")
    print(f"[+] Clean questions saved : {len(clean_questions)}")
    print("\n--- Drops Per Validation Rule ---")
    for rule, count in sorted(drop_counters.items()):
        print(f"  {rule:<40}: {count}")

    print("\n--- Topic Distribution ---")
    topic_counts = Counter(q["topic"] for q in clean_questions)
    for topic, count in sorted(topic_counts.items(), key=lambda x: -x[1]):
        pct = (count / max(len(clean_questions), 1)) * 100
        print(f"  {topic:<30}: {count:3d} ({pct:5.1f}%)")


if __name__ == "__main__":
    main()
