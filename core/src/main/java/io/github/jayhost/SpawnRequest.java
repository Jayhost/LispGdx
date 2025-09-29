package io.github.jayhost;

import com.badlogic.gdx.math.Vector3;

public class SpawnRequest {
    public final String path;
    public final Vector3 position;
    public SpawnRequest(String path, float x, float y, float z) {
        this.path = path;
        this.position = new Vector3(x, y, z);
    }
}
