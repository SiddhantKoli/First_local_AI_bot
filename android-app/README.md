# Sainik Sahayak — Android

An offline RAG assistant for Indian Army procedures, running **entirely on the
phone**: no server, no API key, no `INTERNET` permission. Same idea as the Python
app in the repo root, reduced to what a phone can do:

| | Desktop (repo root) | Android (`android-app/`) |
|---|---|---|
| Embeddings | `mxbai-embed-large` via Ollama | none — BM25 (lexical) |
| Vector store | ChromaDB on disk | in-memory inverted index in the APK |
| LLM | `llama3.2` via Ollama | Llama 3.2 3B Instruct Q4_K_M via llama.cpp (JNI) |
| Context | 5 chunks, MMR | 3 chunks, top-k BM25 |
| Network | loopback only | none at all |

Target device: vivo X300 (Dimensity 9500, Android 16, arm64-v8a). `minSdk` 26,
`compileSdk`/`targetSdk` 36, single ABI `arm64-v8a`.

## How it works

```
question
   │  (only for a follow-up that refers to earlier turns)
   ▼
condense follow-up ───────────► on-device LLM rewrites "what about at night?"
   │                            into a standalone query
   ▼
BM25 over 236 chunks ─────────► top 3 + score + coverage
   │
   ├── score < 6.5 or coverage < 0.40 ──► canned refusal, no LLM call
   ▼
prompt (system rules + numbered context + last 2 turns)
   │
   ▼
llama.cpp (JNI) ──────────────► tokens streamed to the UI
   │
   ▼
answer + "Sources" list
```

The system prompt forbids outside knowledge and keeps answers to three short
sentences. Sources are attached from retrieval results and appear below the
finished answer; the model is not asked to repeat them in its response.

### Relevance gate

BM25 scores are unbounded, so a single cut-off misfires: *"best cricket player in
the world"* scores 7.3 (the word "best" is in a heading) while *"what is a
Subedar"* scores 7.2. The app therefore requires **both**:

* `score >= 6.5`, and
* `coverage >= 0.40` — the share of the query's IDF mass the best chunk explains.

Both numbers were tuned on the real asset and are asserted in
`Bm25RetrieverTest`. Coverage is what rejects questions that only share a common
word with the knowledge base.

### Faster answers and progress

Standalone questions go straight to retrieval instead of spending time on an
extra model-generated rewrite, and their prompts omit old conversation turns.
Only follow-ups that refer to earlier turns are condensed and include chat
history. The app retrieves three context chunks and caps generated answers at
96 tokens to reduce prompt processing and generation time. While the model is
evaluating the prompt, the assistant bubble shows a spinner and “Writing answer”.

### Follow-up condensation

When the question depends on earlier turns, it is rewritten by the same model
(greedy, ≤ 96 tokens) into a standalone search query. If that generation fails
for any reason the raw question is used, so the feature can only help.

## Build

Prerequisites: JDK 17, Android SDK 36, NDK `29.0.13113456`, CMake `3.31.6`
(~10 GB of disk for the toolchain). Android Studio can install all of these, or:

```bash
# cmdline-tools + sdkmanager
sdkmanager "platforms;android-36" "build-tools;36.0.0" \
           "ndk;29.0.13113456" "cmake;3.31.6"
```

```bash
cd android-app
./scripts/fetch-llama-cpp.sh      # clones llama.cpp b11799 into third_party/
./gradlew :app:assembleDebug      # ~5-15 min the first time (compiles llama.cpp)
./gradlew :app:testDebugUnitTest  # BM25 tests against the real asset
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`third_party/llama.cpp` is fetched, not vendored, and git-ignored. CI does the
same thing: [`.github/workflows/build-android.yml`](../.github/workflows/build-android.yml)
builds the APK on every push to `android-app` and uploads it as an artifact.

### llama.cpp binding

The app talks to llama.cpp through a small JNI bridge,
[`app/src/main/cpp/llama_jni.cpp`](app/src/main/cpp/llama_jni.cpp), against the C
API of the pinned tag (`b11799`). It uses
`llama_model_load_from_file` → `llama_init_from_model` →
`llama_chat_apply_template` → `llama_batch_get_one` + `llama_decode` +
`llama_sampler_chain_*`, with `llama_memory_clear` between questions. Every symbol
was checked against `include/llama.h` from that tag; nothing is re-declared or
guessed.

Why not a third-party AAR? The maintained-looking ones either pin an older
llama.cpp, put token streaming behind a paid tier, or are single-author projects.
Why not the official `examples/llama.android` module? Its Kotlin layer is a large
app-and-UI-oriented sample (state machine, GGUF metadata reader, hardware variant
detection); wrapping the C API directly is ~350 lines and reviewable. See the
comments in `CMakeLists.txt` for the options that are turned off (tools, tests,
server, Vulkan, KleidiAI) to keep the APK lean and the configure step offline.

## Getting the model

The APK does **not** ship weights. Any Llama 3.2 3B Instruct Q4 GGUF works; the
one this app was written for:

* Hugging Face: `bartowski/Llama-3.2-3B-Instruct-GGUF`,
  file **`Llama-3.2-3B-Instruct-Q4_K_M.gguf`** (~2.0 GB).

```bash
# The Meta repos are gated: accept the licence on the model page first.
pip install -U "huggingface_hub[cli]"
huggingface-cli download bartowski/Llama-3.2-3B-Instruct-GGUF \
  Llama-3.2-3B-Instruct-Q4_K_M.gguf --local-dir .
