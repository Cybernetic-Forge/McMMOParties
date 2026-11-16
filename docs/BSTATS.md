# bStats

McMMOParties uses the Bukkit / Spigot / Paper service ID `35349`. Both the Paper and
Universal builds use that ID. Each
reporting Paper backend counts as a server. The Velocity entry point does not report.

Reporting is enabled by default. To disable it for McMMOParties, set `Metrics.Enabled`
to `false` in `plugins/McMMOParties/config.yml` and restart the server. The global
bStats switch in `plugins/bStats/config.yml` also applies.

Add the following [custom charts](https://bstats.org/docs/custom-charts) in the service's
dashboard using the exact IDs below. bStats supplies the standard server and online-player
counts, version breakdowns and runtime metrics.

| Chart ID | Type | What it measures |
|---|---|---|
| `database_type` | Simple Pie | Configured storage engine |
| `translation` | Simple Pie | Bundled translation in use |
| `buff_mode` | Simple Pie | Effective party buff mode |
| `friendly_fire` | Simple Pie | Server-wide party friendly-fire setting |
| `membership_policy` | Simple Pie | Single, multiple or unlimited party memberships per player |
| `party_level_cap` | Simple Pie | Capped or unlimited party progression |
| `integrations` | Advanced Pie | Backends with mcMMO, Vault economy, ChestShop or MythicDungeons available to the plugin |
| `network_mode` | Simple Pie | Locally configured proxy chat and teleport channels |
| `loaded_party_count` | Simple Pie | Number of parties loaded by each backend, grouped into ranges |
| `party_sizes` | Advanced Pie | Loaded parties by membership size |
| `party_levels` | Advanced Pie | Loaded parties by level range |
| `party_features` | Advanced Pie | Private parties, XP sharing enabled, and chat, waypoint or treasury unlocked |

The advanced pies can count a backend or a party under multiple applicable categories.
Party charts describe each backend's loaded data. Backends using a shared database can
report the same parties, so chart totals are not unique party counts across a network.
Feature settings and unlocks describe availability. Proxy configuration describes the
local setup and does not test whether a proxy is reachable.

Custom charts send counts and fixed categories. They do not send player or party names,
UUIDs, chat messages, server names, database connection details or messaging secrets.
