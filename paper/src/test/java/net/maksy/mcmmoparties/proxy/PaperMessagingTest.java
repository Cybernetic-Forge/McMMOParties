package net.maksy.mcmmoparties.proxy;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.ConfigManager;
import net.maksy.mcmmoparties.messaging.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.*;
import java.lang.reflect.*;
import java.util.*;
import static org.mockito.Mockito.*;

public class PaperMessagingTest {
    private final Map<Field,Object> previous=new HashMap<>();
    private static final byte[] KEY=new byte[32];
    private Player player;
    private SecureMessaging proxy;
    private void set(Class<?> type,String name,Object value) throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);previous.put(field,field.get(null));field.set(null,value);
    }
    @Before public void setup() throws Exception {
        ConfigManager config=mock(ConfigManager.class);when(config.getServerName()).thenReturn("Bauwelt");
        when(config.getTeleportChannel()).thenReturn(SecureMessaging.TELEPORT);when(config.getPartyChatChannel()).thenReturn(SecureMessaging.CHAT);
        set(McMMOParties.class,"configManager",config);
        var peer=new SecureMessaging.Peer(KEY,Set.of(SecureMessaging.TELEPORT,SecureMessaging.CHAT));
        set(PaperMessaging.class,"messaging",new SecureMessaging("Bauwelt",Map.of("proxy",peer),ignored->{}));
        proxy=new SecureMessaging("proxy",Map.of("Bauwelt",peer),ignored->{});
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var server=mock(Server.class);when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        var world=mock(World.class);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(server.getWorld("world")).thenReturn(world);
        set(Bukkit.class,"server",server);
    }
    @After public void restore() throws Exception { for(var entry:previous.entrySet())entry.getKey().set(null,entry.getValue()); }
    private String teleport(String server) { return "tp,"+player.getUniqueId()+","+server+",world,10,70,10,0,0"; }
    @Test public void clientPlaintextCannotTeleportAnyPlayer() {
        new ProxyTeleportListener().onPluginMessageReceived(SecureMessaging.TELEPORT,player,teleport("Bauwelt").getBytes());
        verify(player,never()).teleport(any(Location.class));
    }
    @Test public void proxySignedTeleportWorksOnceAndReplayDoesNothing() {
        byte[] packet=proxy.encode("Bauwelt",SecureMessaging.TELEPORT,teleport("Bauwelt"));
        var listener=new ProxyTeleportListener();listener.onPluginMessageReceived(SecureMessaging.TELEPORT,player,packet);
        listener.onPluginMessageReceived(SecureMessaging.TELEPORT,player,packet);
        verify(player,times(1)).teleport(any(Location.class));
    }
    @Test public void signedWrongDestinationAndTamperedPacketCannotTeleport() {
        var listener=new ProxyTeleportListener();
        listener.onPluginMessageReceived(SecureMessaging.TELEPORT,player,proxy.encode("Bauwelt",SecureMessaging.TELEPORT,teleport("Farmwelt")));
        byte[] tampered=proxy.encode("Bauwelt",SecureMessaging.TELEPORT,teleport("Bauwelt"));tampered[tampered.length-1]^=1;
        listener.onPluginMessageReceived(SecureMessaging.TELEPORT,player,tampered);
        verify(player,never()).teleport(any(Location.class));
    }
    @Test public void plaintextPartyChatCannotReachRecipients() {
        new ProxyPartyChatListener().onPluginMessageReceived(SecureMessaging.CHAT,player,("chat,"+player.getUniqueId()+",ignored").getBytes());
        verify(player,never()).sendMessage(anyString());
    }
}
