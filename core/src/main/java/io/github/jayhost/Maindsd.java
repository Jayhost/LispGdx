import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;

public class Maindsd extends ApplicationAdapter {

    private PerspectiveCamera cam;
    private ModelBatch depthBatch;
    private ModelInstance boxInstance;
    private FrameBuffer fbo;
    private SpriteBatch spriteBatch;
    private ShaderProgram outlineShader;
    private TextureRegion fboRegion;

    @Override
    public void create() {
        // 1. Create a simple 3D box to render
        ModelBuilder modelBuilder = new ModelBuilder();
        Model boxModel = modelBuilder.createBox(2f, 2f, 2f,
                new Material(ColorAttribute.createDiffuse(Color.GREEN)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        boxInstance = new ModelInstance(boxModel);

        // 2. Set up the camera
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(3f, 4f, 5f);
        cam.lookAt(0, 0, 0);
        cam.near = 1f;
        cam.far = 50f;
        cam.update();

        // 3. Create a special ModelBatch just for the depth pass.
        // This uses a shader that draws object depth instead of color.
        depthBatch = new ModelBatch(new DepthShaderProvider());

        // 4. Set up the 2D renderer and the custom outline shader
        spriteBatch = new SpriteBatch();
        String vert = Gdx.files.internal("outline.vert.glsl").readString();
        String frag = Gdx.files.internal("outline.frag.glsl").readString();
        outlineShader = new ShaderProgram(vert, frag);
        if (!outlineShader.isCompiled()) {
            Gdx.app.error("OutlineShader", "compilation failed:\n" + outlineShader.getLog());
            Gdx.app.exit();
        }
    }

    @Override
    public void resize(int width, int height) {
        // The FBO and camera need to be updated when the screen resizes
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();

        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void render() {
        // Animate the box so we can see the outline change
        boxInstance.transform.rotate(Vector3.Y, Gdx.graphics.getDeltaTime() * 30f);

        // === PASS 1: RENDER DEPTH TO FRAMEBUFFER ===
        fbo.begin();
        Gdx.gl.glClearColor(1, 1, 1, 1); // Clear FBO to white
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        
        depthBatch.begin(cam);
        depthBatch.render(boxInstance);
        depthBatch.end();
        fbo.end();

        // === PASS 2: RENDER OUTLINES TO SCREEN ===
        // Prepare the texture from the FBO
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        // Clear the main screen to a dark color
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Draw the outlines
        spriteBatch.setShader(outlineShader);
        spriteBatch.begin();
        
        outlineShader.setUniformf("u_texelSize", 1f / fbo.getWidth(), 1f / fbo.getHeight());
        
        spriteBatch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        
        spriteBatch.end();
        spriteBatch.setShader(null);
    }

    @Override
    public void dispose() {
        // Clean up all resources
        boxInstance.model.dispose();
        depthBatch.dispose();
        fbo.dispose();
        spriteBatch.dispose();
        outlineShader.dispose();
    }
}