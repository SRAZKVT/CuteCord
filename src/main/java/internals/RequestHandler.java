package internals;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.Arrays;

/**
 * This class is used to send requests to the Discord API.
 *
 */
public class RequestHandler {
    private static RequestHandler instance;
    private final CloseableHttpClient httpClient;
    private RequestHandler() {
        httpClient = HttpClients.createDefault();
    }

    protected static RequestHandler getInstance() {
        if (instance == null) instance = new RequestHandler();
        return instance;
    }

    protected HttpResponse sendRequest(HttpUriRequest request) {
        request.addHeader("user-agent", String.format("DiscordBot (%s, %s)", "https://github.com/SRAZKVT/CuteCord", "@VERSION@"));
        request.addHeader("authorization", String.format("Bot %s", CuteCord.AUTH_TOKEN));
        HttpResponse response;
        try {
            response = this.httpClient.execute(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    protected void close() {
        try {
            this.httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
