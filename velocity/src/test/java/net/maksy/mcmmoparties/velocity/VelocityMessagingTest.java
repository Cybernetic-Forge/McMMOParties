package net.maksy.mcmmoparties.velocity;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.maksy.mcmmoparties.messaging.*;
import org.junit.*;
import java.net.InetSocketAddress;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class VelocityMessagingTest {
    private static final byte[] KEY=new byte[32];
    private ProxyServer proxy; private Player player; private ServerConnection connection;
    private VelocityMessaging messaging; private SecureMessaging source;
    @Before public void setup() {
        proxy=mock(ProxyServer.class);player=mock(Player.class);connection=mock(ServerConnection.class);
        ServerInfo info=new ServerInfo("Bauwelt",new InetSocketAddress("127.0.0.1",25565));
        when(connection.getServerInfo()).thenReturn(info);when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getUsername()).thenReturn("Felix");
        when(proxy.getPlayer(player.getUniqueId())).thenReturn(Optional.of(player));
        var peer=new SecureMessaging.Peer(KEY,Set.of(SecureMessaging.TELEPORT,SecureMessaging.CHAT));
        messaging=new VelocityMessaging(proxy,new SecureMessaging("proxy",Map.of("Bauwelt",peer),ignored->{}));
        source=new SecureMessaging("Bauwelt",Map.of("proxy",peer),ignored->{});
    }
    private PluginMessageEvent event(boolean client,String channel,byte[] bytes) {
        return new PluginMessageEvent(client?player:connection,player,MinecraftChannelIdentifier.from(channel),bytes);
    }
    private String teleport(UUID subject) { return "tp,"+subject+",Farmwelt,world,10,70,10,0,0"; }
    @Test public void clientInjectionIsHandledWithoutUserLookup() {
        var event=event(true,SecureMessaging.TELEPORT,teleport(player.getUniqueId()).getBytes());
        assertNull(messaging.receive(event));assertFalse(event.getResult().isAllowed());verify(proxy,never()).getPlayer(any(UUID.class));
    }
    @Test public void unsignedBackendMessageIsRejected() {
        var event=event(false,SecureMessaging.TELEPORT,teleport(player.getUniqueId()).getBytes());
        assertNull(messaging.receive(event));assertFalse(event.getResult().isAllowed());
    }
    @Test public void validBackendTeleportAcceptedButReplayRejected() {
        String payload=teleport(player.getUniqueId());byte[] bytes=source.encode("proxy",SecureMessaging.TELEPORT,payload);
        assertEquals(payload,messaging.receive(event(false,SecureMessaging.TELEPORT,bytes)));
        assertNull(messaging.receive(event(false,SecureMessaging.TELEPORT,bytes)));
    }
    @Test public void subjectMustBeOnOriginBackend() {
        when(player.getCurrentServer()).thenReturn(Optional.empty());
        assertNull(messaging.receive(event(false,SecureMessaging.TELEPORT,source.encode("proxy",SecureMessaging.TELEPORT,teleport(player.getUniqueId())))));
    }
    @Test public void chatNameAndOriginMustMatchActualPlayerAndBackend() {
        var recipients=List.of(UUID.randomUUID());
        String valid=Payload.encode(new Payload.Chat(player.getUniqueId(),"Felix","party","Party","Bauwelt","hi",recipients));
        assertEquals(valid,messaging.receive(event(false,SecureMessaging.CHAT,source.encode("proxy",SecureMessaging.CHAT,valid))));
        for(var chat:List.of(new Payload.Chat(player.getUniqueId(),"Admin","party","Party","Bauwelt","hi",recipients),
                            new Payload.Chat(player.getUniqueId(),"Felix","party","Party","Farmwelt","hi",recipients)))
            assertNull(messaging.receive(event(false,SecureMessaging.CHAT,source.encode("proxy",SecureMessaging.CHAT,Payload.encode(chat)))));
    }
}
