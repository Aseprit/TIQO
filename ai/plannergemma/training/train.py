"""Reproducible SFT of a FunctionGemma-compatible checkpoint for Tiqo tools."""

from __future__ import annotations

import argparse
import json
import random
from datetime import date, datetime
from pathlib import Path

from ai.plannergemma.benchmark.schema import TOOL_SCHEMAS, assistant_tool_message, developer_message


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-model", required=True)
    parser.add_argument("--train", type=Path, default=Path(__file__).resolve().parents[1] / "datasets" / "planner_pl_v1_train.jsonl")
    parser.add_argument("--validation", type=Path, default=Path(__file__).resolve().parents[1] / "datasets" / "planner_pl_v1_validation.jsonl")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--epochs", type=float, default=2.0)
    parser.add_argument("--seed", type=int, default=20260902)
    parser.add_argument("--max-train-samples", type=int)
    parser.add_argument("--max-validation-samples", type=int)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--gradient-accumulation", type=int, default=8)
    parser.add_argument("--max-length", type=int, default=1600)
    parser.add_argument("--no-gradient-checkpointing", action="store_true")
    args = parser.parse_args()

    import torch
    from datasets import load_dataset
    from transformers import AutoModelForCausalLM, AutoTokenizer, set_seed
    from trl import SFTConfig, SFTTrainer

    random.seed(args.seed)
    set_seed(args.seed)
    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float32,
        attn_implementation="eager",
    )
    raw = load_dataset("json", data_files={"train": str(args.train), "validation": str(args.validation)})
    if args.max_train_samples:
        raw["train"] = raw["train"].select(range(min(args.max_train_samples, len(raw["train"]))))
    if args.max_validation_samples:
        raw["validation"] = raw["validation"].select(range(min(args.max_validation_samples, len(raw["validation"]))))

    def format_row(row):
        def json_value(value):
            if isinstance(value, datetime):
                return value.date().isoformat() if value.time().isoformat() == "00:00:00" else value.isoformat()
            if isinstance(value, date):
                return value.isoformat()
            raise TypeError(type(value).__name__)

        row = json.loads(json.dumps(dict(row), default=json_value, ensure_ascii=False))
        prefix_messages = [
            {"role": "developer", "content": developer_message(row["context"])},
            {"role": "user", "content": row["prompt"]},
        ]
        complete_messages = prefix_messages + [assistant_tool_message(row["expected"]["tool"], row["expected"]["arguments"])]
        prompt = tokenizer.apply_chat_template(prefix_messages, tools=TOOL_SCHEMAS, tokenize=False, add_generation_prompt=True)
        full = tokenizer.apply_chat_template(complete_messages, tools=TOOL_SCHEMAS, tokenize=False, add_generation_prompt=False)
        if not full.startswith(prompt):
            raise RuntimeError("Tokenizer chat template did not preserve the prompt prefix")
        return {"prompt": prompt, "completion": full[len(prompt):]}

    prepared = raw.map(format_row, remove_columns=raw["train"].column_names)
    config = SFTConfig(
        output_dir=str(args.output),
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.gradient_accumulation,
        learning_rate=1e-5,
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        logging_steps=10,
        eval_strategy="epoch",
        save_strategy="epoch",
        save_total_limit=2,
        gradient_checkpointing=not args.no_gradient_checkpointing,
        bf16=torch.cuda.is_available() and torch.cuda.is_bf16_supported(),
        fp16=torch.cuda.is_available() and not torch.cuda.is_bf16_supported(),
        completion_only_loss=True,
        max_length=args.max_length,
        seed=args.seed,
        data_seed=args.seed,
        report_to="none",
    )
    args.output.mkdir(parents=True, exist_ok=True)
    run_manifest = {
        "base_model": args.base_model,
        "dataset_version": "planner-pl-v1",
        "train_file": str(args.train),
        "validation_file": str(args.validation),
        "seed": args.seed,
        "epochs": args.epochs,
        "hyperparameters": config.to_dict(),
    }
    (args.output / "tiqo_training_manifest.json").write_text(json.dumps(run_manifest, indent=2) + "\n", encoding="utf-8")
    trainer = SFTTrainer(
        model=model,
        args=config,
        train_dataset=prepared["train"],
        eval_dataset=prepared["validation"],
        processing_class=tokenizer,
    )
    trainer.train()
    trainer.save_model(str(args.output / "final"))
    tokenizer.save_pretrained(str(args.output / "final"))


if __name__ == "__main__":
    main()
