---
title: Configuration
parent: McMMOParties
nav_order: 3
has_toc: true
---

# McMMOParties Configuration

McMMOParties ships with four main configuration files:

- `config.yml`: core plugin behavior, database, network, leveling, and buff mode settings
- `translations/<locale>/lang.yml`: messages, command text, placeholders, and buff display names
- `translations/<locale>/guis.yml`: GUI titles, slots, materials, button text, and lore
- `features/buffs.yml`: party buff trees for both `LEVEL` and `SKILLPOINTS` modes

## Example `config.yml`

```yaml
SQL:
  Type: SQLITE
  Host: /
  Database: /
  Username: /
  Password: /
  Port: /

translation: en

Network:
  ServerName: paper
  TeleportChannel: "mcmmoparties:teleport"
  PartyChatChannel: "mcmmoparties:partychat"

Commands:
  OverrideMcMMOPartyCommand: true
  OverrideMythicDungeonsCommand: true

Hooks:
  ChestShop:
    Enabled: true

DungeonInstance:
  DefaultSlots: 2

Party:
  BaseMemberSlots: 10
  PartyLevelCap: 100
  DefaultTresorSize: 50000

Buffs:
  Handler: SKILLPOINTS
  SkillPointsPerLevel: 1

Experience:
  LevelCurve: "(x * (20 + (2.5 * x))) * 25"
  Bar:
    Color: BLUE
    Segments: SOLID
  Scaling:
    MINING: 0.10
    WOODCUTTING: 0.10
    REPAIR: 0.04
    UNARMED: 0.09
    HERBALISM: 0.09
    EXCAVATION: 0.10
    ARCHERY: 0.09
    SWORDS: 0.09
    AXES: 0.09
    ACROBATICS: 0.03
    TAMING: 0.05
    FISHING: 0.07
    ALCHEMY: 0.05
    SMELTING: 0.04
    SALVAGE: 0.03
    MACES: 0.09
    TRIDENTS: 0.08
    CROSSBOWS: 0.08
    SPEARS: 0.08
```

## `config.yml` Reference

