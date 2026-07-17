# ChatPlaceholder

A small "core" plugin for a Minecraft **1.8.8** server (CarbonSpigot, Java 21) that acts as a
central placeholder switch. It uses the official **MBedwars** API to detect whether a player is
currently in a running Bedwars round or in the lobby, and exposes clean `%server_...%`
placeholders through **PlaceholderAPI** so that **Phoenix chat**, the **TAB** plugin,
scoreboards, nametags, etc. all stay in sync — you only configure the switch in one place.

```
Lobby:    [MVP] gravijet: Hallo
Bedwars:  [R] gravijet: Hallo
```

## How it works

* **Detection** is done exclusively through the MBedwars API (never through other plugins'
  placeholders). A player counts as `BEDWARS` only when **all** of the following are true:
  1. an MBedwars arena player object exists (`GameAPI.get().getArenaByPlayer` is not `null`),
  2. the arena status is `RUNNING`,
  3. the player has a team (`Arena.getPlayerTeam` is not `null`).

  Otherwise the player is `LOBBY` (this also covers spectators and the pre-game waiting lobby).
* **Lobby values** come from Phoenix, resolved through PlaceholderAPI (rank prefix, rank colour,
  rank priority). All configurable.
* **Bedwars values** are built from the MBedwars team (colour, initials, enum order).
* MBedwars events (`PlayerJoinArenaEvent`, `PlayerQuitArenaEvent`, `ArenaStatusChangeEvent`)
  trigger an instant TAB refresh so transitions are immediate. The placeholders themselves are
  always computed live, so this is only a responsiveness optimisation.

## Placeholders (expansion identifier: `server`)

| Placeholder               | Lobby                                 | Bedwars                        |
|---------------------------|---------------------------------------|--------------------------------|
| `%server_mode%`           | `LOBBY`                               | `BEDWARS`                      |
| `%server_chat_prefix%`    | rank prefix, e.g. `&b[MVP]`           | team prefix, e.g. `&c[R] `     |
| `%server_tab_prefix%`     | rank prefix, e.g. `&b[MVP]`           | team prefix, e.g. `&c[R] `     |
| `%server_tab_sort%`       | rank priority (`1009899`)             | team order (`0029899`)         |
| `%server_tab_name_color%` | rank colour, e.g. `&b`                | team colour, e.g. `&c`         |
| `%server_team%`           | `NONE`                                | `RED` / `BLUE` / `GREEN` / …   |
| `%server_team_color%`     | *(empty)*                             | `&c` / `&9` / `&a` / …         |

### About `%server_tab_sort%`

A zero-padded number, **lowest value on top**, built as `<block><team><rank>`:

* `block` — `0` for Bedwars players, `1` for lobby players, so a running round stays on top.
* `team` — the MBedwars team order, so all of RED sits together, then BLUE, …
* `rank` — the inverted Phoenix rank priority, so staff stay on top (inside a Bedwars team too).

Because every segment has a fixed width, TAB's numeric (`PLACEHOLDER_LOW_TO_HIGH`) and
alphabetic (`PLACEHOLDER_A_TO_Z`) sorting produce the exact same order.

> ⚠️ **Identifier clash:** PlaceholderAPI allows only **one** expansion per identifier, and the
> popular eCloud *Server* expansion (`%server_online%`, `%server_tps%`, `%server_ram%`) also uses
> `server`. ChatPlaceholder takes it over and logs a warning on startup. If you need those
> eCloud placeholders, set `expansion.identifier` in `config.yml` to a free name (e.g. `gj` →
> `%gj_tab_prefix%`) and restart.

## Requirements

| Plugin          | Needed for                              | Hard dependency? |
|-----------------|-----------------------------------------|------------------|
| PlaceholderAPI  | registering / serving the placeholders  | **yes**          |
| MBedwars        | Bedwars detection (else everyone `LOBBY`)| soft             |
| Phoenix         | lobby rank prefix / colour / priority   | soft             |
| TAB             | instant refresh on transitions          | soft             |

## Usage

### Phoenix chat

Take your existing lobby format and replace the `<prefix>` token with `%server_chat_prefix%`:

```yaml
# before: <prefix><color><player><suffix><tag>&7: &f<chatcolor><message>
chat-format: "%server_chat_prefix%<color><player><suffix><tag>&7: &f<chatcolor><message>"
```

The lobby keeps rendering exactly as it did (`<prefix>` resolves to the rank prefix), and in a
Bedwars round the very same format switches to `&c[R] `.

### TAB

`plugins/TAB/groups.yml` — one config for both states, `%phoenix_player_name%` is the Phoenix
name *including* nicks:

```yaml
_DEFAULT_:
  tabprefix: "%server_tab_prefix%"
  customtabname: "%server_tab_name_color%%phoenix_player_name%"
  tabsuffix: ""
```

`plugins/TAB/config.yml`:

```yaml
scoreboard-teams:
  enabled: true
  sorting-types:
    - "PLACEHOLDER_LOW_TO_HIGH:%server_tab_sort%"
```

That gives `[MVP] gravijet` in the lobby (rank prefix, no tags) and `[R] gravijet` in a round,
sorted by rank and by Bedwars team respectively.

### Verifying

```
/papi parse me %server_mode%
/papi parse me %server_chat_prefix%
/papi parse me %server_tab_sort%
```

If a placeholder shows up **literally** (e.g. `%server_tab_prefix%`), it is not registered —
check the startup log, ChatPlaceholder reports the exact reason (identifier clash, missing
PlaceholderAPI, …).

> After changing TAB/PlaceholderAPI setup, do a full server restart (not `/reload`) so the
> placeholders register cleanly.

## Configuration (`config.yml`)

The Phoenix placeholder names are the ones from
[the Phoenix docs](https://docs.refinedev.org/Phoenix/Placeholders/) — note that
`%phoenix_prefix%` does **not** exist; the correct name is `%phoenix_player_rank_prefix%`.

```yaml
expansion:
  identifier: "server"          # restart required
mode:
  lobby: "LOBBY"
  bedwars: "BEDWARS"
team:
  none: "NONE"
bedwars:
  # Tokens: {color} {initials} {team} {team_id}
  chat-prefix-format: "{color}[{initials}] "
  tab-prefix-format: "{color}[{initials}] "
lobby:
  chat-prefix-placeholder: "%phoenix_player_rank_prefix%"
  tab-prefix-placeholder: "%phoenix_player_rank_prefix%"
  prefix-fallback: ""
  name-color-placeholder: "%phoenix_player_rank_color%"
  name-color-fallback: "&7"
  priority-placeholder: "%phoenix_player_rank_priority%"
  priority-fallback: 0
tab-sort:
  higher-priority-is-higher-rank: true
  max-rank-priority: 9999
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
* Unresolvable Phoenix placeholders fall back to a configured value instead of leaking a raw
  `%phoenix_...%` into chat or TAB.