```

Copy it to the phone (USB file transfer into `Download/`, or `adb push`):

```bash
adb push Llama-3.2-3B-Instruct-Q4_K_M.gguf /sdcard/Download/
```

On first launch tap **Choose .gguf file**, pick the file in the system picker, and
wait while it is copied into app-private storage (`filesDir/models/model.gguf`).
The copy is one-time; afterwards the app starts straight into chat and works in
airplane mode. A `.gguf` with no chat template (a base model) is rejected with a
clear message — use an **Instruct** build.

## Regenerating the knowledge base

`app/src/main/assets/knowledge.json` is committed, so a fresh clone builds without
Python. When the CSVs change:

```bash
python android-app/tools/build_knowledge.py     # merge + chunk + write the asset
python android-app/tools/test_build_knowledge.py
```

The script merges every `data/*.csv`, normalises headers (the v1/v2 files use
`Category,Topic,Content`, the newer ones `category,topic,content,source`), fills a
missing `source` with `"<file> row <id>"`, drops exact duplicates, and splits rows
longer than 1200 characters into ~1000-character chunks with a 150-character
overlap. Current output: 236 chunks, 168 KB.

## Project layout

```
android-app/
├── app/src/main/
│   ├── assets/knowledge.json          # generated, committed
│   ├── cpp/llama_jni.cpp              # JNI bridge over llama.cpp C API
│   ├── cpp/CMakeLists.txt
│   ├── java/com/sainiksahayak/app/
│   │   ├── MainActivity.kt            # SAF picker + Compose host
│   │   ├── chat/ChatScreen.kt         # UI: bubbles, sources, stop, new chat
│   │   ├── chat/ChatViewModel.kt      # condense → retrieve → gate → stream
│   │   ├── chat/Prompting.kt          # system prompts + context block
│   │   ├── knowledge/Bm25Retriever.kt # pure Kotlin, unit-tested
│   │   ├── knowledge/Retriever.kt     # interface (swap in embeddings later)
│   │   └── llm/{LlamaNative,LlmEngine,ModelStore}.kt
│   └── AndroidManifest.xml            # no INTERNET permission
├── scripts/fetch-llama-cpp.sh
├── tools/build_knowledge.py           # CSV -> knowledge.json
├── tools/test_build_knowledge.py
└── gradle/libs.versions.toml          # pinned versions
```

## Adding embeddings later

Implement [`Retriever`](app/src/main/java/com/sainiksahayak/app/knowledge/Retriever.kt)
and swap the single construction site in `ChatViewModel` — nothing else knows BM25
exists. An embedding model would need its own GGUF plus `llama_get_embeddings`,
roughly +400 MB and a slower first launch; BM25 costs 0 MB and ~0 ms, which is why
it is the default.

## Known limitations

* **CPU only.** No Vulkan backend is compiled, so a 3B model can take tens of
  seconds per answer on some phones. The prompt and output length are bounded,
  but these changes cannot make CPU token generation as fast as GPU inference.
* **Context is 2048 tokens** (~235 MB of KV cache). The relevance gate keeps the
  prompt around 1200 tokens; a very long question can hit the limit, which the UI
  reports instead of truncating silently.
* **BM25 is lexical.** It matches words, not meaning: "heart attack" will not find
  a chunk that only says "myocardial infarction". Embeddings would fix that;
  stemming or a synonym table would help cheaply.
* **No persistence of chats.** Model choice survives restarts; conversations do
  not.
* **Condensation costs one extra generation** (≈1-2 s) on every follow-up.

## Verification status

Checked here:

* `tools/test_build_knowledge.py` — 13 tests pass; the real CSVs produce 236
  chunks, 172 KB, longest chunk 776 characters.
* `./gradlew :app:testDebugUnitTest` — 9 BM25 tests pass in 0.3 s, including the
  in-scope/out-of-scope gate checks against the committed asset.
* `./gradlew :app:assembleDebug` — builds an 18 MB APK. Verified inside it:
  `lib/arm64-v8a/libllamajni.so`, `libllama.so`, `libggml*.so`,
  `assets/knowledge.json` (172 KB), one ABI only, and **no `INTERNET`
  permission** in the merged manifest.

Not checked:

* **The APK has never been installed on a phone.** Token throughput, memory use,
  thermals, the SAF picker with a real 2 GB GGUF and the native load path are
  all unmeasured. Treat any speed claim as an expectation.
* No instrumented/UI test; the Compose layer is only compile-checked.

If Gradle fails with "Cannot snapshot …/app/build/intermediates/…: not a
regular file" and the repo lives in a OneDrive/iCloud folder, point the build
elsewhere with an init script (`layout.buildDirectory.set(...)`) — it is the sync
client, not the project.
