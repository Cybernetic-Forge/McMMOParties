---
title: API Information
parent: McMMOParties
nav_order: 3
has_toc: true
---

# McMMOParties API Information

McMMOParties exposes a Bukkit-facing API for:

- reading loaded parties
- looking up a player's party
- using helper service methods for party and buff operations
- listening to cancellable party events
- triggering built-in event handler flows for integrations

## Main Entry Point

The static entry point is `McMMOPartyAPI.java`.

It exposes:

- `McMMOPartyAPI.getPartyLoader()`
- `McMMOPartyAPI.getPartyService()`
- `McMMOPartyAPI.getPartyEventHandler()`

Example:

```java
import net.maksy.mcmmoparties.api.McMMOPartyAPI;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;

McMMOParty party = McMMOPartyAPI.getPartyLoader().getParty("my_party");
```

## Core Components

### `PartyLoader`

`PartyLoader` is the in-memory party registry. See `PartyLoader.java`.

Main capabilities:

- `getParty(String partyID)`: lookup by party id
- `getParties()`: get all loaded parties
- `getPartyNames()`: get all loaded party ids
- `getPartyOfPlayer(UUID uuid)`: find the party a player belongs to
- `update(McMMOParty party)`: save and reload one party
- `scheduleSave(McMMOParty party)`: delayed async save
- `reload()`: async full reload from storage
- `reload(String partyID)`: async reload of one party
- `flushPendingSaves()`: force pending saves

Important note:

- `reload()` and `reload(String)` use async SQL helpers, so an immediate read in the same tick may still see old state briefly.

### `McMMOPartyService`

`McMMOPartyService` is the higher-level helper layer. See `McMMOPartyService.java`.

Lookup helpers:

- `getParty(String partyId)`
- `getPartyOfPlayer(UUID playerId)`
- `getPartyNames()`
- `getOnlinePlayerNames()`
- `getPartyLoader()`
- `getPartyEventHandler()`

Party management helpers:

- `invitePlayer(McMMOParty party, UUID playerId)`
- `disbandParty(McMMOParty party)`
- `forceKickMember(McMMOParty party, OfflinePlayer player)`

Progression helpers:

- `applyTotalExperience(McMMOParty party, float totalExperience)`
- `applyPartyLevel(McMMOParty party, long level)`
- `setPartySkillPoints(McMMOParty party, int totalSkillPoints)`

Buff helpers:

- `getBuffSpentPoints(McMMOParty party, PartyBuffType type, String ability)`
- `setBuffSpentPoints(McMMOParty party, PartyBuffType type, String ability, int points)`
- `normalizeAbility(PartyBuffType type, String ability)`
- `requiresAbility(PartyBuffType type)`

Economy helpers:

- `setPartyBalance(McMMOParty party, double balance)`
- `isBalanceWithinLimit(McMMOParty party, double balance)`

Notes:

- These service methods are the safest way to perform admin-style changes because they refresh derived state like buffs, level progress, and loader cache.
- `ABILITY_COOLDOWN_REDUCTION` is currently the only ability-specific buff branch.

### `PartyEventHandler`

`PartyEventHandler` is the plugin's internal event dispatcher. See `PartyEventHandler.java`.

It does two jobs:

- constructs and fires Bukkit events such as `PartyMemberJoinEvent`
- performs the plugin's built-in follow-up logic if the event is not cancelled

This means you can:

- listen to these events like normal Bukkit events
- cancel them to block the default behavior
- mutate supported fields before the plugin continues

Common dispatcher methods include:

