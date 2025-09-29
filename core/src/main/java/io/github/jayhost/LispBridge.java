// io/github/jayhost/LispBridge.java
package io.github.jayhost;

import java.util.ArrayList;
import java.util.List;

public class LispBridge {
    private static final ArrayList<SpawnRequest> spawnList = new ArrayList<>();
    private static final ArrayList<String> removalList = new ArrayList<>();

    // Enqueue with position
    public static void addEntityAt(Object path, Object x, Object y, Object z) {
        String strPath = (String) path;
        double dx = ((Number) x).doubleValue();
        double dy = ((Number) y).doubleValue();
        double dz = ((Number) z).doubleValue();
        spawnList.add(new SpawnRequest(strPath, (float) dx, (float) dy, (float) dz));
    }
    public static List<SpawnRequest> drainSpawns() {
        synchronized(spawnList) {
            List<SpawnRequest> copy = new ArrayList<>(spawnList);
            spawnList.clear();
            return copy;
        }
    }

    public static void removeEntity(Object path) {
        removalList.add((String) path);
    }
    public static List<String> drainRemovals() {
        synchronized(removalList) {
            List<String> copy = new ArrayList<>(removalList);
            removalList.clear();
            return copy;
        }
    }
}
