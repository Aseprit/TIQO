# Tiqo local Qwen planner benchmark

Ten katalog odpowiada na jedno pytanie: czy publiczny model Qwen/Qwen3-0.6B potrafi bez fine-tuningu bezpiecznie pełnić rolę polskiego planera/routera Tiqo?

Benchmark działa lokalnie i nie używa Codexa ani zewnętrznego LLM do generowania lub oceniania odpowiedzi. Oficjalny checkpoint Qwen ma licencję Apache 2.0, wspiera Hermes-style tool calling, a thinking jest wyłączany twardym parametrem enable_thinking=False w chat template. Źródła: [Qwen3-0.6B model card](https://huggingface.co/Qwen/Qwen3-0.6B) i [Qwen function calling](https://qwen.readthedocs.io/en/stable/framework/function_call.html).

## Pierwszy run (smoke)

W PowerShellu, z katalogu głównego repozytorium:

    .\run_qwen_benchmark.ps1 -Limit 20

Skrypt:

1. tworzy izolowane ai/benchmark/.venv, jeśli go nie ma,
2. instaluje zależności tylko przy pierwszym uruchomieniu lub zmianie requirements,
3. wybiera PyTorch CUDA 12.6, gdy wykryje sterownik NVIDIA; w przeciwnym razie instaluje wariant CPU,
4. pobiera publiczny model przy pierwszym użyciu,
5. zachowuje cache Hugging Face do kolejnych uruchomień,
6. wykonuje pierwszych 20 przypadków i zapisuje każdy wynik natychmiast.

Smoke można wymusić na CPU:

    .\run_qwen_benchmark.ps1 -Limit 20 -Device cpu

## Pełny benchmark

    .\run_qwen_benchmark.ps1

Resume jest domyślnie włączone. Jeśli smoke zakończył przypadki 1–20, pełny run pominie je i zacznie od 21. Po przerwaniu przy 187/280 następny run zacznie od pierwszego brakującego ID. Parametr -NoResume jawnie rozpoczyna nowy run i zastępuje pliki bieżącego katalogu wynikowego.

Model nie jest pobierany ponownie, jeśli znajduje się już w cache Hugging Face. Tryb bez sieci po pierwszym pobraniu:

    .\run_qwen_benchmark.ps1 -LocalFilesOnly

Można też dwukrotnie kliknąć run_qwen_benchmark.bat.

## Gold dataset

gold_manual_pl.jsonl zawiera 280 ręcznie kontrolowanych rekordów:

| Kategoria | Liczba |
|---|---:|
| create | 45 |
| recurrence | 35 |
| update | 30 |
| list | 20 |
| read | 10 |
| search | 25 |
| completion | 20 |
| delete | 15 |
| reminder | 20 |
| ambiguous | 25 |
| delegate | 25 |
| statistics | 10 |

Każdy z pięciu stylów — clean, colloquial, no-diacritics, typo, stt — ma dokładnie 56 przypadków. Kontekst jest stały: 2026-09-03 09:00, Europe/Warsaw, pl-PL. Każdy rekord ma ID, input, oczekiwane narzędzie, kontrolowane argumenty, kategorię, styl i opcjonalne candidate items/history.

build_gold_manual_pl.py jest wyłącznie serializatorem jawnie zapisanych wariantów i etykiet. Nie wywołuje modelu, nie parafrazuje i nie losuje danych. Sprawdzenie zgodności:

    py -3.12 -m ai.benchmark.build_gold_manual_pl --check

## Wyniki

Domyślny katalog:

    ai/benchmark/results/qwen3_0.6b/

Pliki do późniejszej analizy:

- qwen3_0.6b_summary.json — metryki ogólne, per category, per style i delete safety,
- qwen3_0.6b_failures.jsonl — wszystkie błędy z expected/actual/parse status/raw output,
- qwen3_0.6b_raw_results.jsonl — komplet odpowiedzi i checkpoint resume.

Raport obejmuje tool selection, full structured accuracy, malformed output, delegate, clarification, kategorie, style, precision dla delete_item, false-positive destructive calls oraz false-positive mutating calls.

## Bez launchera

    ai\benchmark\.venv\Scripts\python.exe -m ai.benchmark.run_qwen_benchmark --model Qwen/Qwen3-0.6B --device cuda --limit 20

Najważniejsze opcje CLI: --model, --dataset, --device auto|cuda|cpu, --dtype, --limit, --resume/--no-resume, --output, --max-new-tokens i --local-files-only.

Ten desktopowy baseline używa oryginalnego checkpointu Transformers (BF16/FP16 na CUDA, FP32 na CPU). Nie jest to jeszcze pomiar konkretnej konwersji INT4 na Androidzie. Jeśli jakość baseline'u przejdzie próg, następnym krokiem jest wybranie zgodnego mobile INT4 i ponowienie gold benchmarku na dokładnym artefakcie deploymentowym przed integracją aplikacji.
