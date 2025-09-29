// File: io/github/jayhost/GBufferShader.java
package io.github.jayhost;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;

public class GBufferShader extends DefaultShader {

    // You should have already loaded these static sources in Main.create()
    public static String vertexShaderSource;
    public static String fragmentShaderSource;

    // A static method to be called once from Main.create()
    public static void initShaderSources() {
        vertexShaderSource = Gdx.files.internal("shaders/gbuffer.vert").readString();
        fragmentShaderSource = Gdx.files.internal("shaders/gbuffer.frag").readString();
    }

    public GBufferShader(Renderable renderable) {
        // 1. Create a configuration object.
        //    Pass your loaded GLSL sources here.
        super(renderable, new DefaultShader.Config(vertexShaderSource, fragmentShaderSource));

        // 2. The super(renderable, config) constructor handles EVERYTHING:
        //    - It creates the ShaderProgram.
        //    - It compiles the ShaderProgram.
        //    - It calls init(), which populates the 'locations' array.
        //
        //    Your GBufferShader is now ready to be used.
    }

    // You can override other methods like begin(), render(), etc. if needed,
    // but the core initialization is now handled correctly.
    @Override
    public void begin(com.badlogic.gdx.graphics.Camera camera, com.badlogic.gdx.graphics.g3d.utils.RenderContext context) {
        // This call will now succeed because 'locations' is not null.
        super.begin(camera, context);
    }
}
