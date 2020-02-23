package com.github.steveice10.mc.protocol;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.message.Message;
import com.github.steveice10.mc.protocol.data.status.PlayerInfo;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.data.status.VersionInfo;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoBuilder;
import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.login.client.EncryptionResponsePacket;
import com.github.steveice10.mc.protocol.packet.login.client.LoginStartPacket;
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSetCompressionPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket;
import com.github.steveice10.mc.protocol.packet.status.client.StatusPingPacket;
import com.github.steveice10.mc.protocol.packet.status.client.StatusQueryPacket;
import com.github.steveice10.mc.protocol.packet.status.server.StatusPongPacket;
import com.github.steveice10.mc.protocol.packet.status.server.StatusResponsePacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectingEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import cz.tomet123.App;

import javax.crypto.SecretKey;
import java.net.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

public class ServerListener extends SessionAdapter {
    private static final int DEFAULT_COMPRESSION_THRESHOLD = 256;

    // Always empty post-1.7
    private static final String SERVER_ID = "";
    private static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(1024);
            KEY_PAIR = gen.generateKeyPair();
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to generate server key pair.", e);
        }
    }

    private byte[] verifyToken = new byte[4];
    private String username = "";

    private long lastPingTime = 0;
    private int lastPingId = 0;

    public ServerListener() {
        new Random().nextBytes(this.verifyToken);
    }

    @Override
    public void connected(ConnectedEvent event) {
        event.getSession().setFlag(MinecraftConstants.PING_KEY, 0);
    }

    @Override
    public void packetReceived(PacketReceivedEvent event) {
        MinecraftProtocol protocol = (MinecraftProtocol) event.getSession().getPacketProtocol();
        int ver=-1;
        if(protocol.getSubProtocol() == SubProtocol.HANDSHAKE) {
            if(event.getPacket() instanceof HandshakePacket) {
                HandshakePacket packet = event.getPacket();
                switch(packet.getIntent()) {
                    case STATUS:
                        protocol.setSubProtocol(SubProtocol.STATUS, false, event.getSession());
                        ver=packet.getProtocolVersion();
                        break;
                    case LOGIN:
                        protocol.setSubProtocol(SubProtocol.LOGIN, false, event.getSession());
                        DateFormat dateFormat = new SimpleDateFormat("ddMM");
                        Date date = new Date();
                        event.getSession().disconnect("§aDoslo k chybe cislo §cBD-"+dateFormat.format(date)+"§4. \n§aNapis na §6fb.com/minecore.cz §ado zprav, \ndostanes odmenu za nahlaseni bugu.");
                        App.requests.inc();

                        break;
                    default:
                        throw new UnsupportedOperationException("Invalid client intent: " + packet.getIntent());
                }
            }
        }

        if(protocol.getSubProtocol() == SubProtocol.LOGIN) {
            if(event.getPacket() instanceof LoginStartPacket) {
                this.username = event.<LoginStartPacket>getPacket().getUsername();

                boolean verify = event.getSession().hasFlag(MinecraftConstants.VERIFY_USERS_KEY) ? event.getSession().<Boolean>getFlag(MinecraftConstants.VERIFY_USERS_KEY) : true;
                if(verify) {
                    event.getSession().send(new EncryptionRequestPacket(SERVER_ID, KEY_PAIR.getPublic(), this.verifyToken));
                } else {
                    new Thread(new UserAuthTask(event.getSession(), null)).start();
                }
            } else if(event.getPacket() instanceof EncryptionResponsePacket) {
                EncryptionResponsePacket packet = event.getPacket();
                PrivateKey privateKey = KEY_PAIR.getPrivate();
                if(!Arrays.equals(this.verifyToken, packet.getVerifyToken(privateKey))) {
                    event.getSession().disconnect("Invalid nonce!");
                    return;
                }

                SecretKey key = packet.getSecretKey(privateKey);
                protocol.enableEncryption(key);
                new Thread(new UserAuthTask(event.getSession(), key)).start();
            }
        }

        if(protocol.getSubProtocol() == SubProtocol.STATUS) {
            if(event.getPacket() instanceof StatusQueryPacket) {
                ServerInfoBuilder builder = event.getSession().getFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY);

                ServerStatusInfo info = builder.buildInfo(event.getSession(),ver);
                event.getSession().send(new StatusResponsePacket(info));
            } else if(event.getPacket() instanceof StatusPingPacket) {
                event.getSession().send(new StatusPongPacket(event.<StatusPingPacket>getPacket().getPingTime()));
            }
        }

        if(protocol.getSubProtocol() == SubProtocol.GAME) {
            if(event.getPacket() instanceof ClientKeepAlivePacket) {
                ClientKeepAlivePacket packet = event.getPacket();
                if(packet.getPingId() == this.lastPingId) {
                    long time = System.currentTimeMillis() - this.lastPingTime;
                    event.getSession().setFlag(MinecraftConstants.PING_KEY, time);
                }
            }
        }
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {
        MinecraftProtocol protocol = (MinecraftProtocol) event.getSession().getPacketProtocol();
        if(protocol.getSubProtocol() == SubProtocol.LOGIN) {
            event.getSession().send(new LoginDisconnectPacket(event.getReason()));
        } else if(protocol.getSubProtocol() == SubProtocol.GAME) {
            event.getSession().send(new ServerDisconnectPacket(event.getReason()));
        }
    }

    private class UserAuthTask implements Runnable {
        private Session session;
        private SecretKey key;

        public UserAuthTask(Session session, SecretKey key) {
            this.key = key;
            this.session = session;
        }

        @Override
        public void run() {
            boolean verify = this.session.hasFlag(MinecraftConstants.VERIFY_USERS_KEY) ? this.session.<Boolean>getFlag(MinecraftConstants.VERIFY_USERS_KEY) : true;

            GameProfile profile = null;
            if(verify && this.key != null) {
                Proxy proxy = this.session.<Proxy>getFlag(MinecraftConstants.AUTH_PROXY_KEY);
                if(proxy == null) {
                    proxy = Proxy.NO_PROXY;
                }

                SessionService sessionService = new SessionService(proxy);
                try {
                    profile = sessionService.getProfileByServer(username, sessionService.getServerId(SERVER_ID, KEY_PAIR.getPublic(), this.key));
                } catch(RequestException e) {
                    this.session.disconnect("Failed to make session service request.", e);
                    return;
                }

                if(profile == null) {
                    this.session.disconnect("Failed to verify username.");
                }
            } else {
                profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()), username);
            }

            int threshold;
            if(this.session.hasFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD)) {
                threshold = this.session.getFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD);
            } else {
                threshold = DEFAULT_COMPRESSION_THRESHOLD;
            }

            this.session.send(new LoginSetCompressionPacket(threshold));
            this.session.setCompressionThreshold(threshold);
            this.session.send(new LoginSuccessPacket(profile));
            this.session.setFlag(MinecraftConstants.PROFILE_KEY, profile);
            ((MinecraftProtocol) this.session.getPacketProtocol()).setSubProtocol(SubProtocol.GAME, false, this.session);
            ServerLoginHandler handler = this.session.getFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY);
            if(handler != null) {
                handler.loggedIn(this.session);
            }

            new Thread(new KeepAliveTask(this.session)).start();
        }
    }

    private class KeepAliveTask implements Runnable {
        private Session session;

        public KeepAliveTask(Session session) {
            this.session = session;
        }

        @Override
        public void run() {
            while(this.session.isConnected()) {
                lastPingTime = System.currentTimeMillis();
                lastPingId = (int) lastPingTime;
                this.session.send(new ServerKeepAlivePacket(lastPingId));

                try {
                    Thread.sleep(2000);
                } catch(InterruptedException e) {
                    break;
                }
            }
        }
    }
}