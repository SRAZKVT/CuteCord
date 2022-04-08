package internals;

public class CuteCordMain {
    public static void main(String[] args) {
        if (!CuteCord.loadModules()) System.exit(1);
        CuteCord.start();
    }
}