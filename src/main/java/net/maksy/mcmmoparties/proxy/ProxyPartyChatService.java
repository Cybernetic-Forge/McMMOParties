package net.maksy.mcmmoparties.proxy;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class ProxyPartyChatService {

    private ProxyPartyChatService() {
    }

    public static String getChannelName() {
        return McMMOParties.getConfigManager().getPartyChatChannel();
    }

    public static void sendPartyChat(Player sender, McMMOParty party, String message) {
        if (sender == null || party == null || message == null || message.isBlank()) {
            return;
        }

        var chatEvent = McMMOParties.getPartyEventHandler().callPartyChatWriteEvent(
                sender,
                party,
                message,
                new ArrayList<>(party.getMembers())
        );
        if (chatEvent.isCancelled() || chatEvent.getMessage() == null || chatEvent.getMessage().isBlank()) {
            return;
        }

        message = chatEvent.getMessage();
        List<UUID> recipients = new ArrayList<>(chatEvent.getRecipientIdsView());

        List<UUID> remoteRecipients = new ArrayList<>();
        for (UUID memberId : recipients) {
            Player localRecipient = Bukkit.getPlayer(memberId);
            if (localRecipient != null && localRecipient.isOnline()) {
                localRecipient.sendMessage(formatMessage(
                        sender.getName(),
                        party.getPartyID(),
                        party.getDisplay(),
                        McMMOParties.getConfigManager().getServerName(),
                        message
                ));
                continue;
            }
            remoteRecipients.add(memberId);
        }

        if (remoteRecipients.isEmpty()) {
            return;
        }

        sender.sendPluginMessage(
                McMMOParties.getInstance(),
                getChannelName(),
                encodePayload(
                        sender.getUniqueId(),
                        sender.getName(),
                        party.getPartyID(),
                        party.getDisplay(),
                        McMMOParties.getConfigManager().getServerName(),
                        message,
                        remoteRecipients
                )
        );
    }

    public static void handleIncoming(byte[] bytes) {
        DecodedChatPayload payload = decodePayload(bytes);
        if (payload == null) {
            return;
        }

        String formatted = formatMessage(
                payload.senderName(),
                payload.partyId(),
                payload.partyDisplay(),
                payload.serverName(),
                payload.message()
        );

        for (UUID recipientId : payload.recipientIds()) {
            Player recipient = Bukkit.getPlayer(recipientId);
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage(formatted);
            }
        }
    }

    private static String formatMessage(String senderName, String partyId, String partyDisplay, String serverName, String message) {
        return LanguageConfig.get().getMessage(
                Lang.PARTY_CHAT_FORMAT,
                new Replaceable("%player%", senderName),
                new Replaceable("%party_id%", partyId),
                new Replaceable("%party_display%", partyDisplay),
                new Replaceable("%server%", serverName),
                new Replaceable("%message%", message)
        );
    }

    private static byte[] encodePayload(UUID senderId, String senderName, String partyId, String partyDisplay, String serverName, String message, List<UUID> recipientIds) {
        String recipients = recipientIds.stream()
                .map(UUID::toString)
                .reduce((first, second) -> first + ";" + second)
                .orElse("");

        String payload = String.join(",",
                "chat",
                senderId.toString(),
                encode(senderName),
                encode(partyId),
                encode(partyDisplay),
                encode(serverName),
                encode(message),
                recipients
        );
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static DecodedChatPayload decodePayload(byte[] bytes) {
        String[] message = new String(bytes, StandardCharsets.UTF_8).split(",", 8);
        if (message.length < 8 || !"chat".equalsIgnoreCase(message[0])) {
            return null;
        }

        try {
            UUID senderId = UUID.fromString(message[1]);
            List<UUID> recipients = new ArrayList<>();
            if (!message[7].isBlank()) {
                for (String entry : message[7].split(";")) {
                    if (!entry.isBlank()) {
                        recipients.add(UUID.fromString(entry));
                    }
                }
            }

            return new DecodedChatPayload(
                    senderId,
                    decode(message[2]),
                    decode(message[3]),
                    decode(message[4]),
                    decode(message[5]),
                    decode(message[6]),
                    recipients
            );
        } catch (IllegalArgumentException ex) {
            McMMOParties.getInstance().getLogger().warning("Invalid incoming party chat payload: " + ex.getMessage());
            return null;
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private record DecodedChatPayload(
            UUID senderId,
            String senderName,
            String partyId,
            String partyDisplay,
            String serverName,
            String message,
            List<UUID> recipientIds
    ) {
    }
}
