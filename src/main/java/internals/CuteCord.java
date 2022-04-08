package internals;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CuteCord {

    /**
     * The class loader used to load the modules.
     */
    protected static ClassLoader moduleLoader = null;

    static {
        try {
            moduleLoader = new URLClassLoader(new URL[]{new File("modules").toURI().toURL()});
        } catch (MalformedURLException e) {
            e.printStackTrace();
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

    public static Map<String, Module> loadModules(File moduleFile) {
        Map<String, Module> currentModules = new HashMap<>();
        if (moduleFile.isDirectory()) {
            for (File file : Objects.requireNonNull(moduleFile.listFiles())) {
                currentModules.putAll(loadModules(file));
            }
        } else {
            if (moduleFile.getName().endsWith(".jar")) {
                System.out.println("Loading module: " + moduleFile.getName());
                Module module = new Module(moduleFile);
                String moduleName = module.getName();
                if (modules.containsKey(moduleName)) {
                    System.err.printf("ERROR: Module `%s` is already loaded.%n", moduleName);
                    System.exit(1);
                }
                currentModules.put(moduleName, module);
            }
        }
        return currentModules;
    }

    public static void start() {
        for (Module module : modules.values()) {
            module.start();
        }
    }

    public static void stop() {
        for (Module module : modules.values()) {
            module.stop();
        }
    }
}