| Path | Type | Default | Description |
| --- | --- | --- | --- |
| `SQL.Type` | string | `SQLITE` | Database backend. Valid values are `SQLITE`, `MYSQL`, and `MARIADB`. |
| `SQL.Host` | string | `/` | SQL host for `MYSQL`/`MARIADB`. Ignored by `SQLITE`. |
| `SQL.Database` | string | `/` | Database name for `MYSQL`/`MARIADB`. `SQLITE` always uses `plugins/McMMOParties/Database.db`. |
| `SQL.Username` | string | `/` | Database username for `MYSQL`/`MARIADB`. Ignored by `SQLITE`. |
| `SQL.Password` | string | `/` | Database password for `MYSQL`/`MARIADB`. Ignored by `SQLITE`. |
| `SQL.Port` | integer | `/` | Database port for `MYSQL`/`MARIADB`. Ignored by `SQLITE`. |
| `translation` | string | `en` | Locale folder used under `translations/<locale>/`. Missing files fall back to English. |
| `Network.ServerName` | string | `paper` | Logical server identifier used for proxy chat and cross-server waypoint/teleport logic. This must match your network naming. |
| `Network.TeleportChannel` | string | `mcmmoparties:teleport` | Plugin messaging channel used between Paper and Velocity for waypoint teleports. Must match on both sides. |
| `Network.PartyChatChannel` | string | `mcmmoparties:partychat` | Plugin messaging channel used between Paper and Velocity for party chat relays. Must match on both sides. |
| `Commands.OverrideMcMMOPartyCommand` | boolean | `true` | Present in the file, but currently not read by the runtime code. Treat as unused for now. |
| `Commands.OverrideMythicDungeonsCommand` | boolean | `true` | Present in the file, but currently not read by the runtime code. Treat as unused for now. |
| `Hooks.ChestShop.Enabled` | boolean | `true` | Enables or disables the ChestShop integration layer. |
| `DungeonInstance.DefaultSlots` | integer | `2` | Base number of dungeon instance slots before party buff bonuses are added. |
| `Party.BaseMemberSlots` | integer | `10` | Base party member capacity before `MEMBER_SLOTS` buff bonuses are added. |
| `Party.PartyLevelCap` | integer | `100` | Maximum party level. Set to `-1` for no level cap. |
| `Party.DefaultTresorSize` | integer | `50000` | Base party treasury limit before `TRESOR_SIZE` buff bonuses are added. Set to `-1` for unlimited base treasury size. |
| `Buffs.Handler` | string | `SKILLPOINTS` | Buff mode. Valid values are `LEVEL` and `SKILLPOINTS`. |
| `Buffs.SkillPointsPerLevel` | integer | `1` | Number of party skill points granted per party level in `SKILLPOINTS` mode. |
| `Experience.LevelCurve` | string | `"(x * (20 + (2.5 * x))) * 25"` | Expression used to calculate required exp for level `x`. |
| `Experience.Bar.Color` | string | `BLUE` | Boss bar color enum used for party exp progress. Uses Bukkit `BarColor` names. |
| `Experience.Bar.Segments` | string | `SOLID` | Boss bar style enum used for party exp progress. Uses Bukkit `BarStyle` names. |
| `Experience.Scaling.MINING` | decimal | `0.10` | Party exp multiplier applied to gained mcMMO Mining exp. |
| `Experience.Scaling.WOODCUTTING` | decimal | `0.10` | Party exp multiplier applied to gained mcMMO Woodcutting exp. |
| `Experience.Scaling.REPAIR` | decimal | `0.04` | Party exp multiplier applied to gained mcMMO Repair exp. |
| `Experience.Scaling.UNARMED` | decimal | `0.09` | Party exp multiplier applied to gained mcMMO Unarmed exp. |
| `Experience.Scaling.HERBALISM` | decimal | `0.09` | Party exp multiplier applied to gained mcMMO Herbalism exp. |
| `Experience.Scaling.EXCAVATION` | decimal | `0.10` | Party exp multiplier applied to gained mcMMO Excavation exp. |
| `Experience.Scaling.ARCHERY` | decimal | `0.09` | Party exp multiplier applied to gained mcMMO Archery exp. |
| `Experience.Scaling.SWORDS` | decimal | `0.09` | Party exp multiplier applied to gained mcMMO Swords exp. |
| `Experience.Scaling.AXES` | decimal | `0.09` | Party exp multiplier applied to gained mcMMO Axes exp. |
| `Experience.Scaling.ACROBATICS` | decimal | `0.03` | Party exp multiplier applied to gained mcMMO Acrobatics exp. |
| `Experience.Scaling.TAMING` | decimal | `0.05` | Party exp multiplier applied to gained mcMMO Taming exp. |
| `Experience.Scaling.FISHING` | decimal | `0.07` | Party exp multiplier applied to gained mcMMO Fishing exp. |
| `Experience.Scaling.ALCHEMY` | decimal | `0.05` | Party exp multiplier applied to gained mcMMO Alchemy exp. |
| `Experience.Scaling.SMELTING` | decimal | `0.04` | Party exp multiplier applied to gained mcMMO Smelting exp. |
| `Experience.Scaling.SALVAGE` | decimal | `0.03` | Party exp multiplier applied to gained mcMMO Salvage exp. |
| `Experience.Scaling.MACES` | decimal | `0.09` | Party exp multiplier applied to gained mcMMO Maces exp. |
| `Experience.Scaling.TRIDENTS` | decimal | `0.08` | Party exp multiplier applied to gained mcMMO Tridents exp. |
| `Experience.Scaling.CROSSBOWS` | decimal | `0.08` | Party exp multiplier applied to gained mcMMO Crossbows exp. |
| `Experience.Scaling.SPEARS` | decimal | `0.08` | Party exp multiplier applied to gained mcMMO Spears exp. |

## `lang.yml` and `guis.yml`

You usually do not need separate documentation pages for these two files if you understand their role:

- `translations/<locale>/lang.yml` contains normal text output: command feedback, chat formatting, buff names, GUI helper labels, and placeholder-based messages.
- `translations/<locale>/guis.yml` contains inventory GUI layout and presentation: titles, sizes, slots, materials, lore lines, sorting labels, and button text.
- The `translation` key in `config.yml` picks the locale folder. If a requested file is missing for that locale, the plugin falls back to `translations/en/`.
- `lang.yml` is the right place to change wording, colors, and message formatting.
- `guis.yml` is the right place to change inventory layout, icons, titles, lore, and navigation buttons.

