package net.maksy.mcmmoparties.messaging;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded canonical decoding before either platform performs actions. */
public final class Payload {
    private Payload() {}
    public record Teleport(UUID player, String server, String world, double x, double y, double z, float yaw, float pitch) {}
    public record Chat(UUID sender, String name, String party, String display, String server, String text, List<UUID> recipients) {
        public Chat { recipients = List.copyOf(recipients); }
    }
    public static UUID uuid(String value) {
        try { UUID uuid = UUID.fromString(value); if (!uuid.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return uuid; }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("uuid"); }
    }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max
                || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("text");
        return value;
    }
    private static double number(String value, double max) {
        try { double n = Double.parseDouble(value); if (!Double.isFinite(n) || Math.abs(n) > max) throw new IllegalArgumentException(); return n; }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("number"); }
    }
    public static Teleport teleport(String payload) {
        String[] fields = payload.split(",", -1);
        if (fields.length != 9 || !fields[0].equals("tp")) throw new IllegalArgumentException("teleport-shape");
        return new Teleport(uuid(fields[1]), text(fields[2],64), text(fields[3],128), number(fields[4],30000000),
                number(fields[5],20000000), number(fields[6],30000000), (float)number(fields[7],1000000), (float)number(fields[8],90));
    }
    private static String decode(String value, int max) {
        if (value.length() > max * 2) throw new IllegalArgumentException("text-size");
        try { return text(SignedMessageCodec.utf8(Base64.getDecoder().decode(value)), max); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("chat-field"); }
    }
    public static Chat chat(String payload) {
        String[] fields = payload.split(",",-1);
        if (fields.length != 8 || !fields[0].equals("chat")) throw new IllegalArgumentException("chat-shape");
        String[] ids = fields[7].split(";",-1);
        if (ids.length > 256 || fields[7].isBlank()) throw new IllegalArgumentException("recipients");
        Set<UUID> recipients = new LinkedHashSet<>();
        for (String id : ids) if (!recipients.add(uuid(id))) throw new IllegalArgumentException("duplicate-recipient");
        return new Chat(uuid(fields[1]), decode(fields[2],64), decode(fields[3],128), decode(fields[4],512),
                decode(fields[5],64), decode(fields[6],4096), List.copyOf(recipients));
    }
    public static String encode(Chat chat) {
        String recipients = chat.recipients.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(";"));
        return String.join(",", "chat", chat.sender.toString(), b64(chat.name), b64(chat.party), b64(chat.display), b64(chat.server), b64(chat.text), recipients);
    }
    private static String b64(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
