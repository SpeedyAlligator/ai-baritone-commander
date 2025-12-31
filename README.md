# AI Baritone Commander

> Control Minecraft with natural language using local AI

Type `/ai mine 10 diamonds` and watch your character automatically navigate and mine!

![Minecraft 1.20.4](https://img.shields.io/badge/Minecraft-1.20.4-green)
![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-blue)
![Ollama](https://img.shields.io/badge/AI-Ollama-orange)

---

## ✨ Features

- **Natural Language Control** - Just type what you want in plain English
- **100% Local & Free** - Uses Ollama, runs entirely on your computer
- **Powered by Baritone** - Reliable pathfinding and automation
- **Smart Context** - AI sees your inventory, surroundings, and health
- **Safe Mode** - Prevents dangerous actions when low on health

---

## 🚀 Quick Start (Windows)

```
1. Extract this zip to C:\Projects\ai-baritone-commander
2. Double-click INSTALL.bat
3. Install Fabric API and Baritone in your mods folder
4. Start Ollama and launch Minecraft
5. Type: /ai mine 10 stone
```

📖 **Full instructions:** See [INSTALLATION_GUIDE.md](INSTALLATION_GUIDE.md)

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
/ai mine 10 iron ore
/ai go to 100 64 -200
/ai explore the area
/ai farm wheat
/ai follow Steve
/ai stop
```

---

## 🎮 Special Commands

| Command | Description |
|---------|-------------|
| `/ai stop` | Stop all actions |
| `/ai status` | Show status |
| `/ai debug` | Toggle debug mode |
| `/ai dryrun` | Preview without executing |
| `/ai help` | Show help |

---

## 🤖 Supported AI Models

| Model | Speed | Quality | Command |
|-------|-------|---------|---------|
| llama3.2 | ⚡ Fast | Good | `ollama pull llama3.2` |
| llama3.2:1b | ⚡⚡ Fastest | Basic | `ollama pull llama3.2:1b` |
| mistral | Medium | Better | `ollama pull mistral` |

---

## 📁 Files

| File | Description |
|------|-------------|
| `INSTALL.bat` | Windows auto-installer (double-click) |
| `install.ps1` | PowerShell installer script |
| `INSTALLATION_GUIDE.md` | Complete setup instructions |
| `build.gradle` | Build configuration |

---

## 🔧 Troubleshooting

**"Ollama not running"** → Start Ollama: `ollama serve`

**"Baritone not detected"** → Install [baritone-api-fabric-1.10.4.jar](https://github.com/cabaletta/baritone/releases)

**Build fails** → Run `INSTALL.bat` (downloads correct Gradle)

**Slow responses** → Use faster model: `ollama pull llama3.2:1b`

---

## 📄 License

MIT License - See [LICENSE](LICENSE)

---

Made with ❤️ for Minecraft automation
