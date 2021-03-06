package graphics;

import gameobject.GameObject;
import utils.Global;
import utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/*
 * ShaderProgram.java
 * Ambulare
 * Jacob Oaks
 * 4/15/20
 */

/**
 * Represents a GLSL shader program. All uniforms must be registered and set before rendering. The shader program class
 * provides essy ways to register and set an array of lights uniform of length MAX_LIGHTS and of name LIGHT_ARRAY_NAME
 * for easy light integration
 */
public class ShaderProgram {

    /**
     * Static Data
     */
    private static final int MAX_LIGHTS = 32;                /* the largest number of lights that can be rendered per
                                                                binding of one shader program*/
    private static final String LIGHT_ARRAY_NAME = "lights"; /* the name to assume light array uniforms to be in shader
                                                                program source code */
    private static final float MIN_FLICKER = -0.2f;          // the minimum allowed flicker of a light
    private static final float MAX_FLICKER = 0.2f;           // the maximum allowed flicker of a light

    /**
     * Members
     */
    private final Map<String, Integer> uniforms; // map of uniform names to locations
    private List<GameObject> postRenders;        /* a list of items that objects can add to in their render methods in
                                                    order to specify certain game objects that should be rendered after
                                                    rendering everything else */
    private float[] flickers;                    // an array of flicker values for each light
    private final int progID;                    // program id of the shader program
    private int vShaderID;                       // program id of the vertex shader
    private int fShaderID;                       // program id of the fragment shader
    private int lightNo;                         // how many light uniforms have been set since the last unbind/bind

    /**
     * Constructor
     *
     * @param vShaderPath the path to the vertex shader code
     * @param fShaderPath the path to the fragment shader code
     */
    public ShaderProgram(Utils.Path vShaderPath, Utils.Path fShaderPath) {
        this.progID = glCreateProgram(); // create GLSL program
        this.uniforms = new HashMap<>(); // initialize uniform map
        if (this.progID == 0) Utils.handleException(new Exception("Unable to create GLSL program"),
                this.getClass(), "ShaderProgram", true); // throw exception if cannot create program
        this.processShaders(vShaderPath, fShaderPath); // process shader program
    }

    /**
     * Processes the GLSL shaders by loading the code, compiling the code, then linking.
     *
     * @param vShaderPath the path to the vertex shader
     * @param fShaderPath the path to the fragment shader
     */
    private void processShaders(Utils.Path vShaderPath, Utils.Path fShaderPath) {
        String vShaderCode = Utils.pathContentsToString(vShaderPath); // read vertex shader code
        String fShaderCode = Utils.pathContentsToString(fShaderPath); // read fragment shader code
        this.vShaderID = processShader(vShaderCode, GL_VERTEX_SHADER); // process vertex shader
        this.fShaderID = processShader(fShaderCode, GL_FRAGMENT_SHADER); // process fragment shader
        this.link(); // link shaders
    }

