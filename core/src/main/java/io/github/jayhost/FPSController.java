package io.github.jayhost;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

public class FPSController extends InputAdapter {
    private final PerspectiveCamera cam;
    private float yaw = 0f, pitch = 0f;
    private final float rotSpeed = 0.002f; // tweak sensitivity
    private final float moveSpeed = 2f;
    private final Vector3 tmp = new Vector3();

    public FPSController(PerspectiveCamera camera) {
        this.cam = camera;
        // Hide & grab cursor so it won't leave window
        Gdx.input.setCursorCatched(true);
        // Initialize yaw/pitch from cam.direction
        Vector3 d = cam.direction.cpy().nor();
        yaw   = (float)Math.atan2(d.x, -d.z);
        pitch = (float)Math.asin(d.y);
        // Center the cursor once
        recenterMouse();
    }

    private void recenterMouse() {
        int cx = Gdx.graphics.getWidth()/2;
        int cy = Gdx.graphics.getHeight()/2;
        Gdx.input.setCursorPosition(cx, cy);
    }

    public void update(float delta) {
        // 1) Mouse look via center-cursor
        int cx = Gdx.graphics.getWidth()/2;
        int cy = Gdx.graphics.getHeight()/2;
        int mx = Gdx.input.getX();
        int my = Gdx.input.getY();
        float dx = (mx - cx) * rotSpeed;
        float dy = (my - cy) * rotSpeed;
        if (dx != 0 || dy != 0) {
            yaw   -= dx;
            pitch -= dy;
            pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
            float cosP = (float)Math.cos(pitch);
            cam.direction.set(
                (float)Math.sin(yaw) * cosP,
                (float)Math.sin(pitch),
                (float)-Math.cos(yaw) * cosP
            );
            cam.up.set(Vector3.Y);
            cam.update();
            // immediately recenter
            recenterMouse();
        }

        // 2) WASD movement
        tmp.set(cam.direction).nor().scl(moveSpeed * delta);
        if (Gdx.input.isKeyPressed(Input.Keys.W)) cam.position.add(tmp);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) cam.position.sub(tmp);
        Vector3 right = cam.direction.cpy().crs(cam.up).nor().scl(moveSpeed*delta);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) cam.position.add(right);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) cam.position.sub(right);

        cam.update();
    }

    @Override
    public boolean keyDown(int keycode) {
        // allow escape to release cursor
        if (keycode == Input.Keys.ESCAPE) {
            Gdx.input.setCursorCatched(false);
        }
        return true;
    }
}
