package net.maksy.mcmmoparties.messaging;

import dev.dejvokep.boostedyaml.YamlDocument;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Direction-bound per-node authentication. Missing/invalid keys always reject; no plaintext fallback. */
public final class SecureMessaging {
    public static final String TELEPORT = "mcmmoparties:teleport";
    public static final String CHAT = "mcmmoparties:partychat";
    public record Peer(byte[] secret, Set<String> channels) {
        public Peer { secret = secret.clone(); channels = Set.copyOf(channels); }
        @Override public byte[] secret() { return secret.clone(); }
    }
    private final String node;
    private final Map<String, Peer> peers;
    private final SignedMessageCodec codec = new SignedMessageCodec();
    private final Consumer<String> logger;
    private long lastWarning;
    public SecureMessaging(String node, Map<String, Peer> peers, Consumer<String> logger) {
        if (node == null || node.isBlank() || node.length() > 64 || node.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("node-name");
        this.node = node; this.peers = Map.copyOf(peers); this.logger = logger;
    }
    public static SecureMessaging load(Path folder, String node, Consumer<String> logger) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve("InternalMessaging.yml");
        if (!Files.exists(file)) try (InputStream defaults = SecureMessaging.class.getResourceAsStream("/InternalMessaging.yml")) {
            if (defaults == null) throw new IOException("Missing InternalMessaging.yml");
            Files.copy(defaults, file);
        }
        Map<String, Peer> peers = new HashMap<>();
        try (InputStream stream = Files.newInputStream(file)) {
            YamlDocument yaml = YamlDocument.create(stream);
            if (node.equals("proxy")) {
                var servers = yaml.getSection("servers");
                if (servers != null) for (Object key : servers.getKeys()) {
                    String name = key.toString();
                    if (name.equals("proxy") || name.contains(".")) throw new IllegalArgumentException("server-name");
                    peers.put(name, peer(yaml, "servers." + name));
                }
            } else peers.put("proxy", peer(yaml, "proxy"));
        }
        if (peers.values().stream().noneMatch(peer -> peer.secret.length >= 32))
            logger.accept("Internal cross-server messaging disabled: configure matching per-node keys in InternalMessaging.yml.");
        return new SecureMessaging(node, peers, logger);
    }
    private static Peer peer(YamlDocument yaml, String path) {
        byte[] key = new byte[0];
        try { key = Base64.getDecoder().decode(yaml.getString(path + ".secret", "")); }
        catch (IllegalArgumentException ignored) { /* Invalid key stays disabled, without logging its value. */ }
        return new Peer(key, new HashSet<>(yaml.getStringList(path + ".channels")));
    }
    private Peer peer(String name, String channel) {
        if (!Set.of(TELEPORT, CHAT).contains(channel)) throw new IllegalArgumentException("channel");
        Peer peer = peers.get(name);
        if (peer == null || peer.secret.length < 32) throw new IllegalArgumentException("missing-key");
        if (!peer.channels.contains(channel)) throw new IllegalArgumentException("channel-denied");
        return peer;
    }
    public boolean hasConfiguredChannel(String name, String channel) {
        Peer peer = peers.get(name);
        return (TELEPORT.equals(channel) || CHAT.equals(channel))
                && peer != null && peer.secret.length >= 32 && peer.channels.contains(channel);
    }

    public byte[] encode(String destination, String channel, String payload) {
        return codec.encode(peer(destination, channel).secret, node, destination, channel, payload);
    }
    public String decode(String sender, String channel, byte[] packet) {
        return codec.decode(peer(sender, channel).secret, sender, node, channel, packet);
    }
    public synchronized void reject(String channel, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastWarning < 10000) return;
        lastWarning = now;
        String safe = reason != null && reason.matches("[a-z-]{1,64}") ? reason : "invalid-payload";
        logger.accept("Rejected internal plugin message: channel=" + channel + ", reason=" + safe);
    }
}
