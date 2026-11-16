package net.maksy.mcmmoparties.messaging;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** Wire implementation is identical in Paper and Velocity. No platform dependencies. */
public final class SignedMessageCodec {
    public static final int MAX_PACKET = 30000;
    public static final long MAX_AGE_MS = 60000;
    private static final long FUTURE_SKEW_MS = 5000;
    private static final int MAGIC = 0x4d4d5031;
    private static final int SIGNATURE_BYTES = 32;
    private final Clock clock;
    private final Map<String, Long> seen = new HashMap<>();

    public SignedMessageCodec() { this(Clock.systemUTC()); }
    public SignedMessageCodec(Clock clock) { this.clock = clock; }

    public byte[] encode(byte[] key, String sender, String destination, String channel, String payload) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(MAGIC);
            out.writeByte(1);
            out.writeLong(clock.millis());
            UUID nonce = UUID.randomUUID();
            out.writeLong(nonce.getMostSignificantBits());
            out.writeLong(nonce.getLeastSignificantBits());
            out.writeUTF(sender);
            out.writeUTF(destination);
            out.writeUTF(channel);
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_PACKET - 512) throw new IllegalArgumentException("payload-size");
            out.writeInt(body.length);
            out.write(body);
            out.flush();
            byte[] unsigned = buffer.toByteArray();
            if (unsigned.length + SIGNATURE_BYTES > MAX_PACKET) throw new IllegalArgumentException("packet-size");
            out.write(mac(key, unsigned));
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("encoding", e);
        }
    }

    public synchronized String decode(byte[] key, String sender, String destination, String channel, byte[] packet) {
        if (packet == null || packet.length < 70 || packet.length > MAX_PACKET)
            throw new IllegalArgumentException("packet-size");
        byte[] unsigned = Arrays.copyOf(packet, packet.length - SIGNATURE_BYTES);
        if (!MessageDigest.isEqual(mac(key, unsigned), Arrays.copyOfRange(packet, unsigned.length, packet.length)))
            throw new IllegalArgumentException("signature");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(unsigned));
            if (in.readInt() != MAGIC || in.readUnsignedByte() != 1) throw new IllegalArgumentException("version");
            long issued = in.readLong();
            long now = clock.millis();
            if (issued < now - MAX_AGE_MS || issued > now + FUTURE_SKEW_MS)
                throw new IllegalArgumentException("timestamp");
            UUID nonce = new UUID(in.readLong(), in.readLong());
            if (!sender.equals(in.readUTF()) || !destination.equals(in.readUTF()) || !channel.equals(in.readUTF()))
                throw new IllegalArgumentException("route");
            int size = in.readInt();
            if (size < 0 || size != in.available()) throw new IllegalArgumentException("payload-size");
            String payload = utf8(in.readNBytes(size));
            seen.values().removeIf(expiry -> expiry < now);
            String replayKey = sender + ":" + nonce;
            if (seen.containsKey(replayKey)) throw new IllegalArgumentException("replay");
            // Fail closed instead of evicting live IDs and allowing replay under load.
            if (seen.size() >= 100000) throw new IllegalArgumentException("replay-capacity");
            seen.put(replayKey, issued + MAX_AGE_MS);
            return payload;
        } catch (IOException e) {
            throw new IllegalArgumentException("envelope", e);
        }
    }

    public static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("utf8", e);
        }
    }

    private static byte[] mac(byte[] key, byte[] bytes) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("key");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(bytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