- `callPartyLevelChangedEvent(McMMOParty party)`
- `callPartyExpChangedEvent(Player player, McMMOParty party, McMMOPlayerXpGainEvent event)`
- `callPartyShareExpEvent(Player player, McMMOParty party, McMMOPlayerXpGainEvent event)`
- `callPartyMemberJoinEvent(McMMOParty party, OfflinePlayer newComer, boolean instant)`
- `callPartyMemberLeaveEvent(McMMOParty party, OfflinePlayer leaver)`
- `callPartyMemberKickEvent(McMMOParty party, OfflinePlayer kickedPlayer)`
- `callPartyLeaderChangeEvent(OfflinePlayer player, UUID newLeader, McMMOParty party, boolean left)`
- `callPartyWaypointSetEvent(...)`
- `callPartyWaypointTeleportEvent(...)`
- `callPartyTresorDepositEvent(...)`
- `callPartyTresorWithdrawEvent(...)`
- `callPartyChatWriteEvent(...)`
- `callPartyBuffHighlightEvent(...)`
- `callPartyBuffUpgradeEvent(...)`
- `callPartyDisbandEvent(...)`

## `McMMOParty` Model

The main runtime model is `McMMOParty.java`.

Important getters and fields:

- `getPartyID()`
- `getDisplay()`
- `getLevel()`
- `getOwner()`
- `getMembers()`
- `getPartySettings()`
- `getBuffHandler()`
- `getCurrentExperience()`
- `getNeededExperience()`
- `getTotalExperience()`
- `getBalance()`
- `getMaxMembers()`
- `getMaxTresorSize()`

Role and permission helpers:

- `isOwner(UUID uuid)`
- `getPartyState(UUID uuid)`
- `canManageParty(UUID uuid)`
- `canDisband(UUID uuid)`
- `canManageChestShop(UUID uuid)`
- `canUpgradeBuffs(UUID uuid)`
- `canManageMemberRoles(UUID uuid)`
- `canManageDungeonInstances(UUID uuid)`
- `canAccessWaypoint(UUID uuid)`
- `canAccessTresor(UUID uuid)`
- `canAccessPartyChat(UUID uuid)`

State update helpers:

- `setExperience(float experience)`
- `setTotalExperience(float experience)`
- `setLevel(long level)`
- `setOwner(UUID owner)`
- `setPartyState(UUID uuid, PartyState state)`
- `removeMemberState(UUID uuid)`
- `refreshBuffs()`
- `refreshLevelProgress()`
- `announceToMembers(String message)`

Important note:

- Directly mutating a `McMMOParty` does not automatically persist everything. If you make manual changes, follow up with a loader or service save path.

## Related Data Types

### `PartySettings`

See `PartySettings.java`.

Useful capabilities:

- read and change lock state
- read and change password
- access skill requirements
- access exp-sharing state
- validate join passwords with `isAuthorized(...)`

### `PartyWaypoint`

See `PartyWaypoint.java`.

It stores:

- `partyID`
- `server`
- `world`
- `x`, `y`, `z`
- `yaw`, `pitch`

### `BuffUpgradeCondition`

See `BuffUpgradeCondition.java`.

Condition types:

- `MCMMO_SKILL`
- `PARTY_LEVEL`
- `BUFF_LEVEL`

Each condition can describe:

- a required cumulative mcMMO skill level
- a required party level
- a required buff rank, optionally for a specific ability branch

## Event System

All custom party events extend `PartyEvent.java`.

Shared behavior:

- every event is a Bukkit `Event`
- every event is `Cancellable`
- every event exposes `getParty()`

### Event List

| Event | Purpose | Mutable fields | Typical use |
| --- | --- | --- | --- |
| `PartyLevelChangeEvent` | Fired before the party levels up. | `nextLevel` | Change or block a level-up. |
| `PartyExpChangeEvent` | Fired when party exp is awarded from mcMMO gain. | `amount` | Modify party exp gain. |
| `PartyShareExpEvent` | Fired before shared exp is given to a player. | `sharedExp` | Modify or block shared exp. |
| `PartyMemberJoinEvent` | Fired before a member joins or is invited via handler flow. | None beyond cancellation | Block join or invite logic. |
| `PartyMemberLeaveEvent` | Fired before a member leaves. | None beyond cancellation | Block leaving logic. |
| `PartyMemberKickEvent` | Fired before a member is kicked. | None beyond cancellation | Block kicks. |
| `PartyLeaderChangeEvent` | Fired before leadership changes. | None beyond cancellation | Block owner transfer. |
| `PartyDisbandEvent` | Fired before a party disbands. | None beyond cancellation | Block disbanding. |
| `PartyWaypointSetEvent` | Fired before a waypoint is stored. | `waypoint` | Redirect or rewrite waypoint target. |
| `PartyWaypointTeleportEvent` | Fired before waypoint teleport logic runs. | `waypoint` | Redirect teleport destination. |
| `PartyTresorDepositEvent` | Fired before a treasury deposit is applied. | `amount` | Modify or block deposits. |
| `PartyTresorWithdrawEvent` | Fired before a treasury withdrawal is applied. | `amount` | Modify or block withdrawals. |
| `PartyChatWriteEvent` | Fired before party chat is sent. | `message`, recipient list | Filter chat or retarget recipients. |
| `PartyBuffHighlightEvent` | Fired when a player highlights a preferred buff. | `buffType`, `ability` | Remap highlight target. |
| `PartyBuffUpgradeEvent` | Fired before a buff upgrade is purchased. | `treasuryCost` | Raise, lower, or block upgrade cost. |

