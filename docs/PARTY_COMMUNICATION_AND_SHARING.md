# Party sharing and communication

XP sharing unlocks through both EXP_SHARING_RATE and EXP_SHARING_RADIUS buffs.
In the default skill-point mode, buy at least the first upgrade in each branch.
Members must be on the same server and within the spherical sharing radius.
Both the earning player and recipient must be accepted party members.
Recipients receive bonus XP without reducing the earning player's award.
Shared XP cannot create additional shared XP or party-level XP.
The legacy SQL ExpShare flag is now derived from the buffs on load/refresh,
so fresh and existing parties need no manual database changes to enable sharing.

Party.FriendlyFire defaults to false and is added to existing configurations.
Players sharing any accepted party membership cannot hurt each other through
direct attacks, projectiles, owned pets, attributed TNT, or harmful potions/clouds.
The same policy protects their tameable pets, including pets whose owner is
offline. Pet-to-player and pet-to-pet attacks/targeting are blocked between party
members. Self-damage and an owner's interactions with their own pet are unchanged.
Friendly damage is cancelled at LOWEST priority, before mcMMO's HIGHEST-priority
combat handlers can trigger dodge, rupture/bleeding, or other hit effects.
Paper's pre-attack event blocks friendly melee attempts before disarm, armor
damage, and direct-hit side effects. Projectile collisions and attributed
knockback are also cancelled. Fishing hooks cannot hook/pull friendly players or
pets; PlayerFishEvent is cancelled before mcMMO can steal inventory through Shake.
Ordinary fishing and fishing unrelated entities still work.

An mcMMO-specific listener cancels McMMOEntityDamageByRuptureEvent and zeroes its
damage for party victims. This covers secondary Serrated Strikes bleeding and
other attributed rupture ticks that modify health outside normal damage events.
The party relationship is checked on every tick, including after joining a party.
Blast Mining TNT attribution also recognizes metadata owned by mcMMO, using only
online or cached owner lookups. No global mcMMO abilities are disabled.
mcMMO can still emit an AoE notification/particle before its damage is blocked.
Beneficial potions and self-damage remain available. Unattributed environmental
damage (such as placed lava or fire) is outside this protection.
Set Party.FriendlyFire to true to allow party PvP.

`/party chat` toggles chat mode for the current active party. If chat mode is
already enabled, it turns it off regardless of the current active party.
`/party chat <party>` toggles mode for a named party you belong to.
`/party chat <message>` and `/party chat <party> <message>` still send one message.
A single word matching one of your party IDs selects that party instead.
Chat access must be unlocked. The selected chat party stays pinned when switching
active parties. Membership and access are rechecked before sending; if access is
lost, the message is discarded and mode is disabled, never sent to public chat.
Mode resets on disconnect, restart, or plugin disable and uses the existing
local/proxy party delivery service.
Party routing claims both Bukkit's legacy AsyncPlayerChatEvent and Paper's modern
AsyncChatEvent at LOWEST priority, and clears the public recipients/viewers.
Legacy events run first in Paper; cancellation propagates into the modern event,
which is skipped to avoid duplicate party delivery. This uses standard server
events without dependencies on ChatControl, OpenMCFramework, or other chat plugins.
Plugins relaying public chat must respect event cancellation. A plugin that sends
messages independently of cancellation needs its own integration/configuration;
ordinary Bukkit/Paper event listeners cannot retract messages already sent by it.

These changes add backward-compatible features; proposed release: 0.2.0.

## Treasury economy compatibility

The treasury starts with Party.DefaultTresorSize (default 50,000) plus size
upgrade bonuses. A +0 size buff means no additional capacity, not an empty limit.

Transfers use Vault's player-account overloads where supported. Reflection-based
bridges that reject player objects with ClassCastException are detected by a
read-only balance check; those transfers use Vault's legacy player-name overloads
for checks, withdrawals, deposits and refunds. Monetary operations are never
retried automatically with another overload. This works around the reported
EconomySystem 1.32-SNAPSHOT ReflectionVault player-to-String cast failure.
Non-finite transfer amounts are rejected before touching balances.

The economy compatibility fix on its own is a PATCH. The combined feature release
remains the proposed 0.2.0; build version metadata has not been changed here.
