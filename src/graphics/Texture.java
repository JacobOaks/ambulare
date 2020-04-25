package graphics;

import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import utils.Utils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/**
 * Represents a Texture
 */
public class Texture {

    /**
     * Members
     */
    private final int id, w, h; // texture ID, width, and height

    /**
     * Constructor
     * @param path the path to the image
     * @param resPath whether the given path is resource-relative
     */
    public Texture(String path, boolean resPath) {

        // create buffers to hold texture info
        ByteBuffer buf = null; // create buffer for texture data
        MemoryStack stack = MemoryStack.stackPush(); // push memory stack for buffers
        IntBuffer w = stack.mallocInt(1); // create buffer for texture width
        IntBuffer h = stack.mallocInt(1); // create buffer for texture height
        IntBuffer channels = stack.mallocInt(1); // create buffer to hold channel amount (4 if rgba)

        // attempt to load texture
        try {
            if (resPath) { // if resource-relative path
                byte[] p = IOUtils.toByteArray(Class.forName(Texture.class.getName())
                        .getResourceAsStream(path)); // convert resource to byte array
                ByteBuffer pBuff = BufferUtils.createByteBuffer(p.length); // create buffer for texture data
                pBuff.put(p).flip(); // put texture data into buffer
                buf = stbi_load_from_memory(pBuff, w, h, channels, 4); // load texture into buffer
            } else { // if non-resource path
                File file = new File(path); // create file with corresponding path
                buf = stbi_load(file.getAbsolutePath(), w, h, channels, 4); // load texture into buffer
            }
            if (buf == null)
                Utils.handleException(new Exception("Unable to load texture with " + (resPath ? ("resource-relative ") :
                                "") + "path '"  + path +"' for reason: " + stbi_failure_reason()), "graphics.Texture",
                                "Texture(String)", true); // throw exception if unable to load texture
        } catch (Exception e) { // if exception
            Utils.handleException(e, "graphics.Texture", "Texture(String)", true); // handle exception
        }

        // save info, create texture, cleanup
        this.w = w.get(); // save width
        this.h = h.get(); // save height
        this.id = glGenTextures(); // generate texture object

        glBindTexture(GL_TEXTURE_2D, id); // bind new texture object
        glPixelStoref(GL_UNPACK_ALIGNMENT, 1); // tell GL that each component will be one byte in size
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); // this makes pixels clear and un-blurred
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST); // this makes pixels clear and un-blurred
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, this.w, this.h, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                buf); // generate texture
        glGenerateMipmap(GL_TEXTURE_2D); // generate mip maps
        stbi_image_free(buf); // cleanup by freeing image memory
    }

    /**
     * @return the texture's ID
     */
    public int getID() { return this.id; }

    /**
     * @return the texture's width
     */
    public int getWidth() { return this.w; }

    /**
     * @return the texture's height
     */
    public int getHeight() { return this.h; }

    /**
     * Cleans up the texture
     */
    public void cleanup() { glDeleteTextures(this.id); }
}