## Buffs (`features/buffs.yml`)

The shipped buff file is `features/buffs.yml`. It supports both buff handlers:

- `Level`: automatic unlocks based on party level
- `Skillpoints`: a skill tree where each spent point can also cost party treasury money and require progression conditions

### Buff Types

The plugin currently supports these buff keys:

- `EXP_SHARING_RATE`
- `EXP_SHARING_RADIUS`
- `MEMBER_SLOTS`
- `DUNGEON_INSTANCE_SLOTS`
- `TRESOR_SIZE`
- `ACCESS_PARTY_WAYPOINT`
- `ACCESS_PARTY_TRESOR`
- `ACCESS_PARTY_CHAT`
- `ABILITY_DURATION`
- `ABILITY_COOLDOWN_REDUCTION`

### `LEVEL` mode

When `Buffs.Handler: LEVEL` is active, buffs are granted automatically from the `Level:` section.

| Party Level | Granted Buffs |
| --- | --- |
| `1` | `EXP_SHARING_RATE +3`, `EXP_SHARING_RADIUS +12` |
| `5` | `ACCESS_PARTY_CHAT` unlocked |
| `10` | `MEMBER_SLOTS +1` |
| `15` | `ACCESS_PARTY_TRESOR` unlocked |
| `20` | `TRESOR_SIZE +25000` |
| `30` | `ABILITY_DURATION +5` |
| `40` | `MEMBER_SLOTS +1` |
| `50` | `ABILITY_COOLDOWN_REDUCTION.ALL +5` |
| `60` | `ACCESS_PARTY_WAYPOINT` unlocked |
| `75` | `DUNGEON_INSTANCE_SLOTS +1` |
| `90` | `EXP_SHARING_RATE +5`, `EXP_SHARING_RADIUS +12` |
| `100` | `ABILITY_DURATION +10`, `ABILITY_COOLDOWN_REDUCTION.ALL +10` |

### `SKILLPOINTS` mode

When `Buffs.Handler: SKILLPOINTS` is active:

- parties gain `Buffs.SkillPointsPerLevel` points per level
- each spent point can also consume party treasury balance
- the next point may have progression requirements
- the default tree is designed around a `PartyLevelCap` of `100`

The basic structure for one buff branch looks like this:

```yaml
Skillpoints:
  SOME_BUFF:
    Configuration:
      Costs:
        1: 250
        2: 400
      Conditions:
        2:
          PartyLevel: 10
          MINING: 150
          BuffLevel:
            OTHER_BUFF: 3
    1: 4
    2: 6
```

Meaning:

- `Configuration.Costs.<point>`: party treasury cost to buy that point
- `Configuration.Conditions.<point>`: requirements that must be met before that point can be purchased
- `<point>: <value>` outside `Configuration`: resulting buff value after spending that many points

### Supported condition types

| Condition form | Meaning |
| --- | --- |
| `PartyLevel: <amount>` | Requires the party to have reached that party level. |
| `<MCMMO_SKILL>: <amount>` | Requires the party's cumulative mcMMO level in that skill to meet the value. |
| `BuffLevel: <BUFF_TYPE>: <amount>` | Requires another buff branch to have at least that many spent points. |
| `BuffLevel: <BUFF_TYPE>: <ABILITY>: <amount>` | Requires an ability-specific buff branch to have that many spent points. |

For ability-specific branches, `ABILITY_COOLDOWN_REDUCTION` uses nested ability keys such as `ALL`.

### Default skillpoint tree summary

