# Phase 3 handoff — secure WIP and Qwen benchmark pivot

Stan na 2026-09-03.

## Stan zastany i zabezpieczenie

Repozytorium znajdowało się na gałęzi main, commit 077c52a, z dużym niecommitowanym WIP Phase 3. Znaleziono:

- generator, walidator i planner-pl-v1: 10 000 train, 1 000 validation, 720 test (11 720 rekordów),
- pięć równych stylów po 2 344 rekordy oraz validation report bez błędów,
- recurrence DAILY, WEEKLY, MONTHLY i YEARLY oraz ochronę przed duplicate/template leakage,
- trening, evaluation, konwersję LiteRT-LM i lokalne eksperymenty Tasky,
- Android manual tool calling, PlannerAgent, 10 narzędzi, osobne silniki planner/chat, Model Manager,
- zmiany repozytoriów i UI czatu oraz testy create/update/delete/complete, reminders i Undo.

Nie znaleziono wcześniejszego handoffu. Modele, checkpointy, lokalne results, .venv, cache Pythona, buildy i work były obecne lokalnie, ale poprawnie ignorowane. Skan trackowalnych plików nie wykazał tokenów, kluczy prywatnych ani sekretów.

WIP zabezpieczono i wypchnięto do origin/main:

- 23bc677 — chore: checkpoint phase 3 WIP before Qwen benchmark pivot

## Zmiana strategii

Custom fine-tuning PlannerGemma 270M jest wstrzymany, nie usunięty. Katalog ai/plannergemma zachowuje dataset, trening, evaluation, conversion i dokumentację jako experimental / paused.

Pierwszym kandydatem jest publiczny Apache-2.0 Qwen/Qwen3-0.6B w trybie non-thinking. Obecny benchmark mierzy oryginalny checkpoint Transformers; nie udaje jeszcze benchmarku konkretnego Android INT4. Mobile quantization i Android integration są decyzją po wynikach.

## Dodane tooling

- ai/benchmark/gold_manual_pl.jsonl — 280 ręcznie kontrolowanych polskich przypadków,
- ai/benchmark/build_gold_manual_pl.py — deterministyczny serializator jawnie zapisanych danych,
- ai/benchmark/run_qwen_benchmark.py — CUDA/CPU, Qwen tools, enable_thinking=False, parser, scoring i resume,
- ai/benchmark/test_qwen_benchmark.py — szybkie testy datasetu, parsera, scoringu i delete safety,
- run_qwen_benchmark.ps1 — automatyczne venv/dependencies/CUDA/cache/run,
- run_qwen_benchmark.bat — prosty wrapper dla Windows,
- ai/benchmark/README.md — instrukcja użytkownika.

Metryki obejmują tool selection, full structured accuracy, malformed output, delegate, clarification, per category, per style, delete precision, false-positive delete oraz false-positive mutation. Każdy przypadek trafia od razu do raw JSONL; resume pomija ukończone case IDs. Failure report przechowuje input, expected, actual, parse status, schema errors, category, style i raw output.

## Uruchomienie

Smoke (20 pierwszych przypadków):

    .\run_qwen_benchmark.ps1 -Limit 20

Pełny benchmark z automatycznym resume:

    .\run_qwen_benchmark.ps1

Wyniki:

- ai/benchmark/results/qwen3_0.6b/qwen3_0.6b_summary.json
- ai/benchmark/results/qwen3_0.6b/qwen3_0.6b_failures.jsonl
- ai/benchmark/results/qwen3_0.6b/qwen3_0.6b_raw_results.jsonl

## Czego nie wykonano

- nie uruchomiono pełnych 280 inferencji,
- nie uruchomiono SFT ani długiego benchmarku FunctionGemma,
- nie pobierano ani nie commitowano modelu,
- nie integrowano Qwen z Androidem i nie przebudowywano LlmEngine/Model Manager,
- nie wykonywano migracji do Android INT4.

Przechodzi 10 szybkich testów nowego benchmarku, 4 istniejące testy PlannerGemma oraz walidacja wszystkich 11 720 wcześniejszych rekordów. Pierwsza próba użycia przeniesionego ai/plannergemma/.venv nie powiodła się, bo środowisko zapamiętało starą ścieżkę interpretera; testy Pythona wykonano następnie bez modyfikowania tego venv za pomocą dostępnego interpretera. Gradle w ograniczonym środowisku próbował użyć niedozwolonego C:\.gradle, więc testów Android nie ponawiano; po checkpointcie nie zmieniano żadnego pliku Android. Nie obchodzono tego destrukcyjną zmianą lokalnych artefaktów.

## Następny krok

Użytkownik uruchamia pełny benchmark lokalnie, a następnie przekazuje summary, failures i raw results do analizy. Na tej podstawie należy ustalić progi bezpieczeństwa, zbadać szczególnie ambiguous/delete/reminder i dopiero wtedy zdecydować o Qwen zero-shot, dostrojeniu lub powrocie do PlannerGemma.
