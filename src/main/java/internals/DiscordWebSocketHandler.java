package internals;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordWebSocketHandler extends WebSocketClient {
    private static final String EVENT_DATA = "d";
    private static final String OP_CODE = "op";
    private static final String EVENT_NAME = "t";
    private static final String SEQUENCE_NUMBER = "s";

    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    private long heartbeatInterval;
    private int lastSequenceNumber;
    private long lastHeartbeatAck;

    private final ExecutorService heartbeat = Executors.newFixedThreadPool(1);
    public DiscordWebSocketHandler(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.printf("Connected to Discord WebSocket%n");
    }

    @Override
    public void onMessage(String message) {
        JSONObject receivedMessage = new JSONObject(message);
        if (receivedMessage.has(OP_CODE) &&
                receivedMessage.getInt(OP_CODE) == 0 &&
                receivedMessage.has(SEQUENCE_NUMBER)) lastSequenceNumber = receivedMessage.getInt(SEQUENCE_NUMBER);
        if (receivedMessage.has(OP_CODE)) {
            if (receivedMessage.getInt(OP_CODE) != OP_HEARTBEAT_ACK) System.out.printf("Received message: %s%n", message);
            int op = receivedMessage.getInt(OP_CODE);
            switch (op) {
                case OP_HELLO:
                    lastHeartbeatAck = System.currentTimeMillis();
                    heartbeatInterval = receivedMessage.getJSONObject("d").getLong("heartbeat_interval");
                    System.out.printf("Heartbeat interval is of: %d%n", heartbeatInterval);
                    startHeartbeat();
                    sendIdentify();
                    break;

                case OP_HEARTBEAT:
                    JSONObject heartbeat = new JSONObject();
                    heartbeat.put(OP_CODE, 1);
                    if (receivedMessage.has(SEQUENCE_NUMBER)) {
                        heartbeat.put("d", lastSequenceNumber);
                    }
                    send(heartbeat.toString());
                    System.out.println(heartbeat);
                    break;

                case OP_HEARTBEAT_ACK:
                    lastHeartbeatAck = System.currentTimeMillis();
                    break;

                    // TODO: Handle other op codes
            }
        }
    }

    private void sendIdentify() {
        JSONObject identifyMessage = new JSONObject().
                put(OP_CODE, OP_IDENTIFY).
                put("d", new JSONObject().
                        put("token", CuteCord.AUTH_TOKEN).
                        put("properties", new JSONObject().
                                put("$os", System.getProperties().get("os.name")).
                                put("$browser", "CuteCord").
                                put("$device", "CuteCord")
                        ).put("intents", 1));
        send(identifyMessage.toString());
    }

    private void startHeartbeat() {
        Runnable heartbeatTask = () -> {
            try {
                Thread.sleep(new Random().nextLong(heartbeatInterval));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            while (DiscordWebSocketHandler.this.isOpen()) {
                if(System.currentTimeMillis() - lastHeartbeatAck > heartbeatInterval * 2) {
                    try {
                        DiscordWebSocketHandler.this.close(CloseFrame.SERVICE_RESTART);
                        DiscordWebSocketHandler.this.reconnectBlocking();
                        // TODO: Resume session
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                String heartbeatMessage = new JSONObject().
                        put("op", 1).
                        put("d", lastSequenceNumber).
                        toString();
                send(heartbeatMessage);
                try {
                    Thread.sleep(heartbeatInterval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        this.heartbeat.execute(heartbeatTask);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.printf("Disconnected from Discord WebSocket: %d - %s%n", code, reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}