| Buff | Max points | Final effect | Treasury cost range | Notable requirements |
| --- | --- | --- | --- | --- |
| `EXP_SHARING_RATE` | `17` | `+45%` party exp share rate | `250` to `5400` | Starts open. Later ranks require party levels, Mining/Woodcutting/Herbalism totals, `EXP_SHARING_RADIUS`, `ABILITY_DURATION`, and `ACCESS_PARTY_WAYPOINT`. |
| `EXP_SHARING_RADIUS` | `12` | `72` blocks | `150` to `2300` | Later ranks require party levels, Excavation/Mining/Herbalism totals, `ACCESS_PARTY_CHAT`, `EXP_SHARING_RATE`, `ABILITY_DURATION`, and `ACCESS_PARTY_WAYPOINT`. |
| `TRESOR_SIZE` | `17` | Unlimited at point `15+` | `300` to `9500` | Requires party level milestones, Repair/Smelting/Salvage totals, `ACCESS_PARTY_TRESOR`, `MEMBER_SLOTS`, `EXP_SHARING_RATE`, `ABILITY_DURATION`, `ACCESS_PARTY_WAYPOINT`, and `ABILITY_COOLDOWN_REDUCTION.ALL`. |
| `ACCESS_PARTY_WAYPOINT` | `1` | Unlocks party waypoint access | `1500` | Requires `PartyLevel 20`, `EXCAVATION 400`, `ACCESS_PARTY_CHAT 1`, and `ACCESS_PARTY_TRESOR 1`. |
| `ACCESS_PARTY_TRESOR` | `1` | Unlocks party treasury access | `0` | Requires `PartyLevel 10` and `REPAIR 250`. |
| `ACCESS_PARTY_CHAT` | `1` | Unlocks party chat | `250` | Requires `PartyLevel 2` and `HERBALISM 75`. |
| `ABILITY_DURATION` | `18` | `+40s` ability duration | `250` to `5600` | Requires party levels, Swords/Axes/Unarmed/Archery/Maces totals, `EXP_SHARING_RATE`, `MEMBER_SLOTS`, `EXP_SHARING_RADIUS`, `ABILITY_COOLDOWN_REDUCTION.ALL`, and `ACCESS_PARTY_WAYPOINT`. |
| `ABILITY_COOLDOWN_REDUCTION.ALL` | `15` | `+35s` cooldown reduction | `300` to `4600` | Requires party levels, Unarmed/Swords/Axes/Archery/Maces/Tridents totals, `ABILITY_DURATION`, and `ACCESS_PARTY_WAYPOINT`. |
| `MEMBER_SLOTS` | `15` | `+15` party member slots | `300` to `6000` | Requires party levels, Taming totals, `ACCESS_PARTY_CHAT`, `EXP_SHARING_RATE`, `TRESOR_SIZE`, `ABILITY_DURATION`, `EXP_SHARING_RADIUS`, and `ACCESS_PARTY_WAYPOINT`. |
| `DUNGEON_INSTANCE_SLOTS` | `3` | `+3` dungeon instance slots | `250` to `500` | Requires party levels, Taming/Swords/Tridents totals, `MEMBER_SLOTS`, and `ACCESS_PARTY_TRESOR`. |

### Default skillpoint branch values

| Buff | Value progression |
| --- | --- |
| `EXP_SHARING_RATE` | `4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 35, 40, 45` percent |
| `EXP_SHARING_RADIUS` | `12, 16, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72` blocks |
| `TRESOR_SIZE` | `10000` up to `220000`, then unlimited from point `15` onward |
| `ACCESS_PARTY_WAYPOINT` | `1` unlock point |
| `ACCESS_PARTY_TRESOR` | `1` unlock point |
| `ACCESS_PARTY_CHAT` | `1` unlock point |
| `ABILITY_DURATION` | `2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 33, 36, 40` seconds |
| `ABILITY_COOLDOWN_REDUCTION.ALL` | `2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 27, 30, 35` seconds |
| `MEMBER_SLOTS` | `+1` through `+15` slots |
| `DUNGEON_INSTANCE_SLOTS` | `+1, +1, +1` slots |

### Special parsing rules

- Negative treasury-size values such as `-1` are treated as unlimited.
- Text values such as `infinite`, `infinity`, or `unlimited` are also treated as unlimited where integer size values are expected.
- `BuffMeta` defines GUI icon materials for each buff. Ability-specific icons can be overridden under nested keys such as `BuffMeta.ABILITY_COOLDOWN_REDUCTION.ALL.IconMaterial`.

### Practical editing tips

- If you only want to rebalance progression speed, start with `Buffs.SkillPointsPerLevel`, `Party.PartyLevelCap`, and the `Configuration.Costs` blocks.
- If you want to gate late-game upgrades harder, edit `Configuration.Conditions` rather than the displayed values.
- If you want to change what a purchased point actually gives, edit the numbered value map under each buff branch.
- If you want a simpler server setup, use `LEVEL` mode and maintain only the `Level:` section.
