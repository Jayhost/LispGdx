// File: io/github/jayhost/GBufferShaderProvider.java
package io.github.jayhost;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.utils.Array; // For tracking
import com.badlogic.gdx.utils.GdxRuntimeException;

public class GBufferShaderProvider implements ShaderProvider {

    // Keep track of all shaders this provider has EVER vended
    // This is for debugging disposal, NOT for caching for reuse.
    private static final Array<GBufferShader> allCreatedShaders = new Array<>(false, 16);

    @Override
    public Shader getShader(Renderable renderable) {
        String renderableId = (renderable != null ? renderable.toString() : "null");
        Gdx.app.log("GBufferShaderProvider (Tracking)", "getShader called for: " + renderableId);

        GBufferShader shaderInstance;
        Gdx.app.log("GBufferShaderProvider (Tracking)", "Creating NEW GBufferShader instance for: " + renderableId);
        try {
            shaderInstance = new GBufferShader(renderable); // Constructor handles its own program creation/compilation.
            allCreatedShaders.add(shaderInstance); // Track it
            Gdx.app.log("GBufferShaderProvider (Tracking)", "Tracking new shader: " + shaderInstance.toString());
        } catch (Exception e) {
            Gdx.app.error("GBufferShaderProvider (Tracking)", "Exception during GBufferShader instantiation for " + renderableId, e);
            throw new GdxRuntimeException("Failed to instantiate GBufferShader for " + renderableId, e);
        }

        Gdx.app.log("GBufferShaderProvider (Tracking)", "New GBufferShader (" + shaderInstance.toString() + ") created, program should be valid. Program ID: " + shaderInstance.program.getHandle());

        if (!shaderInstance.canRender(renderable)) {
            Gdx.app.log("GBufferShaderProvider (Tracking)", "Newly created GBufferShader (" + shaderInstance.toString() + ") reports canRender() == false for: " + renderableId + ". Disposing it and returning NULL.");
            shaderInstance.dispose(); // This will log from GBufferShader.dispose()
            // DO NOT remove from allCreatedShaders here, we want to see its disposal log.
            return null;
        }

        Gdx.app.log("GBufferShaderProvider (Tracking)", "Newly created GBufferShader (" + shaderInstance.toString() + ") is suitable. Returning it for: " + renderableId);
        return shaderInstance;
    }

    @Override
    public void dispose() {
        Gdx.app.log("GBufferShaderProvider (Tracking)", "dispose() called on provider. Disposing all tracked shaders ("+allCreatedShaders.size+").");
        for(GBufferShader shader : allCreatedShaders){
            if(shader != null) { // Should not be null if added
                Gdx.app.log("GBufferShaderProvider (Tracking)", "Disposing tracked shader from provider: " + shader.toString());
                shader.dispose();
            }
        }
        allCreatedShaders.clear();
    }

    // Static method to check if a shader is known and its state (for debugging from other places)
    public static String getTrackedShaderInfo(Shader shader) {
        if (shader == null) return "null_shader_instance_ref";
        if (!(shader instanceof GBufferShader)) return "not_a_GBufferShader_instance";

        GBufferShader gbShader = (GBufferShader) shader;
        boolean isTracked = false; // Check if it's in our static list
        for (GBufferShader s : allCreatedShaders) {
            if (s == gbShader) {
                isTracked = true;
                break;
            }
        }

        String programState = "PROGRAM_UNKNOWN";
        String locationsState = "LOCATIONS_UNKNOWN";

        if (gbShader.program == null) {
            programState = "PROGRAM_NULL";
        } else {
            programState = "ProgramID:" + gbShader.program.getHandle() +
                (gbShader.program.isCompiled() ? "_Compiled" : "_NOT_COMPILED");
        }

        // To check 'locations', we need to access it. It's protected in BaseShader.
        // We can't directly access it here unless GBufferShader exposes it or a method.
        // For now, let's assume if program is null, locations is also likely null due to dispose.
        // The true test is the NullPointerException.

        // Let's check if the shader instance itself believes it's disposed via a hypothetical flag
        // (DefaultShader/BaseShader don't have a public 'isDisposed' flag)

        return gbShader.toString() + " (TrackedByProvider: " + isTracked + ", State: " + programState + ")";
    }
}
