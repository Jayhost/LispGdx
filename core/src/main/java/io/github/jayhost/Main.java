package io.github.jayhost;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.shaders.DepthShader;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import net.mgsx.gltf.scene3d.utils.EnvironmentUtil;
import java.util.HashMap;

public class Main extends ApplicationAdapter {

    private PerspectiveCamera cam;
    private InputAdapter cameraController;
    private ReplSession interpreter;
    private Thread lispThread;
    
    // Managers and scene trackers
    private SceneManager outlinedObjectManager, backgroundManager, viewmodelManager;
    private HashMap<String, Scene> outlinedScenes = new HashMap<>();
    private HashMap<String, Scene> backgroundScenes = new HashMap<>();
    private Scene gunScene;

    // Outline Effect Resources
    private FrameBuffer fbo;
    private SpriteBatch spriteBatch;
    private ShaderProgram outlineShader;
    private TextureRegion fboRegion;

    // ✅ NEW: Special ModelBatch for rendering the gun's depth
    private ModelBatch gunDepthBatch;
    private boolean isPaused = false;
    private SpriteBatch batch;
    private BitmapFont font;

    @Override
    public void create() {
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        batch = new SpriteBatch();
     font = new BitmapFont(); // Creates a default white font

        outlinedObjectManager = new SceneManager(PBRShaderProvider.createDefault(24), new DepthShaderProvider());
        backgroundManager = new SceneManager(PBRShaderProvider.createDefault(24), new DepthShaderProvider());
        viewmodelManager = new SceneManager(PBRShaderProvider.createDefault(24), new DepthShaderProvider());

        // ✅ NEW: Create the custom depth batch for the gun
        String vert = Gdx.files.internal("com/badlogic/gdx/graphics/g3d/shaders/depth.vertex.glsl").readString();
        String frag = Gdx.files.internal("gun-depth.frag.glsl").readString();
        gunDepthBatch = new ModelBatch(new DepthShaderProvider(vert, frag));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(-13f, 1f, 1f);
        cam.lookAt(0, 5f, 0);
        cam.near = 0.1f;
        cam.far = 100f;
        cam.update();

        outlinedObjectManager.setCamera(cam);
        backgroundManager.setCamera(cam);
        viewmodelManager.setCamera(cam);

        Cubemap diffuse = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), "mytex/diffuse/diffuse_", ".png", EnvironmentUtil.FACE_NAMES_NEG_POS);
        Cubemap specular = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), "mytex/specular/specular_", "_", ".png", 10, EnvironmentUtil.FACE_NAMES_NEG_POS);
        Texture brdfLUT = new Texture(Gdx.files.internal("textures/brdfLUT.png"));
        DirectionalShadowLight worldShadowLight = new DirectionalShadowLight(4096, 4096);
        worldShadowLight.set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f);
        SceneSkybox skybox = new SceneSkybox(diffuse);

        SceneManager[] worldManagers = {outlinedObjectManager, backgroundManager};
        for (SceneManager sm : worldManagers) {
            sm.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
            sm.environment.set(PBRCubemapAttribute.createSpecularEnv(specular));
            sm.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuse));
            sm.environment.set(new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 0.005f));
            sm.environment.add(worldShadowLight);
            sm.setSkyBox(skybox);
        }

        DirectionalShadowLight gunLight = new DirectionalShadowLight(512, 512);
        gunLight.set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f);
        viewmodelManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        viewmodelManager.environment.add(gunLight);
        viewmodelManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specular));
        viewmodelManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuse));

        spriteBatch = new SpriteBatch();
        String outlineVert = Gdx.files.internal("outline.vert.glsl").readString();
        String outlineFrag = Gdx.files.internal("outline.frag.glsl").readString();
        outlineShader = new ShaderProgram(outlineVert, outlineFrag);
        if (!outlineShader.isCompiled()) {
            Gdx.app.error("OutlineShader", "compilation failed:\n" + outlineShader.getLog());
            Gdx.app.exit();
        }

        createCameraController();
        Gdx.input.setInputProcessor(cameraController);
        Gdx.input.setCursorCatched(true);
        setupLispInterpreter();
    }

    @Override
    public void resize(int width, int height) {
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        outlinedObjectManager.updateViewport(width, height);
        backgroundManager.updateViewport(width, height);
        viewmodelManager.updateViewport(width, height);
        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void render() {

        if (isPaused) {
            isPaused = false;
            // We can also skip the rest of the render loop for this single frame
            // to ensure everything is stable, though it's optional.
            return; 
        }

        processLispCommands();

        float dt = Gdx.graphics.getDeltaTime();
        updateCamera(dt);
        outlinedObjectManager.update(dt);
        backgroundManager.update(dt);
        viewmodelManager.update(dt);
        
        if (gunScene != null) {
            gunScene.modelInstance.transform.set(cam.view).inv();
            gunScene.modelInstance.transform.translate(0.4f, -0.55f, -0.8f);
            gunScene.modelInstance.transform.rotate(Vector3.Y, 180);
        }

        // ✅ UPDATED DEPTH PASS
        fbo.begin();
        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        // Render world objects (alpha will be 1.0)
        outlinedObjectManager.renderDepth();
        // Render the gun with our custom shader to "tag" its pixels with alpha 0.5
        if (gunScene != null) {
            gunDepthBatch.begin(cam);
            gunDepthBatch.render(gunScene.modelInstance);
            gunDepthBatch.end();
        }
        fbo.end();

        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        // RENDER PBR SCENE
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        backgroundManager.render();
        outlinedObjectManager.render();

        // RENDER VIEWMODEL
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        if (gunScene != null) {
            viewmodelManager.render();
        }

        //outline pass
        spriteBatch.setShader(outlineShader);
        spriteBatch.begin();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        outlineShader.setUniformf("u_texelSize", 1f / fbo.getWidth(), 1f / fbo.getHeight());
        // These control the world outlines
        outlineShader.setUniformf("u_nearThreshold", 0.01f);
        outlineShader.setUniformf("u_farThreshold", 0.001f);
        // This is the separate, less sensitive threshold for the gun
        outlineShader.setUniformf("u_gunThreshold", 0.1f);
        spriteBatch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glDisable(GL20.GL_BLEND);
        spriteBatch.end();
        spriteBatch.setShader(null);


        batch.begin();
    int fps = Gdx.graphics.getFramesPerSecond();
    font.draw(batch, "FPS: " + fps, 10, Gdx.graphics.getHeight() - 10);
    batch.end();
    }

    // In your Main.java file, REPLACE the entire old method with this one.

private void createCameraController() {
    cameraController = new InputAdapter() {
        private final float rotateSpeed = 0.2f;
        private float pitch = 0f;
        private final Vector3 tmp = new Vector3();

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
     
            float maxDelta = 100.0f; 

            // 2. Get the raw delta values.
            float rawDeltaX = -Gdx.input.getDeltaX();
            float rawDeltaY = -Gdx.input.getDeltaY();

            // 3. Clamp the values to our maximum and apply rotation speed.
            float deltaX = Math.min(maxDelta, Math.max(-maxDelta, rawDeltaX)) * rotateSpeed;
            float deltaY = Math.min(maxDelta, Math.max(-maxDelta, rawDeltaY)) * rotateSpeed;

            // --- YAW (Left/Right) ---
            cam.rotate(Vector3.Y, deltaX);

            // --- PITCH (Up/Down) ---
            Vector3 right = tmp.set(cam.direction).crs(Vector3.Y).nor();
            
            // We still clamp the pitch angle to prevent flipping over.
            if (pitch + deltaY > -89.0f && pitch + deltaY < 89.0f) {
                pitch += deltaY;
                cam.rotate(right, deltaY);
            }

            cam.update();
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) {
                Gdx.input.setCursorCatched(false);
            }
            return false;
        }
    };
}

    @Override
