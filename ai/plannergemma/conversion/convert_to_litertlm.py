"""Convert a trained 270M Gemma checkpoint to a dynamic-int8 LiteRT-LM bundle.

Run in Linux or Colab with requirements-conversion.txt. The API follows Google's
current FunctionGemma Mobile Actions conversion recipe and fails loudly if a
nightly package changes rather than emitting an invalid bundle.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--prefill-seq-len", type=int, default=256)
    parser.add_argument("--kv-cache-max-len", type=int, default=2048)
    args = parser.parse_args()

    import ai_edge_torch
    from ai_edge_torch.generative.examples.gemma3 import gemma3
    from ai_edge_torch.generative.utilities import converter

    model = gemma3.build_model_270m(str(args.checkpoint))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    converter.convert_to_litert(
        model,
        output_path=str(args.output),
        prefill_seq_len=args.prefill_seq_len,
        kv_cache_max_len=args.kv_cache_max_len,
        quantize="dynamic_int8",
        output_format="litertlm",
    )
    metadata = {
        "checkpoint": str(args.checkpoint),
        "output": str(args.output),
        "quantization": "dynamic_int8",
        "prefill_seq_len": args.prefill_seq_len,
        "kv_cache_max_len": args.kv_cache_max_len,
    }
    args.output.with_suffix(args.output.suffix + ".json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
