# AI Baritone Commander v2

> Control Minecraft with natural language using local AI

Type `/ai do mine 10 diamonds` and watch your character automatically navigate and mine!

![Minecraft 1.20.4](https://img.shields.io/badge/Minecraft-1.20.4-green)
![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-blue)
![Ollama](https://img.shields.io/badge/AI-Ollama-orange)

---

## ✨ Features

- **Natural Language Control** - Just type what you want in plain English
- **100% Local & Free** - Uses Ollama, runs entirely on your computer
- **Powered by Baritone** - Reliable pathfinding and automation
- **Two-Stage Planning** - Fast router + smart planner for optimal speed
- **Plan Caching** - Avoids redundant LLM calls for repeated commands
- **Model Presets** - Choose between fast, balanced, and thinking modes
- **Safe Mode** - Prevents dangerous actions when low on health

---

## 🚀 Quick Installation

### Option 1: Download Release (Recommended)

1. Download the latest `.jar` file from [Releases](https://github.com/yourusername/ai-baritone-commander-v2/releases)
2. Install prerequisites:
   - [Fabric API](https://modrinth.com/mod/fabric-api) (for Minecraft 1.20.4)
   - [Baritone](https://github.com/cabaletta/baritone/releases) (baritone-api-fabric-1.10.4.jar)
   - [Ollama](https://ollama.ai)
3. Place all `.jar` files in your `.minecraft/mods` folder
4. Start Ollama: `ollama serve`
5. Download recommended models: `/ai download`
6. Launch Minecraft and try: `/ai do mine 10 stone`

### Option 2: Build from Source

```bash
git clone https://github.com/yourusername/ai-baritone-commander-v2.git
cd ai-baritone-commander-v2
./gradlew build
```

The compiled mod will be in `build/libs/aicommander-*.jar`

---

## 📋 Requirements

- Minecraft 1.20.4
- Java 17-21
- Fabric Loader 0.15+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Baritone](https://github.com/cabaletta/baritone/releases)
- [Ollama](https://ollama.ai)

---

## 💬 Example Commands

```
/ai do mine 10 iron ore
/ai do go to 100 64 -200
/ai do explore the area
/ai do farm wheat
/ai do follow Steve
/ai stop
```

---

## 🎮 Commands

### Core Commands
| Command | Description |
|---------|-------------|
| `/ai do <instruction>` | Execute AI command |
| `/ai stop` | Stop all actions and clear queue |
| `/ai pause` | Pause execution |
| `/ai resume` | Resume execution |
| `/ai status` | Show system status |
| `/ai help` | Show help |

### Model Management
| Command | Description |
|---------|-------------|
| `/ai download` | Show recommended models |
| `/ai download qwen` | Download qwen2.5:7b |
| `/ai download phi` | Download phi3.5:mini |
| `/ai download llama` | Download llama3.2:3b |
| `/ai download mistral` | Download mistral-nemo:12b |
| `/ai models` | List installed models |
| `/ai set <preset>` | Set preset (fast/balanced/thinking) |
| `/ai modelA <name>` | Override Stage A model |
| `/ai modelB <name>` | Override Stage B model |

### Utility Commands
| Command | Description |
|---------|-------------|
| `/ai cache clear` | Clear plan cache |
| `/ai cache stats` | Show cache statistics |
| `/ai debug` | Toggle debug mode |
| `/ai dryrun` | Toggle dry run (preview only) |
| `/ai safe` | Toggle safe mode |
| `/ai reload` | Reload config |

---

## 🤖 Recommended AI Models

| Rank | Model | Use Case | Command |
|------|-------|----------|---------|
| 🥇 #1 | qwen2.5:7b | Best overall planner | `/ai download qwen` |
| 🥈 #2 | phi3.5:mini | Best fast router | `/ai download phi` |
| 🥉 #3 | llama3.2:3b | Best simple all-round | `/ai download llama` |
| 🏅 #4 | mistral-nemo:12b | Best thinking mode | `/ai download mistral` |

### Model Presets

- **Fast** - phi3.5:mini → qwen2.5:7b (fastest responses)
- **Balanced** - llama3.2:3b → qwen2.5:7b (default, best overall)
- **Thinking** - llama3.2:3b → mistral-nemo:12b (smartest, slower)

Use `/ai set fast`, `/ai set balanced`, or `/ai set thinking` to switch.

---

## 🔧 Configuration

Config file location: `.minecraft/config/aicommander.json`

Key settings:
- `activePreset` - Model preset (fast/balanced/thinking)
- `safeMode` - Enable safety checks (default: true)
- `maxActionsPerPlan` - Max actions per plan (default: 8)
- `planCacheTTLSeconds` - Cache time-to-live (default: 45)

---

## 🔧 Troubleshooting

**"Ollama not running"** → Start Ollama: `ollama serve`

**"Baritone not detected"** → Install [baritone-api-fabric-1.10.4.jar](https://github.com/cabaletta/baritone/releases)

**"Model not installed"** → Use `/ai download` to see and install recommended models

**Slow responses** → Use fast preset: `/ai set fast`

**Messages not showing in chat** → Messages are queued - wait a moment for them to appear

---

## 🏗️ Architecture

- **CommandRouter** - Fast-path routing for common commands (no LLM)
- **PlanCache** - LRU cache with 45s TTL (max 20 entries)
- **TwoStagePlanner** - Stage A (fast intent parsing) → Stage B (full planning)
- **MinimalWorldStateCollector** - Compact world state (~500 tokens)
- **ActionExecutor** - Queue-based execution with Baritone integration

---

## 📄 License

MIT License - See [LICENSE](LICENSE)

---

Made with ❤️ for Minecraft automation
