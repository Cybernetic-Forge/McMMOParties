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
| `/party territory [info]` | Shows the active party's claim usage and the owner of the current chunk. | None; hidden while territory is disabled |
| `/party territory claim [party]` | Starts a visual preview of the current chunk for the specified membership, or the active party when omitted. | Party owner, co-owner, or territory manager |
| `/party territory preview [party]` | Toggles a persistent visual overview of nearby chunks without charging anything. Use it again to disable the overview. | Party owner, co-owner, or territory manager |
| `/party territory confirm` | Confirms the active preview and claims the chunk. | Party owner, co-owner, or territory manager |
| `/party territory cancel` | Cancels the active chunk preview. | Party owner, co-owner, or territory manager |
| `/party territory unclaim` | Unclaims the current party-owned chunk. | Party owner, co-owner, or territory manager |
| `/party territory list [party]` | Lists the chunks claimed by the specified membership, or the active party when omitted. | Party member |
| `/party territory permission <player> <permission> <allow\|deny\|default>` | Sets or resets one member's territory permission for the active party. | Party owner, co-owner, or territory manager |

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
| `/pa-admin territory claim <party>` | Claims the administrator's current chunk for a party without charging costs or applying the normal claim limit/adjacency rule. | `mcmmoparties.admin.territory` or `mcmmoparties.admin` |
| `/pa-admin territory unclaim` | Administratively unclaims the current chunk. | `mcmmoparties.admin.territory` or `mcmmoparties.admin` |
| `/pa-admin territory list <party>` | Lists a party's claimed chunks. | `mcmmoparties.admin.territory` or `mcmmoparties.admin` |
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
- Territory is disabled by default. While disabled, its command suggestions and Party Info GUI elements are hidden and territory protection is inactive.
- Territory denial messages use the owning party's configured display name. They do not expose the player who originally claimed the chunk.
- Claim previews show the current chunk with temporary particle corner markers. Green means claimable, yellow means already claimed, gray means an external claim conflict, and red means another validation failure. Previewing never charges the party or player; costs are applied only by confirmation.
- `/party territory preview` toggles a persistent particle overview around the player. Use the same command again to disable it. The visible radius is configured with `Territory.Preview.RadiusChunks`.
- `mcmmoparties.admin.territory.bypass` bypasses protection checks inside party territory.
