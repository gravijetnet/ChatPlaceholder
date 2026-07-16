# ChatPlaceholder

A small "core" plugin for a Minecraft **1.8.8** server (CarbonSpigot, Java 21) that acts as a
central placeholder switch. It uses the official **MBedwars** API to detect whether a player is
currently in a running Bedwars round or in the lobby, and exposes clean `%server_...%`
placeholders through **PlaceholderAPI** so that **Phoenix chat**, the **TAB** plugin,
scoreboards, nametags, etc. all stay in sync — you only configure the switch in one place.

```
Lobby:    Owner | gravijet: Hallo
Bedwars:  [R] gravijet: Hallo
```

## How it works

* **Detection** is done exclusively through the MBedwars API (never through other plugins'
  placeholders). A player counts as `BEDWARS` only when **all** of the following are true:
  1. an MBedwars arena player object exists (`GameAPI.get().getArenaByPlayer` is not `null`),
  2. the arena status is `RUNNING`,
  3. the player has a team (`Arena.getPlayerTeam` is not `null`).

  Otherwise the player is `LOBBY` (this also covers spectators and the pre-game waiting lobby).
* **Lobby prefix** is taken from Phoenix by resolving its placeholder (default
  `%phoenix_prefix%`, configurable).
* **Bedwars prefix** is built from the MBedwars team colour + initials.
* MBedwars events (`PlayerJoinArenaEvent`, `PlayerQuitArenaEvent`, `ArenaStatusChangeEvent`)
  trigger an instant TAB refresh so transitions are immediate. The placeholders themselves are
  always computed live, so this is only a responsiveness optimisation.

## Placeholders (expansion identifier: `server`)

| Placeholder             | Lobby                         | Bedwars                        |
|-------------------------|-------------------------------|--------------------------------|
| `%server_mode%`         | `LOBBY`                       | `BEDWARS`                      |
| `%server_chat_prefix%`  | Phoenix prefix, e.g. `&6Owner &8\| ` | team prefix, e.g. `&c[R] ` |
| `%server_tab_prefix%`   | Phoenix prefix                | team prefix, e.g. `&c[R] `     |
| `%server_team%`         | `NONE`                        | `RED` / `BLUE` / `GREEN` / …   |
| `%server_team_color%`   | *(empty)*                     | `&c` / `&9` / `&a` / …         |

`chat_prefix` and `tab_prefix` currently return the same value but are separate placeholders so
you can differentiate chat vs. TAB later without touching Phoenix or TAB again.

## Requirements

| Plugin          | Needed for                              | Hard dependency? |
|-----------------|-----------------------------------------|------------------|
| PlaceholderAPI  | registering / serving the placeholders  | **yes**          |
| MBedwars        | Bedwars detection (else everyone `LOBBY`)| soft             |
| Phoenix         | lobby prefix value                      | soft             |
| TAB             | instant refresh on transitions          | soft             |

## Usage

**Phoenix chat** (`plugins/Phoenix/.../config.yml`), one format for both states:

```yaml
chat-format: "%server_chat_prefix%<player>&7: %pxcosmetics_player_chat_color%<message>"
```

**TAB** (`plugins/TAB/config.yml`):

```yaml
_OTHER_:
  tabprefix: "%server_tab_prefix%"
  tagprefix: "%server_tab_prefix%"
```

Test in-game with:

```
/papi parse me %server_mode%
/papi parse me %server_chat_prefix%
/papi parse me %server_team%
```

> After changing TAB/PlaceholderAPI setup, do a full server restart (not `/reload`) so the
> placeholders register cleanly.

## Configuration (`config.yml`)

```yaml
mode:
  lobby: "LOBBY"
  bedwars: "BEDWARS"
team:
  none: "NONE"
bedwars:
  # Tokens: {color} {initials} {team}
  prefix-format: "{color}[{initials}] "
lobby:
  phoenix-prefix-placeholder: "%phoenix_prefix%"
  phoenix-prefix-fallback: ""
```

`/chatplaceholder reload` (alias `/cph`, permission `chatplaceholder.admin`) reloads it.

## Building

Requires JDK 21 and Maven.

```bash
mvn -B package
```

The jar is produced at `target/ChatPlaceholder-1.0.0.jar`. All dependencies are
`provided` (supplied by the server at runtime), so the jar is not shaded.

### CI

`.github/workflows/build.yml` builds on every push to `main` and on manual dispatch, then
publishes a per-run archive release (`build-<n>`) and updates the rolling `latest` release.

## Notes / design decisions

* The build resolves Spigot from the aggregated group
  `https://hub.spigotmc.org/nexus/content/groups/public/` rather than the raw snapshots repo,
  because the raw snapshots repo no longer serves the transitive `bungeecord-chat:1.8-SNAPSHOT`
  metadata that Spigot 1.8.8 needs.
* All MBedwars references are isolated in `MBedwarsHook`, which is only instantiated when
  MBedwars is present. This lets the same plugin run on a pure lobby server (everyone `LOBBY`)
  without `NoClassDefFoundError`.
* The team colour is read from MBedwars (`Team.getBungeeChatColor()`), so custom team colours
  are respected instead of being hard-coded.
