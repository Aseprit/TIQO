# PlannerGemma

> **Status: experimental / paused.** Custom SFT, długie benchmarki FunctionGemma i konwersja Android nie są obecnie uruchamiane. Cały dataset, pipeline, wyniki i dokumentacja pozostają zachowane. Aktywny eksperyment zerowego fine-tuningu dla Qwen3-0.6B znajduje się w [../benchmark/README.md](../benchmark/README.md).

This directory contains the reproducible, UI-independent pipeline for Tiqo's Polish local planner model.

## What is here

- `benchmark/` — canonical ten-tool schema, deterministic data generator and validators.
- `datasets/` — `planner-pl-v1`: 10,000 train, 1,000 validation and 720 held-out test examples.
- `evaluation/` — greedy checkpoint runner, per-style/per-category metrics, confusion matrix and failure records.
- `training/` — completion-only supervised fine-tuning (SFT).
- `conversion/` — dynamic-int8 `.litertlm` conversion entry point for Linux/Colab.
- `results/` — versioned benchmark reports. Machine-local experiments belong in `results/local/`.

The test set uses a separate template family and a fixed date/time context. The validator checks IDs, schema, tool names, required values, dates, times, recurrence rules, exact duplicates and train/test family separation. Inputs cover correct Polish, colloquial Polish, missing diacritics, typos and STT-like text in equal proportions.

## Reproduce the dataset

From the repository root:

```powershell
py -3.12 -m ai.plannergemma.benchmark.generate_dataset
py -3.12 -m ai.plannergemma.benchmark.validate_dataset `
  ai/plannergemma/datasets/planner_pl_v1_train.jsonl `
  ai/plannergemma/datasets/planner_pl_v1_validation.jsonl `
  ai/plannergemma/datasets/planner_pl_v1_test.jsonl
py -3.12 -m unittest discover -s ai/plannergemma -p "test_*.py" -v
```

The committed manifest fixes seed `20260902` and dataset version `planner-pl-v1`.

## Environment

```powershell
py -3.12 -m venv ai/plannergemma/.venv
ai/plannergemma/.venv/Scripts/python -m pip install -r ai/plannergemma/requirements.txt
```

For NVIDIA on Windows install the matching official CUDA wheel. The development run used PyTorch 2.8.0 CUDA 12.6:

```powershell
ai/plannergemma/.venv/Scripts/python -m pip install --force-reinstall `
  torch==2.8.0 --index-url https://download.pytorch.org/whl/cu126
ai/plannergemma/.venv/Scripts/python -m pip install --force-reinstall --no-deps fsspec==2025.3.0
```

## Evaluate

```powershell
ai/plannergemma/.venv/Scripts/python -m ai.plannergemma.evaluation.run `
  --model independently-platform/Tasky `
  --output ai/plannergemma/results/tasky-v0
```

Every run stores `summary.json`, `report.md` and every raw prediction in `predictions.jsonl`. Reported metrics include tool selection, exact required and optional arguments, full structured action, date, time, recurrence, delegate, clarification, malformed output and false-positive mutation rate. Host latency is not Android performance.

## Candidate access and licensing

| Checkpoint | Access | Direct Tiqo suitability |
|---|---|---|
| `google/functiongemma-270m-it` | Manually gated under Gemma terms | Correct base family; cannot be downloaded without the user's accepted license/token. |
| `independently-platform/Tasky` | Public | Todo-oriented, but uses its own tool vocabulary and requires Tiqo evaluation/fine-tuning. |
| `Qrzysztof/functiongemma-270m-it-prepaid-cards-v2` | Public | Multilingual but trained for prepaid cards, so domain transfer must be measured. |
| FunctionGemma Mobile Actions | Manually gated | Official ready `.litertlm`; phone-action tools do not match Tiqo's schema. |

Tiqo never embeds a Hugging Face token and does not bypass gated repositories. A gated PlannerGemma bundle is imported by the user after accepting its terms.

## Fine-tune

The defaults preserve Google's current simple SFT recipe: two epochs, learning rate `1e-5`, cosine schedule, completion-only loss, deterministic seed and bfloat16 when supported. The 12 GB development GPU requires batch 1 without gradient checkpointing at the 1,600-token Tiqo prompt length; gradient accumulation retains a useful effective batch.

```powershell
ai/plannergemma/.venv/Scripts/python -m ai.plannergemma.training.train `
  --base-model independently-platform/Tasky `
  --output ai/plannergemma/checkpoints/plannergemma-v1 `
  --batch-size 1 --gradient-accumulation 8 --max-length 1600 `
  --no-gradient-checkpointing
```

Each run writes `tiqo_training_manifest.json` before training, including checkpoint, dataset version, seed and all trainer settings. Do not claim a trained model unless the final checkpoint and a post-training held-out report exist.

## Convert for Android

Conversion uses Google's current AI Edge nightly workflow and must run in a separate Linux/Colab environment:

```bash
python -m pip install -r ai/plannergemma/requirements-conversion.txt
python -m ai.plannergemma.conversion.convert_to_litertlm \
  --checkpoint ai/plannergemma/checkpoints/plannergemma-v1/final \
  --output planner-gemma-270m-pl.litertlm
```

The default is dynamic-int8, prefill 256 and KV cache 2048. Verify the generated bundle with LiteRT-LM on an Android device before distribution; Windows training success is not evidence of a valid mobile bundle.

## Android deployment

Import the resulting file as `PlannerGemma 270M PL` in Tiqo's Model Manager. The app loads planner and chat models into separate LiteRT-LM engines, exposes tools with `automaticToolCalling = false`, validates the returned call, executes it against the shared Room repository and returns a structured result with Undo. Model weights and planner/chat data remain on device.