    /**
     * Processes a GLSL shader (vertex or fragment) based on the given code by compiling it and attaching it to the
     * main program
     *
     * @param code the code to create the shader from
     * @param type the type of shader
     * @return the ID of the created shader
     */
    private int processShader(String code, int type) {
        int id = glCreateShader(type); // create shader
        if (id == 0) // if fail
            Utils.handleException(new Exception("Unable to create shader of type " + type + " with code: " + code),
                    this.getClass(), "processShader", true); // throw exception
        glShaderSource(id, code); // give shader the code
        glCompileShader(id); // compile shader
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) // if fail
            Utils.handleException(new Exception("Unable to compile shader of type " + type + ": " +
                    glGetShaderInfoLog(id, 1024)), this.getClass(), "processShader", true); // crash
        glAttachShader(this.progID, id); // attach to main program
        return id; // return id
    }

    /**
     * Links the shader programs's GLSL shaders together
     */
    private void link() {
        glLinkProgram(this.progID); // link program
        if (glGetProgrami(this.progID, GL_LINK_STATUS) == 0) // if fail
            Utils.handleException(new Exception("Unable to link shaders: " + glGetProgramInfoLog(this.progID,
                    1024)), this.getClass(), "link", true); // throw exception
        glDetachShader(this.progID, this.vShaderID); // detach vertex shader
        glDetachShader(this.progID, this.fShaderID); // detach fragment shader
    }

    /**
     * Register the uniform with the given name by finding its position and saving it
     *
     * @param name the name of the uniform to find
     */
    public void registerUniform(String name) {
        int loc = glGetUniformLocation(this.progID, name); // get location
        if (loc < 0) // if fail
            Utils.handleException(new Exception("Unable to find uniform with name '" + name + "'"), this.getClass(),
                    "registerUniform", true); // throw exception
        this.uniforms.put(name, loc); // save location
    }

    /**
     * Registers a light array uniform with the length MAX_LENGTH and with the name LIGHT_ARRAY_NAME. This will also
     * register the corresponding flicker array uniform
     */
    public void registerLightArrayUniform() {
        for (int i = 0; i < MAX_LIGHTS; i++) {
            this.registerUniform(LIGHT_ARRAY_NAME + "[" + i + "].glow");
            this.registerUniform(LIGHT_ARRAY_NAME + "[" + i + "].reach");
            this.registerUniform(LIGHT_ARRAY_NAME + "[" + i + "].intensity");
            this.registerUniform(LIGHT_ARRAY_NAME + "[" + i + "].x");
            this.registerUniform(LIGHT_ARRAY_NAME + "[" + i + "].y");
            this.registerUniform("flicker[" + i + "]");
        }
        this.flickers = new float[MAX_LIGHTS]; // create flicker values array
    }

    /**
     * Sets the uniform with the given name to the given value (a float)
     *
     * @param name the name of the uniform the set
     * @param v    the value to set it to
     */
    public void setUniform(String name, float v) {
        try {
            glUniform1f(this.uniforms.get(name), v); // try to set uniform
        } catch (Exception e) { // if exception
            Utils.handleException(e, this.getClass(), "setUniform", true); // handle exception
        }
    }

    /**
     * Sets the uniform with the given name to the given value (an integer)
     *
     * @param name the name of the uniform to set
     * @param v    the value to set it to
     */
    public void setUniform(String name, int v) {
        try {
            glUniform1i(this.uniforms.get(name), v); // try to set uniform
        } catch (Exception e) { // if exception
            Utils.handleException(e, this.getClass(), "setUniform", true); // handle exception
        }
    }

    /**
     * Sets the uniform with the given name to the given value (a 4-dimensional float array)
     *
     * @param name the name of the uniform to set
     * @param x    the first value of the 4-dimensional float array
     * @param y    the second value of the 4-dimensional float array
     * @param z    the third value of the 4-dimensional float array
     * @param a    the fourth value of the 4-dimensional float array
     */
    public void setUniform(String name, float x, float y, float z, float a) {
        try {
            glUniform4f(this.uniforms.get(name), x, y, z, a); // try to set uniform
        } catch (Exception e) { // if exception
            Utils.handleException(e, this.getClass(), "setUniform",
                    true); // handle exception
        }
    }

    /**
     * Inserts the light corresponding to the given light source and position into the shader program's lights array
     * uniform. This will only accept up to MAX_LIGHTS amount of lights per binding of the shader program. This assumes
     * that the light uniform is named LIGHT_ARRAY_NAME in the source code
     *
     * @param light the light source whose light properties to use
     * @param x     the x position of the light
     * @param y     the y position of the light
     */
    public void putInLightArrayUniform(LightSource light, float x, float y) {
        if (this.lightNo >= MAX_LIGHTS) // if too many lights are being rendered, throw an exception
            Utils.handleException(new Exception("Maximum amount of renderable lights exceeded: " + MAX_LIGHTS),
                    this.getClass(), "setLightUniform", true);
        try {
            String name = "lights[" + this.lightNo + "]"; // get the proper name for the light in the lights array
            this.flicker(light, this.lightNo); // apply flicker
            this.lightNo++; // iterate the lights array iterator
            float[] glow = light.getGlow(); // get the light's glow
            glUniform3f(this.uniforms.get(name + ".glow"), glow[0], glow[1], glow[2]); // set the light's glow
            glUniform1f(this.uniforms.get(name + ".reach"), light.getReach()); // set the light's reach
            glUniform1f(this.uniforms.get(name + ".intensity"), light.getIntensity()); // set the light's intensity
            glUniform1f(this.uniforms.get(name + ".x"), x); // set the light's x position
            glUniform1f(this.uniforms.get(name + ".y"), y); // set the light's y position
        } catch (Exception e) { // if exception
            // handle exception
            Utils.handleException(e, this.getClass(), "setUniform", true);
        }
    }

    /**
     * Applies flicker to a light source uniform
     * @param ls the light source defining the flicker
     * @param i the index of the light in the lights array
     */
    private void flicker(LightSource ls, int i) {
        // generate a random change in flicker according to the light source's flicker speed
        float dFlicker = ((float)Math.random() * ls.getFlickerSpeed() * 2f) - ls.getFlickerSpeed();
        // apply change in flicker to the light's flicker, bounded by min/max constants
        this.flickers[i] = Math.max(MIN_FLICKER, Math.min(MAX_FLICKER, this.flickers[i] + dFlicker));
        this.setUniform("flicker[" + i + "]", this.flickers[i]); // set the uniform
    }

    /**
     * Will render any objects that have been added to the post-renders list during the course of rendering. See members
     * for more info on post-renders
     */
    public void renderPostRenders() {
        for (GameObject go : this.postRenders) go.render(this); // render each post-render
    }

    /**
     * Adds an object to the list of objects to be rendered after normal rendering has occurred, when renderPostRenders()
     * is called. See members for more info on post renders
     * @param go the game object to add to the post renders list
     */
    public void addToPostRender(GameObject go) {
        this.postRenders.add(go); // add to post renders list
    }

    /**
     * Binds the shader program
     */
    public void bind() {
        glUseProgram(this.progID); // tell OpenGL to use the program
        this.postRenders = new ArrayList<>(); // create a new list for post-renders
    }

    /**
     * Unbinds the shader program and resets the lights array iterator
     */
    public void unbind() {
        glUseProgram(0); // unbind program
        this.lightNo = 0; // reset lights array iterator
        this.postRenders = null; // delete old post-renders list
    }

    /**
     * Cleans up the shader program
     */
    public void cleanup() {
        this.unbind(); // make sure isn't bound
        if (this.progID != 0) glDeleteProgram(this.progID); // delete program
    }
}
