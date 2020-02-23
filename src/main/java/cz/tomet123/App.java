package cz.tomet123;
import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.ServerLoginHandler;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.message.TextMessage;
import com.github.steveice10.mc.protocol.data.status.PlayerInfo;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.data.status.VersionInfo;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoBuilder;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoHandler;
import com.github.steveice10.mc.protocol.data.status.handler.ServerPingTimeHandler;
import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.packetlib.Client;
import com.github.steveice10.packetlib.Server;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.server.ServerAdapter;
import com.github.steveice10.packetlib.event.server.ServerClosedEvent;
import com.github.steveice10.packetlib.event.server.SessionAddedEvent;
import com.github.steveice10.packetlib.tcp.TcpSessionFactory;
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.Proxy;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class App {
    private static final boolean VERIFY_USERS = false;
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 25580;
    private static final Proxy PROXY = Proxy.NO_PROXY;
    private static final Proxy AUTH_PROXY = Proxy.NO_PROXY;
    private static BufferedImage image=null;

    public static final Counter requests = Counter.build().name("con_dummy_server").help("Dummy connections to server").register();

    public static void main(String[] args) {
      DefaultExports.initialize();
        try {
            HTTPServer server = new HTTPServer(8050);
        }catch (Exception e){
            e.printStackTrace();
        }
        final Server server = new Server(HOST, PORT, MinecraftProtocol.class, new TcpSessionFactory(PROXY));
            server.setGlobalFlag(MinecraftConstants.AUTH_PROXY_KEY, AUTH_PROXY);
            server.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, VERIFY_USERS);

            try
            {
            image = ImageIO.read(App.class.getResourceAsStream("/server-icon.png"));
            }catch (Exception e){
                e.printStackTrace();
                image=null;
            }

            server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, new ServerInfoBuilder() {
                @Override
                public ServerStatusInfo buildInfo(Session session,int ver) {
                    System.out.println(ver);
                    return new ServerStatusInfo(
                            new VersionInfo("ERROR", 0),
                            new PlayerInfo(-1, 0, new GameProfile[0]),
                            new TextMessage("§a▁▂▃§§▄▅ §c§lMinecore.cz §r§c[§e1.13§a-§b1.15§c] §6▅▄§a▃▂▁            "),
                            image
                    );
                }
            });

            server.setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, new ServerLoginHandler() {
                @Override
                public void loggedIn(Session session) {
                    DateFormat dateFormat = new SimpleDateFormat("ddMM");
                    Date date = new Date();
                    session.disconnect("§aDoslo k chybe cislo §cBD-"+dateFormat.format(date)+"§4. \n§aNapis na §6fb.com/minecore.cz §ado zprav, \ndostanes odmenu za nahlaseni bugu.");
                    requests.inc();
                }
            });

            server.setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 100);
            server.addListener(new ServerAdapter() {
                @Override
                public void serverClosed(ServerClosedEvent event) {
                    System.out.println("Server closed.");
                }

            });

            server.bind();
        status();
    }

    private static void status() {
        MinecraftProtocol protocol = new MinecraftProtocol(SubProtocol.STATUS);
        Client client = new Client(HOST, PORT, protocol, new TcpSessionFactory(PROXY));
        client.getSession().setFlag(MinecraftConstants.AUTH_PROXY_KEY, AUTH_PROXY);
        client.getSession().setFlag(MinecraftConstants.SERVER_INFO_HANDLER_KEY, new ServerInfoHandler() {
            @Override
            public void handle(Session session, ServerStatusInfo info) {
                System.out.println("Version: " + info.getVersionInfo().getVersionName() + ", " + info.getVersionInfo().getProtocolVersion());
                System.out.println("Player Count: " + info.getPlayerInfo().getOnlinePlayers() + " / " + info.getPlayerInfo().getMaxPlayers());
                System.out.println("Players: " + Arrays.toString(info.getPlayerInfo().getPlayers()));
                System.out.println("Description: " + info.getDescription().getFullText());
                System.out.println("Icon: " + info.getIcon());
            }
        });

        client.getSession().setFlag(MinecraftConstants.SERVER_PING_TIME_HANDLER_KEY, new ServerPingTimeHandler() {
            @Override
            public void handle(Session session, long pingTime) {
                System.out.println("Server ping took " + pingTime + "ms");
            }
        });

        client.getSession().connect();
        while(client.getSession().isConnected()) {
            try {
                Thread.sleep(5);
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}