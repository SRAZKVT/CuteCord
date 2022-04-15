package internals;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DiscordWebSocketHandler extends WebSocketClient {
    private static final String EVENT_DATA = "d";
    private static final String OP_CODE = "op";
    private static final String EVENT_NAME = "t";
    private static final String SEQUENCE_NUMBER = "s";

    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_PRESENCE_UPDATE = 3;
    private static final int OP_VOICE_STATE_UPDATE = 4;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_REQUEST_GUILD_MEMBERS = 8;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    private long heartbeatInterval;
    private int lastSequenceNumber;
    private long lastHeartbeatAck;

    private Timer heartbeatTimer;

    /**
     * This list contains the timestamp of all messages sent through the websocket in the last 60 seconds.
     */
    private final List<Long> timeStampPreviousMessages = new java.util.ArrayList<>();

    public DiscordWebSocketHandler(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.printf("Connected to Discord WebSocket%n");
    }

    /**
     * Handles incoming messages from the Discord gateway.
     * This method then redirects the message to the correct handler, depending on the OP_CODE.
     *
     * @param message The UTF-8 decoded message that was received from the Discord gateway.
     */
    @Override
    public void onMessage(String message) {
        JSONObject receivedMessage = new JSONObject(message);
        if (receivedMessage.getInt(OP_CODE) == 0 && receivedMessage.has(SEQUENCE_NUMBER)) lastSequenceNumber = receivedMessage.getInt(SEQUENCE_NUMBER);
        if (receivedMessage.has(OP_CODE)) {
            int op_code = receivedMessage.getInt(OP_CODE);
            switch (op_code) {
                case OP_DISPATCH      -> {
                    // TODO: This is where most events people care about (like MESSAGE_CREATE) are. Need to create a handler for all those events. This handler shouldn't be in this method, but in its own class, which would just be called from here.
                }

                case OP_HEARTBEAT     -> {
                    JSONObject heartbeat = new JSONObject();
                    heartbeat.put(OP_CODE, 1);
                    if (receivedMessage.has(SEQUENCE_NUMBER)) {
                        heartbeat.put("d", lastSequenceNumber);
                    }
                    send(heartbeat.toString());
                }

                case OP_RECONNECT, OP_INVALID_SESSION -> reconnectToGateway();

                case OP_HELLO         -> {
                    lastHeartbeatAck = System.currentTimeMillis();
                    heartbeatInterval = receivedMessage.getJSONObject("d").getLong("heartbeat_interval");
                    startHeartbeat();
                    sendIdentify();
                }

                case OP_HEARTBEAT_ACK -> lastHeartbeatAck = System.currentTimeMillis();

                default -> throw new IllegalStateException(String.format("Unreachable op code: %d", op_code));
            }
        }
    }

    private void reconnectToGateway() {
        try {
            reconnectBlocking();
            // TODO: Implement RESUME of session with gateway, or IDENTIFY if RESUME is not possible.
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends an identify packet to the Discord WebSocket.
     * This packet contains information about the system the bot runs on, as well as the intents that are needed,
     * if compression is enabled, if the bot is sharded, etc.
     */
    private void sendIdentify() {
        // TODO: Implement support for sharding.
        // TODO: Implement support for compress.
        // TODO: Implement support for large_threshold.
        // TODO: Implement support for presence.
        JSONObject identifyMessage = new JSONObject().
                put(OP_CODE, OP_IDENTIFY).
                put("d", new JSONObject().
                        put("token", CuteCord.AUTH_TOKEN).
                        put("properties", new JSONObject().
                                put("os", System.getProperties().get("os.name")).
                                put("browser", "CuteCord").
                                put("device", "CuteCord")
                        ).put("intents", 65533)); // TODO: Implement intents based on what modules need, and throw error in case bot doesn't have permissions.
        send(identifyMessage.toString());
    }

    /**
     * Starts the heartbeat timer, which allows the connection to the gateways to be kept alive.
     * if there are two heartbeats in a raw that aren't acknowledged, the connection will be closed, restarted, and the session resumed.
     */
    private void startHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        heartbeatTimer = new Timer();
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - lastHeartbeatAck > heartbeatInterval * 1.5) {
                    reconnectToGateway();
                }
                send(new JSONObject().put(OP_CODE, OP_HEARTBEAT).put(EVENT_DATA, lastSequenceNumber).toString());
            }
        }, System.currentTimeMillis() % heartbeatInterval, heartbeatInterval);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // TODO: Handle different gateway close codes.
        System.out.printf("Disconnected from Discord WebSocket: %d - %s%n", code, reason);
    }

    /**
     * Sends a message to the Discord WebSocket. Also enforces the rate limit, which is 120 messages per 60 seconds.
     * @param message The string which will be transmitted to the Discord API.
     */
    @Override
    public void send(String message) {
        long currentTime = System.currentTimeMillis();
        timeStampPreviousMessages.removeIf(timeStamp -> currentTime - timeStamp > 60000);
        if (timeStampPreviousMessages.size() >= 120) {
            long oldestTimeStamp = timeStampPreviousMessages.get(0);
            try {
                Thread.sleep(60000 - (currentTime - oldestTimeStamp));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        super.send(message);
        timeStampPreviousMessages.add(currentTime);
    }

    @Override
    public void onError(Exception ex) {
        // TODO: Handle properly exceptions.
        ex.printStackTrace();
    }
}
