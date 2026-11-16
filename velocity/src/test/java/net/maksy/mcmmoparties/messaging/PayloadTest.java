package net.maksy.mcmmoparties.messaging;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PayloadTest {
    private final UUID uuid=UUID.randomUUID();
    private String teleport(String x) { return "tp,"+uuid+",Bauwelt,world,"+x+",70,10,0,0"; }
    private void reject(Runnable action) { try { action.run();fail("Expected rejection"); } catch(IllegalArgumentException expected) {} }
    @Test public void validTeleportAndInvalidNumbersShapeOrUuid() {
        assertEquals(uuid,Payload.teleport(teleport("10")).player());
        for(String x:List.of("NaN","Infinity","30000001","invalid"))reject(()->Payload.teleport(teleport(x)));
        reject(()->Payload.teleport(teleport("10")+",extra"));
        reject(()->Payload.teleport("tp,invalid,Bauwelt,world,1,1,1,0,0"));
        reject(()->Payload.teleport("tp,"+uuid+",Bauwelt,world,1,1,1,0,91"));
    }
    @Test public void chatPreservesCommasUnicodeAndRecipients() {
        var original=new Payload.Chat(uuid,"Felix","party", "Party Name", "Bauwelt", "Hallo, Gruesse",List.of(UUID.randomUUID()));
        assertEquals(original,Payload.chat(Payload.encode(original)));
    }
    @Test public void rejectsInvalidOrDuplicateChatRecipientsAndOversizedMessage() {
        UUID recipient=UUID.randomUUID();
        reject(()->Payload.chat(Payload.encode(new Payload.Chat(uuid,"Felix","party","Name","Bauwelt","hi",List.of(recipient,recipient)))));
        reject(()->Payload.chat(Payload.encode(new Payload.Chat(uuid,"Felix","party","Name","Bauwelt","hi",List.of()))));
        reject(()->Payload.chat(Payload.encode(new Payload.Chat(uuid,"Felix","party","Name","Bauwelt","x".repeat(4097),List.of(recipient)))));
        reject(()->Payload.chat("chat,"+uuid+",%%%%,abc,abc,abc,abc,"+recipient));
    }
}