public void pause() {
    isPaused = true;
}

@Override
public void resume() {
    isPaused = true;
}


    private void updateCamera(float deltaTime) {
        if (isPaused) return;
        Vector3 moveDirection = new Vector3();
        float moveSpeed = 5f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) moveDirection.add(cam.direction);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) moveDirection.sub(cam.direction);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) moveDirection.add(new Vector3(cam.direction).crs(Vector3.Y).nor());
        if (Gdx.input.isKeyPressed(Input.Keys.A)) moveDirection.sub(new Vector3(cam.direction).crs(Vector3.Y).nor());
        if (!moveDirection.isZero()) {
            moveDirection.y = 0;
            cam.position.add(moveDirection.nor().scl(moveSpeed * deltaTime));
        }
        cam.update();
    }

    private void processLispCommands() {
        try {
            Object spawnRaw = interpreter.eval("(drain-spawns)");
            if (spawnRaw instanceof java.util.List) {
                for (Object o : (java.util.List<?>) spawnRaw) {
                    SpawnRequest req = (SpawnRequest) o;
                    boolean alreadyLoaded = outlinedScenes.containsKey(req.path) || backgroundScenes.containsKey(req.path) || (gunScene != null && req.path.contains("mygun"));
                    if (alreadyLoaded) continue;
                    SceneAsset a = new GLTFLoader().load(Gdx.files.internal(req.path));
                    Scene s = new Scene(a.scene);
                    s.modelInstance.transform.setToTranslation(req.position);
                    if (req.path.contains("gar")) {
                        backgroundManager.addScene(s);
                        backgroundScenes.put(req.path, s);
                    } else if (req.path.contains("mygun")) {
                        gunScene = s;
                        viewmodelManager.addScene(gunScene);
                    } else {
                        outlinedObjectManager.addScene(s);
                        outlinedScenes.put(req.path, s);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        try {
            Object removeRaw = interpreter.eval("(drain-removals)");
            if (removeRaw instanceof java.util.List) {
                for (Object o : (java.util.List<?>) removeRaw) {
                    String path = (String) o;
                    Scene sceneToRemove = outlinedScenes.remove(path);
                    if (sceneToRemove != null) outlinedObjectManager.removeScene(sceneToRemove);
                    sceneToRemove = backgroundScenes.remove(path);
                    if (sceneToRemove != null) backgroundManager.removeScene(sceneToRemove);
                    if (path.contains("mygun") && gunScene != null) {
                        viewmodelManager.removeScene(gunScene);
                        gunScene = null;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupLispInterpreter() {
        interpreter = new ReplSession(new Environment());
        try {
            interpreter.eval("(def (add-at path x y z) (java-call \"io.github.jayhost.LispBridge\" \"addEntityAt\" path x y z))");
            interpreter.eval("(def (add path) (add-at path 0 0 0))");
            interpreter.eval("(def (rem path) (java-call \"io.github.jayhost.LispBridge\" \"removeEntity\" path))");
            interpreter.eval("(def (drain-spawns) (java-call \"io.github.jayhost.LispBridge\" \"drainSpawns\"))");
            interpreter.eval("(def (drain-removals) (java-call \"io.github.jayhost.LispBridge\" \"drainRemovals\"))");
            interpreter.eval("(defvar helm \"models/helm/DamagedHelmet.gltf\")");
            interpreter.eval("(defvar sponza \"models/sponza/Sponza.gltf\")");
            interpreter.eval("(defvar mosin \"models/mosin/M91.gltf\")");
            interpreter.eval("(defvar mygun \"models/hoard/mygun.gltf\")");
            interpreter.eval("(defvar clap \"models/clap/clap.gltf\")");
            interpreter.eval("(defvar gar \"models/garage/garage.gltf\")");

            interpreter.eval("(add helm)");
            // interpreter.eval("(add clap)");
            interpreter.eval("(add gar)");
            interpreter.eval("(add mygun)");
        } catch (Exception e) { e.printStackTrace(); }
        lispThread = new Thread(() -> {
            try { ReplSession.main(this.interpreter.getEnvironment(), this.interpreter.getTopLevelForms()); }
            catch (Exception e) { /* Allow thread to exit */ }
        }, "Lisp-REPL");
        lispThread.setDaemon(true);
        lispThread.start();
    }

    @Override
    public void dispose() {
        if (lispThread != null) lispThread.interrupt();
        outlinedObjectManager.dispose();
        backgroundManager.dispose();
        viewmodelManager.dispose();
        if (gunDepthBatch != null) gunDepthBatch.dispose();
        if (fbo != null) fbo.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (outlineShader != null) outlineShader.dispose();
    }
}

