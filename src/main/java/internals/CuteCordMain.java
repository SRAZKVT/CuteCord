package internals;

public class CuteCordMain {
    public static void main(String[] args) {
        System.out.println("INFO: Setting up...");
        //if (!CuteCord.loadModules()) System.exit(1);
        CuteCord.setAuthToken(CuteCord.getConfig().get("auth_token"));
        CuteCord.start();
        System.out.println("INFO: All modules have been initialized, and the bot has successfully connected to the Discord API.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("INFO: Shutting down...");
            CuteCord.stop();
            System.out.println("INFO: All modules have been stopped, and the bot has been disconnected from Discord API.");
        }));

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
    }
}