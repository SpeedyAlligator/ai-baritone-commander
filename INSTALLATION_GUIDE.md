# AI Baritone Commander - Installation Guide

Control Minecraft with natural language using local AI. Type `/ai mine 10 diamonds` and watch it happen!

---

## Table of Contents

1. [Quick Install (Windows)](#quick-install-windows)
2. [Requirements](#requirements)
3. [Step 1: Install Minecraft & Fabric](#step-1-install-minecraft--fabric)
4. [Step 2: Install Ollama (Local AI)](#step-2-install-ollama-local-ai)
5. [Step 3: Build & Install the Mod](#step-3-build--install-the-mod)
6. [Step 4: Install Other Required Mods](#step-4-install-other-required-mods)
7. [Step 5: First Launch](#step-5-first-launch)
8. [Usage Guide](#usage-guide)
9. [Configuration](#configuration)
10. [Troubleshooting](#troubleshooting)

---

## Quick Install (Windows)

If you just want to get started quickly:

```
1. Extract this zip to C:\Projects\ai-baritone-commander
2. Double-click INSTALL.bat
3. Follow the prompts
```

For detailed instructions, read on.

---

## Requirements

| Software | Version | Required | Download Link |
|----------|---------|----------|---------------|
| Minecraft Java | 1.20.4 | ✅ Yes | [minecraft.net](https://minecraft.net) |
| Java | 17-21 | ✅ Yes | [adoptium.net](https://adoptium.net) |
| Fabric Loader | 0.15+ | ✅ Yes | [fabricmc.net](https://fabricmc.net/use/installer/) |
| Fabric API | 0.97.2+ | ✅ Yes | [modrinth.com](https://modrinth.com/mod/fabric-api) |
| Baritone | 1.10.4 | ✅ Yes | [github.com](https://github.com/cabaletta/baritone/releases) |
| Ollama | Latest | ✅ Yes | [ollama.ai](https://ollama.ai) |

**System Requirements:**
- 8GB RAM minimum (16GB recommended)
- 4GB free disk space
- Internet connection (for initial setup)

---

## Step 1: Install Minecraft & Fabric

### 1.1 Install Java

Check if Java is installed:
```cmd
java -version
```

If not installed or version is below 17, download from [Adoptium](https://adoptium.net/temurin/releases/?version=21).

### 1.2 Install Fabric Loader

1. Download the [Fabric Installer](https://fabricmc.net/use/installer/)
2. Run the installer JAR file
3. Configure:
   - Client: ✅ Selected
   - Minecraft Version: **1.20.4**
   - Loader Version: **0.15.7** or higher
4. Click **Install**

### 1.3 Verify Fabric Installation

1. Open Minecraft Launcher
2. Look for a profile called **fabric-loader-1.20.4**
3. Select it and click **Play** once to generate folders
4. Close Minecraft

---

## Step 2: Install Ollama (Local AI)

Ollama runs AI models locally on your computer - free and private.

### 2.1 Download & Install

**Windows:**
1. Go to [ollama.ai/download](https://ollama.ai/download)
2. Download the Windows installer
3. Run the installer
4. Ollama will start automatically (check system tray)

**macOS/Linux:**
```bash
curl -fsSL https://ollama.ai/install.sh | sh
```

### 2.2 Download an AI Model

Open a terminal/command prompt:

```bash
# Recommended - good balance of speed and quality
ollama pull llama3.2

# For slower computers (faster, less capable)
ollama pull llama3.2:1b

# For better quality (needs more RAM, slower)
ollama pull mistral
```

### 2.3 Start Ollama Server

**Windows:** Ollama auto-starts. Check your system tray for the llama icon.

**macOS/Linux:**
```bash
ollama serve
```

### 2.4 Verify Ollama is Running

Open a browser and go to: http://localhost:11434

You should see: `Ollama is running`

Or in terminal:
```bash
curl http://localhost:11434/api/tags
```

---

## Step 3: Build & Install the Mod

### Option A: Automatic Install (Recommended for Windows)

1. Extract this zip to a simple path like `C:\Projects\ai-baritone-commander`
   
   ⚠️ **Avoid paths with spaces!** Don't use Desktop or Downloads.

2. Double-click **INSTALL.bat**

3. Wait for the build to complete (2-5 minutes first time)

4. The mod JAR will be automatically copied to your mods folder

### Option B: Manual Install

1. Extract the zip to `C:\Projects\ai-baritone-commander`

2. Open PowerShell in that folder:
   ```powershell
   cd C:\Projects\ai-baritone-commander
   .\install.ps1
   ```

3. If PowerShell is blocked, run:
   ```powershell
   powershell -ExecutionPolicy Bypass -File install.ps1
   ```

### Option C: Manual Build (Advanced)

1. Download gradle-wrapper.jar from [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod/raw/1.20/gradle/wrapper/gradle-wrapper.jar)

2. Place it in `gradle/wrapper/gradle-wrapper.jar`

3. Open Command Prompt:
   ```cmd
   cd C:\Projects\ai-baritone-commander
   gradlew.bat build
   ```

4. Copy `build\libs\ai-baritone-commander-1.0.0.jar` to your mods folder

---

## Step 4: Install Other Required Mods

Your mods folder location:
- **Windows:** `%APPDATA%\.minecraft\mods`
- **macOS:** `~/Library/Application Support/minecraft/mods`
- **Linux:** `~/.minecraft/mods`

Download and place these files in your mods folder:

### 4.1 Fabric API (Required)

Download: [Fabric API 0.97.2+1.20.4](https://modrinth.com/mod/fabric-api/version/0.97.2+1.20.4)

Direct link: Click "Download" on the Modrinth page

### 4.2 Baritone (Required)

Download: [Baritone API Fabric 1.10.4](https://github.com/cabaletta/baritone/releases/download/v1.10.4/baritone-api-fabric-1.10.4.jar)

⚠️ Make sure to download the **Fabric** version, not Forge!

### 4.3 Final Mods Folder

Your mods folder should contain:
```
mods/
├── fabric-api-0.97.2+1.20.4.jar
├── baritone-api-fabric-1.10.4.jar
└── ai-baritone-commander-1.0.0.jar
```

---

## Step 5: First Launch

### 5.1 Pre-flight Checklist

- [ ] Ollama is running (check system tray or http://localhost:11434)
- [ ] AI model is downloaded (`ollama list` shows llama3.2)
- [ ] All three mod JARs are in mods folder
- [ ] Fabric profile exists in Minecraft Launcher

### 5.2 Launch Minecraft

1. Open Minecraft Launcher
2. Select **fabric-loader-1.20.4** profile
3. Click **Play**
4. Create a new world or load an existing one

### 5.3 Test the Mod

In the game chat, type:
```
/ai status
```

You should see:
```
[AI] === Status ===
[AI] State: IDLE
[AI] Baritone: OK
[AI] Ollama: OK
[AI] Model: llama3.2
```

### 5.4 Try Your First Command

```
/ai mine 5 stone
```

The AI will respond with a plan and Baritone will start mining!

---

## Usage Guide

### Basic Syntax

```
/ai <instruction in plain English>
```

or

```
!ai <instruction>
```

### Example Commands

#### Mining
```
/ai mine 10 iron ore
/ai mine 64 oak logs
/ai mine diamonds until I have 20
/ai mine coal near spawn
```

#### Navigation
```
/ai go to 100 64 -200
/ai go to the nearest village
/ai find a stronghold
/ai take me home
```

#### Exploration
```
/ai explore the area
/ai explore for 1000 blocks
/ai find a cave
/ai look for a desert temple
```

#### Farming
```
/ai farm wheat
/ai harvest all nearby crops
/ai replant the farm
```

#### Following
```
/ai follow Steve
/ai follow the nearest cow
/ai follow any villager
```

#### Stopping
```
/ai stop
/ai cancel
```

### Special Commands

| Command | Description |
|---------|-------------|
| `/ai stop` | Stop all current actions |
| `/ai status` | Show mod status |
| `/ai help` | Show help message |
| `/ai debug` | Toggle debug messages |
| `/ai dryrun` | Toggle dry run mode (preview without executing) |
| `/ai safe` | Toggle safe mode |
| `/ai reload` | Reload configuration |

---

## Configuration

The config file is created after first launch:
- **Windows:** `%APPDATA%\.minecraft\config\aicommander.json`
- **macOS/Linux:** `~/.minecraft/config/aicommander.json`

### Default Settings

```json
{
  "ollamaHost": "127.0.0.1",
  "ollamaPort": 11434,
  "ollamaModel": "llama3.2",
  "ollamaTimeoutSeconds": 60,
  "ollamaMaxRetries": 2,
  "commandPrefix": "/ai",
  "altCommandPrefix": "!ai",
  "useAltPrefix": true,
  "safeMode": true,
  "maxStepsPerGoal": 1000,
  "maxRetriesPerAction": 3,
  "queueMode": false,
  "chatRateLimitMs": 2000,
  "showDebugMessages": false,
  "dryRunMode": false,
  "nearbyBlockScanRadius": 32,
  "nearbyEntityScanRadius": 64
}
```

### Common Customizations

**Change AI model:**
```json
{
  "ollamaModel": "mistral"
}
```

**Use remote Ollama server:**
```json
{
  "ollamaHost": "192.168.1.100",
  "ollamaPort": 11434
}
```

**Increase timeout for slow models:**
```json
{
  "ollamaTimeoutSeconds": 120
}
```

**Change command prefix:**
```json
{
  "commandPrefix": ".ai"
}
```

After editing, use `/ai reload` in-game or restart Minecraft.

---

## Troubleshooting

### "Ollama not running!"

**Cause:** Ollama server isn't started.

**Fix:**
1. Check system tray for Ollama icon (Windows)
2. Or start manually: `ollama serve`
3. Verify: http://localhost:11434 should say "Ollama is running"

### "Baritone not detected"

**Cause:** Baritone mod missing or wrong version.

**Fix:**
1. Download [baritone-api-fabric-1.10.4.jar](https://github.com/cabaletta/baritone/releases/download/v1.10.4/baritone-api-fabric-1.10.4.jar)
2. Make sure it's the **Fabric** version (not Forge)
3. Place in mods folder
4. Restart Minecraft

### Build fails with "SelfResolvingDependency" error

**Cause:** Wrong Gradle version (9.x instead of 8.5).

**Fix:**
1. Delete the `gradle/wrapper` folder
2. Run `INSTALL.bat` again
3. Or manually download gradle-wrapper.jar from Fabric Example Mod

### Very slow responses

**Causes & Fixes:**
1. **First request is slow:** Normal - model loads into memory
2. **Model too large:** Use `llama3.2:1b` instead
3. **Timeout too short:** Increase `ollamaTimeoutSeconds` to 120

### Commands not recognized

**Fix:**
1. Try both `/ai` and `!ai`
2. Check if another mod uses the same prefix
3. Change prefix in config

### "No model found" error

**Fix:**
```bash
ollama pull llama3.2
ollama list  # verify it's downloaded
```

### Actions don't execute

**Debug steps:**
1. Enable debug mode: `/ai debug`
2. Try dry run: `/ai dryrun` then `/ai mine 5 stone`
3. Check the JSON output in chat
4. Check `.minecraft/logs/latest.log` for errors

### Game crashes on startup

**Fix:**
1. Verify Minecraft version is exactly 1.20.4
2. Verify Fabric Loader is 0.15.7+
3. Verify all mod versions match
4. Try removing other mods temporarily

---

## Getting Help

1. Check this guide's Troubleshooting section
2. Enable debug mode: `/ai debug`
3. Check logs: `.minecraft/logs/latest.log`
4. Open an issue on GitHub

---

## Tips for Best Results

1. **Be specific:** "mine 10 iron ore" works better than "get some iron"
2. **Use coordinates:** "go to 100 64 -200" is more reliable than "go home"
3. **Start simple:** Test with basic commands before complex ones
4. **Check status first:** Use `/ai status` to verify everything is working
5. **Use dry run:** Test commands with `/ai dryrun` enabled first

---

## Quick Reference Card

```
BASIC COMMANDS
/ai mine <count> <block>     Mine blocks
/ai go to <x> <y> <z>        Navigate to coordinates
/ai explore                   Explore the area
/ai farm                      Farm nearby crops
/ai follow <name>            Follow player/entity
/ai stop                      Stop all actions

UTILITY COMMANDS
/ai status                    Check mod status
/ai help                      Show help
/ai debug                     Toggle debug mode
/ai dryrun                    Toggle dry run mode
/ai safe                      Toggle safe mode
/ai reload                    Reload config
```

---

Enjoy your AI-powered Minecraft experience! 🎮🤖
