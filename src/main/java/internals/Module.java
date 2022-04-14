package internals;

import annotations.OnModuleHalt;
import annotations.OnModuleInitialize;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * The Module class contains all internal logic for individual modules in CuteCord.
 */
public class Module {
    /**
     * The name of the module.
     */
    private String name;

    /**
     * The file the module is from.
     */
    File moduleFile;

    /**
     * The Jar file object, from which the manifest can be read, and classes loaded.
     */
    JarFile jarFile;

    /**
     * The classes that belong to the module. Used for {@link Module#start()} and {@link Module#stop()}, may be used for other hooks in the future.
     */
    Map<String, Class<?>> classes = new HashMap<>();

    /**
     * Instantiates a new Module. The Module doesn't get loaded right away, instead it is loaded whenever {@link Module#load()}
     * is called, so that conflicts can be resolved more easily.
     *
     * @param moduleFile the module file
     */
    public Module(File moduleFile) {
        this.moduleFile = moduleFile;
        try {
            this.jarFile = new JarFile(moduleFile);
        } catch (IOException ignored) {
            System.err.printf("ERROR: Could not retrieve JarFile instance for %s%n", moduleFile.getName());
            System.exit(1);
        }
        this.name = this.getName();
    }

    /**
     * Loads the classes for the module.
     */
    protected void load() {
        Set<String> classes = this.getClassesInJar();
        classes.forEach(clazz -> {
            try {
                this.classes.put(clazz, CuteCord.moduleLoader.loadClass(clazz));
            } catch (ClassNotFoundException ignored) {
                System.err.printf("ERROR: Could not load class %s from module %s%n", clazz, this.getName());
                System.exit(1);
            }
        });
    }

    /**
     * Gets the value of a given attribute.
     *
     * @return The attribute as a String
     */
    public String getAttribute(String attribute) {
        String value = null;
        try {
            value = this.jarFile.getManifest().getMainAttributes().getValue(attribute);
        } catch (IOException e) {
            System.err.printf("ERROR: Could not read manifest for module %s%n", this.getName());
            System.exit(1);
        }
        return value;
    }

    /**
     * Gets the name of the module.
     *
     * @return the name of the module,
     */
    public String getName() {
        if (this.name == null) this.name = getAttribute("Module-Name");
        return name;
    }

    private Set<String> getClassesInJar() {
        Set<String> classes;
        classes = jarFile.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.endsWith(".class"))
                .map(name -> name.replace("/", "."))
                .map(name -> name.replace(".class", ""))
                .collect(Collectors.toSet());
        return classes;
    }

    /**
     * Invoke the method specified to be called when the bot starts in the manifest.
     */
    public void start() {
        String startClass = getAttribute("Module-Start");
        if (startClass != null && classes.containsKey(startClass)) {
            for (Method method : classes.get(startClass).getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnModuleInitialize.class)) continue;
                try {
                    method.invoke(null);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    System.err.printf("ERROR: Could not invoke start method %s from module %s%n", method.getName(), this.getName());
                }
            }
        }
    }

    /**
     * Invoke the method specified to be called when the bot stops in the manifest.
     */
    public void stop() {
        String stopClass = getAttribute("Module-Stop");
        if (stopClass != null && classes.containsKey(stopClass)) {
            for (Method method : classes.get(stopClass).getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnModuleHalt.class)) continue;
                try {
                    method.invoke(null);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    System.err.printf("ERROR: Could not invoke stop method %s from module %s%n", method.getName(), this.getName());
                }
            }
        }
    }
}
