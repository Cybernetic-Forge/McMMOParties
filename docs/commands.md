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
| `/party accept <player>` | Accepts a pending join request from a player into your party. | None |
| `/party chat <message...>` | Sends a message to party chat. | None |
| `/party create <name>` | Opens the party creation flow for a new party name. | None |
| `/party disband` | Disbands your party if you are allowed to disband it. | None |
| `/party info` | Opens the overview for your current party. | None |
| `/party info <party>` | Opens the overview for the specified party. | None |
| `/party invite <player>` | Invites an online player to your party. | None |
| `/party join <party>` | Joins a public party, or an invited private party. | None |
| `/party join <party> <password>` | Joins a password-protected party using its password. | None |
| `/party kick <player>` | Kicks a member from your party. | None |
| `/party leave` | Leaves your current party. Party owners cannot use this to leave their own party. | None |
| `/party list` | Opens the party list GUI. | None |
| `/party list <page>` | Opens the party list GUI on the given page. | None |
| `/party list <page> <sort>` | Opens the party list GUI using the given page and sort mode. | None |
| `/party list <sort>` | Opens the party list GUI using the given sort mode. | None |
| `/party newleader <player>` | Transfers party leadership to another party member. | None |

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

- Most player-facing `/party` subcommands do not have dedicated permission checks in code.
- `/party reload` is handled through the user command root, but it is an admin-only command.
- Admin subcommands accept either the specific child permission listed above or the umbrella permission `mcmmoparties.admin`.
- Buff commands only work when the plugin is using `SKILLPOINTS` buff mode.
