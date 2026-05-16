package net.maksy.mcmmoparties.configuration.enums;

public enum PartyState {
    OWNER,
    CO_OWNER,
    SHOP_MANAGER,
    BUFF_MANAGER,
    MEMBER,
    PENDING,
    NONE;

    public boolean isActiveMember() {
        return this != PENDING && this != NONE;
    }

    public boolean canManageParty() {
        return this == OWNER || this == CO_OWNER;
    }

    public boolean canDisbandParty() {
        return this == OWNER;
    }

    public boolean canManageChestShop() {
        return this == OWNER || this == CO_OWNER || this == SHOP_MANAGER;
    }

    public boolean canUpgradeBuffs() {
        return this == OWNER || this == CO_OWNER || this == BUFF_MANAGER;
    }

    public boolean canManageMemberRoles() {
        return this == OWNER;
    }
}
