# RepoLens AI

RepoLens AI is a lightweight IntelliJ plugin designed to help developers understand codebases faster. Instead of just explaining a snippet in isolation, it uses the IDE's PSI tree to pull in relevant repository context—like imports, method signatures, and sibling declarations—to provide a more accurate analysis of what the code actually does, its dependencies, and potential side effects.

## Quick Start

### 1. Prerequisites
- **IntelliJ IDEA** (2024.3+)
- **JDK 17**
- An OpenAI-compatible API key (OpenAI, Ollama, etc.)

### 2. Setup
Clone the repo and open it in IntelliJ:
```bash
git clone https://github.com/your-username/repolens-ai.git
```

Wait for Gradle to sync, then run the plugin using the provided task:
```bash
./gradlew runIde
```
This will launch a new instance of IntelliJ with the plugin pre-installed.

### 3. Configuration
Once the sandbox IDE is open, go to **Settings → Tools → RepoLens AI** to enter your API key and configure your model settings (e.g., `gpt-4o-mini`).

## Usage
Select any block of code or hover over a method and right-click to select **"Explain with RepoLens AI"**. You can also use the shortcut `Ctrl+Shift+L`. The results will appear in a tool window on the right, broken down into logic, dependencies, and side effects.

## Tech Stack
- **Kotlin** 2.1
- **IntelliJ Platform SDK**
- **OkHttp** for API calls
- **kotlinx.serialization** for data handling

## License
MIT
