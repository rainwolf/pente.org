package org.pente.gameServer.mobile.test;

import junit.framework.*;
import org.pente.gameServer.client.web.WhosOnlineRoom;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.ServerData;
import org.pente.gameServer.mobile.LiveServersResponse;
import org.pente.gameServer.mobile.WhosonlineAndLiveResponse;

import java.util.*;
import java.util.stream.Collectors;

/** The mobile server/room lists must not show the Experimental server. */
public class MobileServerListFilterTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{MobileServerListFilterTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(MobileServerListFilterTest.class);
    }

    public MobileServerListFilterTest(String name) {
        super(name);
    }

    private static ServerData server(int id, String name) {
        ServerData d = new ServerData();
        d.setServerId(id);
        d.setName(name);
        return d;
    }

    public void testLiveServersSkipsExperimental() {
        List<LiveServersResponse.ServerEntry> out = LiveServersResponse.build(
            Arrays.asList(server(1, "Main Room"), server(47, "Experimental"), server(0, "Arena Server")),
            Collections.<WhosOnlineRoom>emptyList());
        assertEquals(Arrays.asList("Main Room", "Arena Server"),
            out.stream().map(e -> e.name).collect(Collectors.toList()));
    }

    public void testLiveServersKeepsAForeignRowAtId47() {
        List<LiveServersResponse.ServerEntry> out = LiveServersResponse.build(
            Arrays.asList(server(47, "Someone else's room")), Collections.<WhosOnlineRoom>emptyList());
        assertEquals(1, out.size());
    }

    public void testWhosOnlineSkipsTheExperimentalRoom() {
        List<WhosonlineAndLiveResponse.RoomEntry> out = WhosonlineAndLiveResponse.build(Arrays.asList(
            new WhosOnlineRoom("Main Room", new ArrayList<DSGPlayerData>()),
            new WhosOnlineRoom("Experimental", new ArrayList<DSGPlayerData>()),
            new WhosOnlineRoom("Website", new ArrayList<DSGPlayerData>())));
        assertEquals(Arrays.asList("Main Room", "Website"),
            out.stream().map(r -> r.name).collect(Collectors.toList()));
    }
}
