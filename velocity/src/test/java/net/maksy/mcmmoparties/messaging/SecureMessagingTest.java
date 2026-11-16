package net.maksy.mcmmoparties.messaging;
import org.junit.Test;
import java.util.*;
import java.nio.file.*;
import static org.junit.Assert.*;
public class SecureMessagingTest {
    private static final byte[] KEY=new byte[32];
    private SecureMessaging node(String name,String peer,Set<String> channels) {
        return new SecureMessaging(name,Map.of(peer,new SecureMessaging.Peer(KEY,channels)),ignored->{});
    }
    @Test public void authenticatesBothDirectionsRejectsReflectionReplayAndWrongChannel() {
        var paper=node("Bauwelt","proxy",Set.of(SecureMessaging.TELEPORT,SecureMessaging.CHAT));
        var proxy=node("proxy","Bauwelt",Set.of(SecureMessaging.TELEPORT,SecureMessaging.CHAT));
        byte[] packet=paper.encode("proxy",SecureMessaging.CHAT,"payload");
        assertEquals("payload",proxy.decode("Bauwelt",SecureMessaging.CHAT,packet));
        reject(()->proxy.decode("Bauwelt",SecureMessaging.CHAT,packet));
        reject(()->paper.decode("proxy",SecureMessaging.CHAT,packet));
        byte[] other=paper.encode("proxy",SecureMessaging.CHAT,"payload");
        reject(()->proxy.decode("Bauwelt",SecureMessaging.TELEPORT,other));
        assertEquals("reply",paper.decode("proxy",SecureMessaging.CHAT,proxy.encode("Bauwelt",SecureMessaging.CHAT,"reply")));
    }
    @Test public void deniesMissingKeysAndDisallowedChannels() {
        var missing=new SecureMessaging("proxy",Map.of(),ignored->{});
        reject(()->missing.encode("Bauwelt",SecureMessaging.CHAT,"hi"));
        var denied=node("proxy","Bauwelt",Set.of());reject(()->denied.encode("Bauwelt",SecureMessaging.CHAT,"hi"));
    }
    @Test public void generatesYamlWithoutUsableDefaultKeys() throws Exception {
        Path folder=Files.createTempDirectory("mcmmo-security");
        try {
            var security=SecureMessaging.load(folder,"proxy",ignored->{});
            assertTrue(Files.exists(folder.resolve("InternalMessaging.yml")));
            reject(()->security.encode("Bauwelt",SecureMessaging.CHAT,"hi"));
        } finally { Files.deleteIfExists(folder.resolve("InternalMessaging.yml"));Files.deleteIfExists(folder); }
    }
    @Test public void configuredYamlKeyEnablesOnlyExplicitChannel() throws Exception {
        Path folder=Files.createTempDirectory("mcmmo-security");
        try {
            Files.writeString(folder.resolve("InternalMessaging.yml"),"servers:\n  Bauwelt:\n    secret: \""+Base64.getEncoder().encodeToString(KEY)+"\"\n    channels: [mcmmoparties:teleport]\n");
            var security=SecureMessaging.load(folder,"proxy",ignored->{});
            assertNotNull(security.encode("Bauwelt",SecureMessaging.TELEPORT,"hi"));
            reject(()->security.encode("Bauwelt",SecureMessaging.CHAT,"hi"));
        } finally { Files.deleteIfExists(folder.resolve("InternalMessaging.yml"));Files.deleteIfExists(folder); }
    }
    private void reject(Runnable action) { try { action.run();fail("Expected rejection"); } catch(IllegalArgumentException expected) {} }
}
