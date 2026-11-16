package net.maksy.mcmmoparties.messaging;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class SignedMessageCodecTest {
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] KEY = new byte[32];
    private static final String CHANNEL = "mcmmoparties:partychat";
    private SignedMessageCodec codec() { return new SignedMessageCodec(CLOCK); }

    @Test public void decodesIndependentPythonHmacFixture() {
        // Created with Python struct.pack('>IBq', magic, version, epochMillis), UTF lengths and hmac.sha256.
        byte[] packet = Base64.getDecoder().decode("TU1QMQEAAAGheBTuAAABAgMEBQYHCAkKCwwNDg8ACUJhdXdlbHRlbgAFcHJveHkAFm1jbW1vcGFydGllczpwYXJ0eWNoYXQAAAAFaGVsbG8PCckFWbmK3wAx/eDWlojJXfmMHhZ1CbyevnprlkL6GQ==");
        assertEquals("hello", codec().decode(KEY, "Bauwelten", "proxy", CHANNEL, packet));
    }

    @Test public void roundTripBothDirectionsAndAllChannels() {
        for (String channel : List.of("mcmmo", "bukkitmsg", "chatevent", "eventtp", "proxycommand", "tpquery", "teleport")) {
            String id = "mcmmoparties:" + channel;
            byte[] packet = codec().encode(KEY, "Bauwelten", "proxy", id, "Hello, Grüße 🌍");
            assertEquals("Hello, Grüße 🌍", codec().decode(KEY, "Bauwelten", "proxy", id, packet));
            packet = codec().encode(KEY, "proxy", "Bauwelten", id, "reply");
            assertEquals("reply", codec().decode(KEY, "proxy", "Bauwelten", id, packet));
        }
    }

    @Test public void rejectsTamperingWrongKeyAndRouteChanges() {
        byte[] packet = codec().encode(KEY, "Bauwelten", "proxy", CHANNEL, "hello");
        byte[] changed = packet.clone(); changed[changed.length - 33] ^= 1;
        reject(() -> codec().decode(KEY, "Bauwelten", "proxy", CHANNEL, changed));
        byte[] other = new byte[32]; other[0] = 1;
        reject(() -> codec().decode(other, "Bauwelten", "proxy", CHANNEL, packet));
        reject(() -> codec().decode(KEY, "Survival", "proxy", CHANNEL, packet));
        reject(() -> codec().decode(KEY, "Bauwelten", "Survival", CHANNEL, packet));
        reject(() -> codec().decode(KEY, "Bauwelten", "proxy", "mcmmoparties:proxycommand", packet));
        reject(() -> codec().decode(KEY, "proxy", "Bauwelten", CHANNEL, packet));
    }

    @Test public void rejectsReplayAndAllowsFreshMessageWithSamePayload() {
        SignedMessageCodec receiver = codec();
        byte[] packet = codec().encode(KEY, "Bauwelten", "proxy", CHANNEL, "hello");
        receiver.decode(KEY, "Bauwelten", "proxy", CHANNEL, packet);
        reject(() -> receiver.decode(KEY, "Bauwelten", "proxy", CHANNEL, packet));
        receiver.decode(KEY, "Bauwelten", "proxy", CHANNEL, codec().encode(KEY, "Bauwelten", "proxy", CHANNEL, "hello"));
    }

    @Test public void rejectsExpiredFutureUnsignedTruncatedAndOversizedPackets() {
        for (int offset : List.of(-61, 6)) {
            SignedMessageCodec sender = new SignedMessageCodec(Clock.fixed(NOW.plusSeconds(offset), ZoneOffset.UTC));
            byte[] packet = sender.encode(KEY, "Bauwelten", "proxy", CHANNEL, "hello");
            reject(() -> codec().decode(KEY, "Bauwelten", "proxy", CHANNEL, packet));
        }
        reject(() -> codec().decode(KEY, "Bauwelten", "proxy", CHANNEL, "kick someone".getBytes()));
        reject(() -> codec().decode(KEY, "Bauwelten", "proxy", CHANNEL, new byte[30001]));
        byte[] valid = codec().encode(KEY, "Bauwelten", "proxy", CHANNEL, "hello");
        for (int i = 0; i < valid.length; i++) {
            byte[] truncated = Arrays.copyOf(valid, i);
            reject(() -> codec().decode(KEY, "Bauwelten", "proxy", CHANNEL, truncated));
        }
        reject(() -> codec().encode(KEY, "Bauwelten", "proxy", CHANNEL, "x".repeat(30000)));
    }

    @Test public void rejectsWeakKeysAndInvalidUtf8() {
        reject(() -> codec().encode(new byte[31], "Bauwelten", "proxy", CHANNEL, "hello"));
        reject(() -> SignedMessageCodec.utf8(new byte[]{(byte) 0xc3, 0x28}));
    }

    private static void reject(Runnable action) {
        try { action.run(); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}