### Event Details

#### `PartyLevelChangeEvent`

- `getLevel()`: current level
- `getNextLevel()`: proposed next level
- `setNextLevel(long)`: override the target level

#### `PartyExpChangeEvent`

- `getAmount()`: exp about to be added
- `setAmount(float)`: modify exp gain
- `getExperience()`: current total party experience before applying the new amount

#### `PartyShareExpEvent`

- `getSkill()`
- `getSharedExp()`
- `setSharedExp(float)`

#### `PartyMemberJoinEvent`

- `getJoinedPerson()`
- `getPreMembers()`

#### `PartyMemberLeaveEvent`

- `getLeftPerson()`
- `getMembers()`

#### `PartyMemberKickEvent`

- `getKickedPerson()`
- `getMembers()`

#### `PartyLeaderChangeEvent`

- `getPreLeader()`
- `getNewLeader()`

#### `PartyWaypointSetEvent`

- `getPlayer()`
- `getPreviousWaypoint()`
- `getWaypoint()`
- `setWaypoint(PartyWaypoint)`

#### `PartyWaypointTeleportEvent`

- `getPlayer()`
- `getWaypoint()`
- `setWaypoint(PartyWaypoint)`

#### `PartyTresorDepositEvent` / `PartyTresorWithdrawEvent`

- `getPlayer()`
- `getAmount()`
- `setAmount(double)`

#### `PartyChatWriteEvent`

- `getPlayer()`
- `getMessage()`
- `setMessage(String)`
- `getRecipientIds()`: mutable backing list
- `getRecipientIdsView()`: read-only view

#### `PartyBuffHighlightEvent`

- `getPlayer()`
- `getBuffType()`
- `setBuffType(PartyBuffType)`
- `getAbility()`
- `setAbility(String)`

#### `PartyBuffUpgradeEvent`

- `getPlayer()`
- `getBuffType()`
- `getAbility()`
- `getMaxPoints()`
- `getTreasuryCost()`
- `setTreasuryCost(double)`
- `getConditions()`: read-only view of required conditions

## Example Event Listener

```java
@EventHandler
public void onPartyExp(net.maksy.mcmmoparties.api.events.PartyExpChangeEvent event) {
    if (event.getParty().getPartyID().equalsIgnoreCase("vip")) {
        event.setAmount(event.getAmount() * 1.5f);
    }
}
```

## Example Lookups

```java
McMMOPartyService service = McMMOPartyAPI.getPartyService();
McMMOParty party = service.getPartyOfPlayer(player.getUniqueId());

if (party != null && party.canUpgradeBuffs(player.getUniqueId())) {
    player.sendMessage("You can manage buffs for " + party.getPartyID());
}
```

## Practical Notes

- Prefer `McMMOPartyService` for admin-like changes instead of directly editing SQL or partially mutating the model.
- Prefer listening to events rather than forking core command logic.
- All custom events are cancellable, even when they do not expose extra mutable fields.
- Some handler methods continue with async SQL follow-up work after the event is accepted, so do not assume every side effect is complete immediately after the method call returns.
