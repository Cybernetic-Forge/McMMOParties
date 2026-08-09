---
title: Commands
parent: McMMOParties
nav_order: 1
has_toc: true
---

# McMMOParties Commands

Primary command aliases:

- User commands: `/party`, `/pa`
- Admin commands: `/pa-admin`, `/party-admin`

## User Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/party` | Opens the party hub for browsing parties, creating a party, and managing join requests. | None |
| `/party accept <player> [party]` | Accepts a pending join request into the specified party, or your active party when omitted. | None |
| `/party chat <message...>` | Sends a message to your active party chat. | None |
| `/party chat <party> <message...>` | Sends a message to the specified party chat. | None |
| `/party create <name>` | Opens the party creation flow for a new party name. | None |
| `/party disband [party]` | Disbands the specified party, or your active party when omitted. | None |
| `/party info` | Opens the overview for your current party. | None |
| `/party info <party>` | Opens the overview for the specified party. | None |
| `/party invite <player> [party]` | Invites an online player to the specified party, or your active party when omitted. | None |
| `/party join <party>` | Joins a public party, or an invited private party. | None |
| `/party join <party> <password>` | Joins a password-protected party using its password. | None |
| `/party kick <player> [party]` | Kicks a member from the specified party, or your active party when omitted. | None |
| `/party leave [party]` | Leaves the specified party, or your active party when omitted. Party owners cannot leave their own party. | None |
| `/party list` | Opens the party list GUI. | None |
| `/party list <page>` | Opens the party list GUI on the given page. | None |
| `/party list <page> <sort>` | Opens the party list GUI using the given page and sort mode. | None |
| `/party list <sort>` | Opens the party list GUI using the given sort mode. | None |
| `/party newleader <player> [party]` | Transfers leadership of the specified party, or your active party when omitted. | None |

## Admin Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/pa-admin balance <add\|remove\|set> <party> <amount>` | Adjusts a party balance by adding, removing, or setting an amount. | `mcmmoparties.admin.balance` or `mcmmoparties.admin` |
| `/pa-admin balance show <party>` | Shows the current balance of a party. | `mcmmoparties.admin.balance` or `mcmmoparties.admin` |
| `/pa-admin buff <add\|remove\|set> <party> <buff_type> [ability] <amount>` | Adjusts invested points for a party buff. Some buff types require an ability key. | `mcmmoparties.admin.buff` or `mcmmoparties.admin` |
| `/pa-admin buff show <party> <buff_type> [ability]` | Shows invested points for a party buff. | `mcmmoparties.admin.buff` or `mcmmoparties.admin` |
| `/pa-admin disband <party>` | Forcefully disbands a party. | `mcmmoparties.admin.disband` or `mcmmoparties.admin` |
| `/pa-admin exp <add\|remove\|set> <party> <amount>` | Adjusts a party's total experience. | `mcmmoparties.admin.exp` or `mcmmoparties.admin` |
| `/pa-admin exp show <party>` | Shows a party's total experience and level. | `mcmmoparties.admin.exp` or `mcmmoparties.admin` |
| `/pa-admin invite <party> <player>` | Sends an invite from a party to an online player. | `mcmmoparties.admin.invite` or `mcmmoparties.admin` |
| `/pa-admin kick <party> <player>` | Forcefully kicks a member from a party. | `mcmmoparties.admin.kick` or `mcmmoparties.admin` |
| `/pa-admin level <add\|remove\|set> <party> <amount>` | Adjusts a party's level. | `mcmmoparties.admin.level` or `mcmmoparties.admin` |
| `/pa-admin level show <party>` | Shows a party's current level and total experience. | `mcmmoparties.admin.level` or `mcmmoparties.admin` |
| `/pa-admin skillpoints <add\|remove\|set> <party> <amount>` | Adjusts a party's total skill points. | `mcmmoparties.admin.skillpoints` or `mcmmoparties.admin` |
| `/pa-admin skillpoints show <party>` | Shows a party's total skill points. | `mcmmoparties.admin.skillpoints` or `mcmmoparties.admin` |
| `/party reload` | Reloads the plugin configuration, translations, and cached party state. | `mcmmoparties.admin` or operator |

## Notes

- In the party browser, left-click opens the selected party overview and right-click sends a join request.
- In the main party hub, left-clicking the center button creates a party and right-clicking opens the player's memberships. In that list, left-click views a party, owner-only right-click edits it, and shift-left-click makes it active.
- Commands without an explicit party argument operate on the persisted active party. If no selection exists, the first membership by party ID is used.
- A player can belong to multiple parties up to `Party.MaxPartiesPerPlayer`; earned party experience and party buffs are processed for each membership.
- Most player-facing `/party` subcommands do not have dedicated permission checks in code.
- `/party reload` is handled through the user command root, but it is an admin-only command.
- Admin subcommands accept either the specific child permission listed above or the umbrella permission `mcmmoparties.admin`.
- Buff commands only work when the plugin is using `SKILLPOINTS` buff mode.
