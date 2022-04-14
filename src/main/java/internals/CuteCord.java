package internals;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class CuteCord {
    protected static String AUTH_TOKEN;
    private static final Map<String, String> CONFIG = new HashMap<>();

    private static DiscordWebSocketHandler handler;

    /**
     * The class loader used to load the modules.
     */
    protected static ClassLoader moduleLoader = null;

    static {
        try {
            moduleLoader = new URLClassLoader(new URL[]{new File("modules").toURI().toURL()});
        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * The current list of all modules, with name of module as key and module as value.
     */
    private static Map<String, Module> modules = new HashMap<>();

    /**
     * Load modules
     *
     * @return true if modules were loaded successfully, false otherwise
     */
    public static boolean loadModules() {
        File moduleFolder = new File("modules");
        if (!moduleFolder.exists() || !moduleFolder.isDirectory()) {
            System.err.printf("ERROR: The module folder `%s` is in an invalid state. (%s)%n",
                    moduleFolder.getAbsolutePath(),
                    moduleFolder.exists() ? "Not a directory" : "Does not exist");
            System.exit(1);
        }
        modules = loadModules(moduleFolder);
        if (modules.isEmpty()) {
            System.err.println("ERROR: No modules were found.");
            return false;
        }
        return true;
    }

    /**
     * Loads recursively the modules from the given folder.
     * @param moduleFile The base folder to load the modules from.
     * @return A map with all the modules loaded. As this is a recursive method, the map will contain modules present in specified folder and all its subfolders.
     */
    public static Map<String, Module> loadModules(File moduleFile) {
        Map<String, Module> currentModules = new HashMap<>();
        if (moduleFile.isDirectory()) {
            for (File file : Objects.requireNonNull(moduleFile.listFiles())) {
                currentModules.putAll(loadModules(file));
            }
        } else {
            if (moduleFile.getName().endsWith(".jar")) {
                System.out.println("INFO: Loading module: " + moduleFile.getName());
                Module module = new Module(moduleFile);
                String moduleName = module.getName();
                if (modules.containsKey(moduleName)) {
                    // TODO: Handle case of module collision to only load the latest version of a module
                    System.err.printf("ERROR: Module `%s` is already loaded.%n", moduleName);
                    System.exit(1);
                }
                currentModules.put(moduleName, module);
            }
        }
        return currentModules;
    }


    /**
     * Invoke each module's {@link Module#start()} method, after loading the classes, and checking if duplicate modules are present.
     * Connects to the Discord API and logs in the bot, using the auth key provided in the configuration file.
     * Stops the bot if the connection fails, or connection to the Discord API fails.
     */
    public static void start() {
        for (Module module : modules.values()) {
            module.load();
            module.start();
        }
        HttpUriRequest request = new HttpPost("https://discordapp.com/api/v9/auth/login");
        HttpResponse response = RequestHandler.getInstance().sendRequest(request);
        if (response.getStatusLine().getStatusCode() != 200) {
            System.err.println("ERROR: Failed to connect to Discord API.");
            System.exit(1);
        }
        // Connect to Discord API via gateway
        String gatewayUrl = "https://discordapp.com/api/v9/gateway";
        request = new HttpGet(gatewayUrl);
        response = RequestHandler.getInstance().sendRequest(request);
        if (response.getStatusLine().getStatusCode() != 200) {
            System.err.println("ERROR: Failed to connect to Discord API.");
            System.exit(1);
        }
        try {
            Scanner scanner = new Scanner(response.getEntity().getContent());
            StringBuilder webSocketUri = new StringBuilder();
            while (scanner.hasNextLine()) {
                webSocketUri.append(scanner.nextLine());
            }
            JSONObject json = new JSONObject(webSocketUri.toString());
            URI gatewayUri = URI.create(json.get("url").toString());
            handler = new DiscordWebSocketHandler(gatewayUri);
            handler.connectBlocking();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }


    }

    /**
     * Invoke each module's {@link Module#stop()} method.
     */
    public static void stop() {
        for (Module module : modules.values()) {
            module.stop();
        }
        RequestHandler.getInstance().close();
        try {
            handler.closeBlocking();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the configuration for the bot runtime, with fields such as auth_key, which is the key used to authenticate to the Discord API,
     * or bot_prefix, which is the prefix used to invoke commands in legacy command handler
     * @return A map containing as key the name of the field in the configuration file, and as value the value of said field.
     */
    protected static Map<String, String> getConfig() {
        if (CONFIG.isEmpty()) {
            File configFile = new File("config.txt");
            if (!configFile.exists()) {
                System.err.printf("ERROR: The config file (located at %s) does not exist.%n", configFile.getAbsolutePath());
                System.exit(1);
            } else if (!configFile.isFile()) {
                System.err.printf("ERROR: The config file (located at %s) is not a file.%n", configFile.getAbsolutePath());
                System.exit(1);
            } else if (!configFile.canRead()) {
                System.err.printf("ERROR: The config file (located at %s) is not readable.%n", configFile.getAbsolutePath());
                System.exit(1);
            }
            try {
                Scanner scanner = new Scanner(configFile);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] split = line.split(":", 2);
                    if (split.length != 2) {
                        System.err.printf("ERROR: The config file (located at %s) is in an invalid state.%n", configFile.getAbsolutePath());
                    }
                    CONFIG.put(split[0].trim(), split[1].trim());
                }
            } catch (FileNotFoundException e) {
                System.err.println("ERROR: The config file could not be found.");
                System.exit(1);
            }
            if (CONFIG.get("auth_token") == null) {
                System.err.println("ERROR: The config file does not contain a field named `auth_key`, otherwise the bot will be unable to log in to discord.");
                System.exit(1);
            }
        }
        return CONFIG;
    }

    /**
     * Sets the auth key for the bot.
     * @param auth_key The auth key for the bot to use.
     */
    protected static void setAuthToken(String auth_key) {
        AUTH_TOKEN = auth_key;
    }
}
