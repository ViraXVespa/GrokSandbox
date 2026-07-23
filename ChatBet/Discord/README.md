# ChatBet Discord Integration

Local Discord bot + betting hub for ChatBet.

Viewers see **active and recently closed polls** in a dedicated channel, place bets, and check balances without needing Twitch chat.

```
RuneLite ChatBet plugin  ──HTTP──►  Hub API :8766  ◄──in-process──  Discord bot
                                         │
                                         ▼
                              data/chatbet_state.json
```

## Features

| Feature | How |
|--------|-----|
| Polls channel | Live embeds for open polls + recent closed results |
| `/bet` | Place a wager on a poll |
| `/balance` | Check your token balance (ephemeral) |
| `/bets` | List active polls |
| `/leaderboard` | Top balances |
| `/poll create` | Admin: create a poll from Discord |
| `/poll resolve` | Admin: resolve a poll |
| `/poll refresh` | Admin: force-refresh channel embeds |
| RuneLite sync | Plugin pushes poll create / resolve / in-game bets to the hub |

Balances and Discord wagers live in the **hub** (persisted under `data/`). New users start with `STARTING_BALANCE` tokens (default 10,000).

---

## What you need to give me (API keys / IDs)

Fill these into `ChatBet/Discord/.env` (copy from `.env.example`):

### Required

| Env var | What it is | Where to get it |
|---------|------------|-----------------|
| **`DISCORD_BOT_TOKEN`** | Bot token | [Discord Developer Portal](https://discord.com/developers/applications) → your app → **Bot** → Reset/Copy Token |
| **`DISCORD_APPLICATION_ID`** | Application (client) ID | Developer Portal → your app → **General Information** → Application ID |
| **`DISCORD_GUILD_ID`** | Your server’s ID | Discord → User Settings → Advanced → enable **Developer Mode** → right-click server → **Copy Server ID** |
| **`DISCORD_POLLS_CHANNEL_ID`** | Channel for poll embeds | Right-click the channel → **Copy Channel ID** |

### Strongly recommended

| Env var | What it is |
|---------|------------|
| **`HUB_API_KEY`** | Shared secret so only your RuneLite client can call the hub. Any random string, e.g. `python -c "import secrets; print(secrets.token_hex(16))"`. Put the **same value** in RuneLite ChatBet config → **Discord hub API key**. |

### Optional

| Env var | What it is |
|---------|------------|
| `DISCORD_ADMIN_ROLE_IDS` | Comma-separated role IDs allowed to use `/poll *` (otherwise Manage Server / Admin) |
| `STARTING_BALANCE` | Tokens for new bettors (default `10000`) |
| `RECENT_CLOSED_LIMIT` | How many closed polls stay visible (default `8`) |
| `HUB_HOST` / `HUB_PORT` | Default `127.0.0.1` / `8766` |
| `BOT_STATUS` | Presence text |

There is **no** separate “Discord API key” beyond the **bot token**. OAuth client secret is **not** needed for this bot.

---

## One-time Discord setup

1. Create an application at https://discord.com/developers/applications  
2. **Bot** tab → Add Bot → enable it  
3. **OAuth2 → URL Generator**:
   - Scopes: `bot`, `applications.commands`
   - Bot permissions: `View Channels`, `Send Messages`, `Embed Links`, `Read Message History`, `Manage Messages` (optional, for cleanup)
4. Open the generated invite URL and add the bot to your server  
5. Create a text channel (e.g. `#chatbet`) and copy its channel ID  
6. Copy server ID, application ID, bot token into `.env`

Invite URL template (replace `APP_ID`):

```
https://discord.com/api/oauth2/authorize?client_id=APP_ID&permissions=274877975552&scope=bot%20applications.commands
```

(`274877975552` ≈ View Channel + Send Messages + Embed Links + Read History + Use App Commands)

---

## Run the service

```powershell
cd ChatBet\Discord
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
# edit .env with your token + IDs
python main.py
```

You should see:

- `Hub API → http://127.0.0.1:8766`
- `Logged in as YourBot#1234`
- Slash commands sync to your guild (instant when `DISCORD_GUILD_ID` is set)

### RuneLite side

In ChatBet plugin settings:

- **Discord hub sync**: on  
- **Discord hub URL**: `http://127.0.0.1:8766`  
- **Discord hub API key**: same as `HUB_API_KEY` in `.env`

When you create/resolve polls in RuneLite (or Ourania auto-polls), they appear in the Discord channel. Discord `/bet` uses the hub balance system.

---

## Viewer commands

```
/balance
/bet poll_id:1 option:Yes amount:500
/bet poll_id:1 option:1 amount:250
/bets
/leaderboard
```

Admins (Manage Server or roles in `DISCORD_ADMIN_ROLE_IDS`):

```
/poll create question:Will I get ETC? options:Yes,No
/poll resolve poll_id:1 option:Yes
/poll refresh
```

---

## Hub HTTP API (for tooling / RuneLite)

| Method | Path | Notes |
|--------|------|--------|
| GET | `/health` | Liveness |
| GET | `/api/v1/state` | Full snapshot |
| GET | `/api/v1/polls` | Active + closed |
| POST | `/api/v1/polls` | Create / sync poll |
| POST | `/api/v1/polls/{id}/resolve` | Resolve |
| POST | `/api/v1/bets` | Place bet |
| GET | `/api/v1/balance/{username}` | Balance |
| GET | `/api/v1/leaderboard` | Top balances |

If `HUB_API_KEY` is set, send header: `X-Api-Key: <key>`.

---

## Notes

- Run the Discord service **on the same machine** as RuneLite (localhost hub).  
- Hub state is saved to `ChatBet/Discord/data/chatbet_state.json`.  
- Discord balances are hub-owned; they are separate from any purely in-memory RuneLite balances if the hub was offline when a bet was placed. Keep the hub running during streams for a single economy.  
- Do **not** commit `.env` or bot tokens.
