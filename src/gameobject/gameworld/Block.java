package gameobject.gameworld;

import graphics.*;
import utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

/*
 * Block.java
 * Ambulare
 * Jacob Oaks
 * 5/9/2020
 */

/**
 * An abstract class that provides functionality for loading and rendering blocks
 */
public abstract class Block {

    /**
     * Static Data
     */
    private static final int NO_TEXTURE = -1;              // used to denote that a block info is not textured
    private static final int MAX_OVERLAYS = 4;             // the max amount of overlays to be applied to block texture

    /**
     * Renders the set of blocks corresponding to the given set of block positions very efficiently using the given
     * shader program
     *
     * @param bm             the block model to render
     * @param sp             the shader program to render with
     * @param blockPositions the list of block positions grouped together by material. The reason it is set up as a map
     *                       from material to pair lists is because by rendering all blocks of a certain material at
     *                       once, repetitive calls can be avoided
     * @param camView        the axis-aligned bounding box to check the block's position against. If the block is not
     *                       within the camera's view, it will not be rendered
     * @return the total amount of blocks rendered
     */
    public static int renderBlocks(BlockModel bm, ShaderProgram sp,
                                    Map<Material, List<Pair<Integer>>> blockPositions, PhysicsEngine.AABB camView) {
        int blocksRendered = 0; // keep track of the amount of rendered blocks
        for (Material m : blockPositions.keySet()) { // for each material
            Texture t = m.getTexture(); // get texture for material
            if (t instanceof AnimatedTexture) bm.useTexCoordVBO(((AnimatedTexture) t).getTexCoordVBO(false),
                    false); // if the texture is animated, tell the model which tex coords (frame) to use
                // if the texture isn't animated, just use the entire texture
            else bm.useTexCoordVBO(AnimatedTexture.getTexCoordVBO(0, 1, false), false);
            m.setUniforms(sp); // set the appropriate material uniforms
            // render all the blocks with that material at once and keep track of total blocks rendered
            blocksRendered += bm.renderBlocks(sp, blockPositions.get(m), camView);
        }
        return blocksRendered; // return total block render count
    }

    /**
     * Uses a key and layout layers from an area node-file to load blocks and compile a corresponding mapping of
     * material to positions of blocks using that material. This method will also populate a list of animated textures
     * with any animated textures created during the loading process. This method will undergo the process of loading
     * for all three layout layers: background, middleground, and foreground
     *
     * @param key          the block_key child node from the area node-file
     * @param background   the background layout layer child node from the area node-file. May be null if no
     *                     background layout layer was specified for the area
     * @param middleground the middleground layout layer child node from the area node-file. May not be null as a
     *                     middleground layout layer is required for all areas
     * @param foreground   the foreground layout layer child node from the area node-file. May be null if no
     *                     foreground layout layer was specified for the area
     * @param blocks       the material to block maps to populate. Should be a length 3 array where blocks[0] is for
     *                     the background blocks, blocks[1] is for the middleground blocks, and blocks[2] is for the
     *                     foreground blocks
     * @param ats          the list of animated textures to populate
     * @return an array of objects where [0](boolean[][]) is the block map populated with the layout layer's blocks and
     * [1](PhysicsEngine.SlopeType[][]) is the slope map populated with the layout layer's slopes
     */
    public static Object[] loadLayoutBlocks(Node key, Node background, Node middleground, Node foreground,
                                               Map<Material, List<Pair<Integer>>>[] blocks, List<AnimatedTexture> ats) {
        Map<Character, BlockInfo> k = parseKeyData(key); // parse the key
        Map<List<Object>, Material> mm = new HashMap<>(); /* maps from a list of properties of a block to a
            corresponding material. This is used to maintain high space efficiency and low memory usage by minimizing
            the amount of materials needed, as opposed to creating a new material for each block. This works because
            lists are hashed based off of their contained values rather than their references. The order of objects that
            are entered into this map is as follows:
            [0] - [BlockInfo]: block information
            [1] - [Utils.Path]: texture path (or NO_TEXTURE if the block info is not textured). This requires a separate
            field because some block information specifies multiple textures to be randomized over
            [2] - [Connectivity]: the connectivity of the block in question
            [3] - [List<Boolean>]: the cut flags of the block in question */

        // calculate block map width and height
        int bmw = 0, bmh = 0; // initialize width and height to zero
        if (background != null) { // if a background was specified
            List<Node> rows = background.getChildren(); // get the rows of the background
            if (rows.size() > bmh) bmh = rows.size(); // if there are more rows, record new height
            // loop through each row and see if a wider row is found
            for (Node node : rows) if (node.getValue().length() > bmw) bmw = node.getValue().length();
        }
        List<Node> rows = middleground.getChildren(); // get the rows of the middleground
        if (rows.size() > bmh) bmh = rows.size(); // if there are more rows, record new height
        // loop through each row and see if a wider row is found
        for (Node node : rows) if (node.getValue().length() > bmw) bmw = node.getValue().length();
        if (foreground != null) { // if a foreground was specified
            rows = foreground.getChildren(); // get the rows of the foreground
            if (rows.size() > bmh) bmh = rows.size(); // if there are more rows, record new height
            // loop through each row and see if a wider row is found
            for (Node node : rows) if (node.getValue().length() > bmw) bmw = node.getValue().length();
        }

        // load layouts for each layer and format the blocks
        ShaderProgram sp = beginBlockFormatting(); // create shader program for block formatting
        Model m = Model.getStdGridRect(2, 2); // create model for texture creation
        // load layouts for each layer
        if (background != null) // if there is a background
            loadLayoutLayerBlocks(mm, blocks[0], background, k, ats, bmw, bmh, sp, m); // load background
        // load middleground and save the block map and slope map for collision
        Object[] maps = loadLayoutLayerBlocks(mm, blocks[1], middleground, k, ats, bmw, bmh, sp, m);
        if (foreground != null) // if there is a foreground
            loadLayoutLayerBlocks(mm, blocks[2], foreground, k, ats, bmw, bmh, sp, m); // load foreground
        for (BlockInfo bi : k.values()) bi.cleanup(); // cleanup block info overlay textures
        m.cleanup();
        endBlockFormatting(sp); // end block formatting

        // log block loading metrics
        int totalBlocks = 0; // create variable to store block count
        // go through each block layer
        for (Map<Material, List<Pair<Integer>>> block : blocks)
            for (List<Pair<Integer>> bs : block.values()) totalBlocks += bs.size(); // and count the total blocks
        Utils.log("Finished loading block layout with:\n" + mm.values().size() + " resulting material instances\n"
                + totalBlocks + " total blocks", Block.class, "loadLayoutBlocks", false); // log metrics
        return maps; // return the middleground block map and slope map to use for collision
    }

    /**
     * Loads a single layout layer of blocks
     *
     * @param mm     the material map to draw from and populate. See loadLayoutBlocks() for more information
     * @param blocks the material to block maps to populate
     * @param layout the layout node from the area node-file to read from
     * @param key    the parsed character to block info key
     * @param ats    the list of animated textures to populate
     * @param bmw    the width of a block map for the corresponding layout
     * @param bmh    the height of a block map for the corresponding layout
     * @param sp     the block formatting shader program created via beginBlockFormatting
     * @param model  the full square model to use for formatting block textures
     * @return an array of objects where [0](boolean[][]) is the block map populated with the layout layer's blocks and
     * [1](PhysicsEngine.SlopeType[][]) is the slope map populated with the layout layer's slopes
     */
    private static Object[] loadLayoutLayerBlocks(Map<List<Object>, Material> mm,
                                                     Map<Material, List<Pair<Integer>>> blocks, Node layout,
                                                     Map<Character, BlockInfo> key, List<AnimatedTexture> ats, int bmw,
                                                     int bmh, ShaderProgram sp, Model model) {

        // populate block map based on where blocks are
        List<Node> rows = layout.getChildren(); // get the rows of the layout
        int diff = bmh - rows.size(); // find diff in rows of the current layer and the overall layout
        boolean[][] blockMap = new boolean[bmw][bmh]; // create an appropriately sized block map
        Map<Pair<Integer>, PhysicsEngine.SlopeType> slopeList = new HashMap<>(); //  slope positions to populate
        for (int i = 0; i < rows.size(); i++) { // for each row
            int y = i + diff; // the y for this row is i + the difference in rows
            String row = rows.get(rows.size() - 1 - i).getValue(); // get the row
            for (int x = 0; x < row.length(); x++) { // for each character in the row
                // if the block info for that character isn't null, then there is a block there, so the update block map
                blockMap[x][y] = (key.get(row.charAt(x))) != null;
            }
        }

        // create the block materials and the blocks
        for (int i = 0; i < rows.size(); i++) { // for each row
            int y = i + diff; // the y for this row is i + the difference in rows
            String row = rows.get(rows.size() - 1 - i).getValue(); // get the row string
            for (int x = 0; x < row.length(); x++) { // for each x position in that row
                BlockInfo bi = key.get(row.charAt(x)); // get the block info corresponding to that character
                if (bi != null) { // if a block is actually supposed to go there

                    // create the material map key list to see if a corresponding material already exists
                    List<Object> mmKey = new ArrayList<>(); // start as empty list
                    mmKey.add(bi); // add block info to list
                    if (bi.texturePaths.size() > 0) mmKey.add(bi.texturePaths.get((int) (Math.random() *
                            bi.texturePaths.size()))); // if there are textures, choose a random one to use
                    else mmKey.add(NO_TEXTURE); // otherwise use the no texture flag to denote a lack of textures
                    List<Boolean> cut = new ArrayList<>(); // create boolean list to store cut flags
                    // get connectivity and add to slopes list in the process if applicable
                    Connectivity c = getConnectivity(x, y, blockMap, bi, slopeList, cut);
                    mmKey.add(c); // add connectivity to the map
                    mmKey.add(cut); // add cuts to the map

                    // get and use material or create it if non-existent yet
                    Material m = mm.get(mmKey); // get the material
                    if (m == null) { // if the material doesn't exist, need to create it
                        // if there is no texture, create the material using just the block info's color
                        if (bi.texturePaths.size() < 1) m = new Material(bi.color);
                        else { // otherwise create the material using a formatted texture
                            Texture base = new Texture((Utils.Path) mmKey.get(1)); // get base texture
                            // apply animation if the texture is animated
                            if (bi.animated()) base.animate(bi.animFrames, bi.animTime, true);
                            // format the texture
                            Texture formatted = createTexture(base, bi.overlayTextures, c, bi.cutRadius, cut,
                                    bi.slopes && c.toString().toLowerCase().contains("corner"), sp, model);
                            // if the texture is animated, add it to the animated textures list
                            if (formatted instanceof AnimatedTexture) ats.add((AnimatedTexture) formatted);
                            m = new Material(formatted); // create a new material using the formatted texture
                            base.cleanup();
                        }
                        mm.put(mmKey, m); // save in material map
                    }
                    // if no list in blocks exists for the material yet, create a new one
                    blocks.computeIfAbsent(m, k -> new ArrayList<>());
                    blocks.get(m).add(new Pair<>(x, y)); // add the position to that material
                }
            }
        }

        // process slopes
        PhysicsEngine.SlopeType[][] slopeMap = new PhysicsEngine.SlopeType[bmw][bmh]; // create slope map
        for (Pair<Integer> slope : slopeList.keySet()) { // for each slope
            slopeMap[slope.x][slope.y] = slopeList.get(slope); // put in slope map
            blockMap[slope.x][slope.y] = false; // disable position in block map
        }
        return new Object[] { blockMap, slopeMap }; // return block map and slope map
    }

    /**
     * Parses an area node-file's block key child used for loading blocks of an area
     *
     * @param keyData the area's block_key child node from the area node-file
     * @return a mapping from characters that may appear in the layout to corresponding block information
     */
    private static Map<Character, BlockInfo> parseKeyData(Node keyData) {
        Map<Character, BlockInfo> key = new HashMap<>(); // start as empty hashmap
        // create a block info for each child and put it in the key
        for (Node c : keyData.getChildren()) key.put(c.getName().charAt(0), new BlockInfo(c));
        return key; // return the compiled key
    }

    /**
     * Calculates the connectivity piece for the given position in the given block map and with the given block info.
     * This method will do its best with the overlays present in the block info but cannot guarantee that connectivity
     * will be aesthetically perfect if not all types of overlay are given. For the best results, ensure that the
     * block has all five overlay textures provided
     *
     * @param x         the x position of the block to calculate connectivity for
     * @param y         the y position of the block to calculate connectivity for
     * @param blockMap  the block map to use to check for neighbors to calculate connectivity
     * @param bi        the block info whose overlay availability, slope flag, and edge connection flag will help to
     *                  determine connectivity
     * @param cut       flags to populate determining what corners to cut (starting at the top-left and continuing
     *                  counter-clockwise) as given by getConnectivity(). Must be initialized to an array of length four
     * @return the best possible type of connectivity for the block at the given position given its neighbors and its
     * available overlay textures
     */
    private static Connectivity getConnectivity(int x, int y, boolean[][] blockMap, BlockInfo bi,
                                                Map<Pair<Integer>, PhysicsEngine.SlopeType> slopeList,
                                                List<Boolean> cut) {
        // determine which directions are still within bounds of the block map
        boolean leftInBounds = x > 0;
        boolean rightInBounds = x < blockMap.length - 1;
        boolean belowInBounds = y > 0;
        boolean aboveInBounds = y < blockMap[0].length - 1;

        // determine immediate neighbors
        boolean left = (leftInBounds && blockMap[x - 1][y]) || (!leftInBounds && bi.connectsWithEdge); // left
        boolean right = (rightInBounds && blockMap[x + 1][y]) || (!rightInBounds && bi.connectsWithEdge); // right
        boolean below = (belowInBounds && blockMap[x][y - 1]) || (!belowInBounds && bi.connectsWithEdge); // below
        boolean above = (aboveInBounds && blockMap[x][y + 1]) || (!aboveInBounds && bi.connectsWithEdge); // above

        // determine whether to cut each corner
        cut.add(!(above || left));
        cut.add(!(left || below));
        cut.add(!(below || right));
        cut.add(!(right || above));

        // if no overlays, return default
        if (bi.overlayTextures == null) return Connectivity.DEFAULT;

        // check for no connectivity
        if (bi.overlayTextures.get(BlockInfo.OverlayType.single) != null) {
            if (!(left || right || below || above)) return Connectivity.NONE;
        }

        // check for connectivity with only a single neighbor
        if (bi.overlayTextures.get(BlockInfo.OverlayType.cap) != null) {
            if (left && !(right || below || above)) return Connectivity.RIGHT_CAP;
            if (right && !(below || above || left)) return Connectivity.LEFT_CAP;
            if (below && !(above || left || right)) return Connectivity.ABOVE_CAP;
            if (above && !(left || right || below)) return Connectivity.BELOW_CAP;
        }

        // determine diagonals and whether edges, corners, or insets are available
        boolean aboveLeft = (aboveInBounds && leftInBounds && blockMap[x - 1][y + 1])
                || ((!aboveInBounds || !leftInBounds) && bi.connectsWithEdge);
        boolean aboveRight = (aboveInBounds && rightInBounds && blockMap[x + 1][y + 1])
                || ((!aboveInBounds || !rightInBounds) && bi.connectsWithEdge);
        boolean belowLeft = (belowInBounds && leftInBounds && blockMap[x - 1][y - 1])
                || ((!belowInBounds || !leftInBounds) && bi.connectsWithEdge);
        boolean belowRight = (belowInBounds && rightInBounds && blockMap[x + 1][y - 1])
                || ((!belowInBounds || !rightInBounds) && bi.connectsWithEdge);
        boolean cornerAvailable = bi.overlayTextures.get(BlockInfo.OverlayType.corner) != null;
        boolean edgeAvailable = bi.overlayTextures.get(BlockInfo.OverlayType.edge) != null;
        boolean insetAvailable = bi.overlayTextures.get(BlockInfo.OverlayType.inset) != null;

        // check for connectivity with two neighbors via corners (two adjacent neighbors)
        if (cornerAvailable) {

            // check for top left (inset) corner
            if (below && right && !(above || left)) {
                if (bi.slopes) slopeList.put(new Pair<>(x, y), PhysicsEngine.SlopeType.PositiveBottom);
                if (!belowRight && insetAvailable) return Connectivity.TOP_LEFT_CORNER_INSET;
                else return Connectivity.TOP_LEFT_CORNER;
            }

            // check for top right (inset) corner
            if (below && left && !(above || right)) {
                if (bi.slopes) slopeList.put(new Pair<>(x, y), PhysicsEngine.SlopeType.NegativeBottom);
                if (!belowLeft && insetAvailable) return Connectivity.TOP_RIGHT_CORNER_INSET;
                else return Connectivity.TOP_RIGHT_CORNER;
            }

            // check for bottom left (inset) corner
            if (above && right && !(below || left)) {
                if (bi.slopes) slopeList.put(new Pair<>(x, y), PhysicsEngine.SlopeType.NegativeTop);
                if (!aboveRight && insetAvailable) return Connectivity.BOTTOM_LEFT_CORNER_INSET;
                else return Connectivity.BOTTOM_LEFT_CORNER;
            }

            // check for bottom right (inset) corner
            if (above && left && !(below || right)) {
                if (bi.slopes) slopeList.put(new Pair<>(x, y), PhysicsEngine.SlopeType.PositiveTop);
                if (!aboveLeft && insetAvailable) return Connectivity.BOTTOM_RIGHT_CORNER_INSET;
                else return Connectivity.BOTTOM_RIGHT_CORNER;
            }
        }

        // check for connectivity with two neighbors via column or row (two neighbors on opposite sides)
        if (edgeAvailable) {
            if (left && right && !(below || above)) return Connectivity.ROW;
            if (above && below && !(left || right)) return Connectivity.COLUMN;

            // check for connectivity with three neighbors via left edge (inset & intersection)
            if (!left && right && above && below) {
                if (insetAvailable) {
                    if (!aboveRight && !belowRight) return Connectivity.LEFT_EDGE_T_INTERSECTION;
                    else if (!aboveRight) return Connectivity.LEFT_EDGE_ABOVE_INSET;
                    else if (!belowRight) return Connectivity.LEFT_EDGE_BELOW_INSET;
                }
                return Connectivity.LEFT_EDGE;
            }

            // check for connectivity with three neighbors via right edge (inset & intersection)
            if (!right && above && below && left) {
                if (insetAvailable) {
                    if (!aboveLeft && !belowLeft) return Connectivity.RIGHT_EDGE_T_INTERSECTION;
                    else if (!aboveLeft) return Connectivity.RIGHT_EDGE_ABOVE_INSET;
                    else if (!belowLeft) return Connectivity.RIGHT_EDGE_BELOW_INSET;
                }
                return Connectivity.RIGHT_EDGE;
            }

            // check for connectivity with three neighbors via above edge (inset & intersection)
            if (!above && below && left && right) {
                if (insetAvailable) {
                    if (!belowLeft && !belowRight) return Connectivity.ABOVE_EDGE_T_INTERSECTION;
                    else if (!belowLeft) return Connectivity.ABOVE_EDGE_LEFT_INSET;
                    else if (!belowRight) return Connectivity.ABOVE_EDGE_RIGHT_INSET;
                }
                return Connectivity.ABOVE_EDGE;
            }

            // check for connectivity with three neighbors via below edge (inset & intersection)
            if (!below && left && right && above) {
                if (insetAvailable) {
                    if (!aboveLeft && !aboveRight) return Connectivity.BELOW_EDGE_T_INTERSECTION;
                    else if (!aboveLeft) return Connectivity.BELOW_EDGE_LEFT_INSET;
                    else if (!aboveRight) return Connectivity.BELOW_EDGE_RIGHT_INSET;
                }
                return Connectivity.BELOW_EDGE;
            }
        }

        // check for connectivity with all four neighbors
        if (insetAvailable) {

            // check for connectivity with all four neighbors but no diagonal neighbors
            if (!(aboveLeft || aboveRight || belowLeft || belowRight)) return Connectivity.FOUR_WAY_INTERSECTION;

            // check with connectivity with all four neighbors and one diagonal neighbor
            if (aboveLeft && !(aboveRight || belowRight || belowLeft)) return Connectivity.ALL_INSET_BUT_ABOVE_LEFT;
            if (aboveRight && !(belowRight || belowLeft || aboveLeft)) return Connectivity.ALL_INSET_BUT_ABOVE_RIGHT;
            if (belowRight && !(belowLeft || aboveLeft || aboveRight)) return Connectivity.ALL_INSET_BUT_BELOW_RIGHT;
            if (belowLeft && !(aboveLeft || aboveRight || belowRight)) return Connectivity.ALL_INSET_BUT_BELOW_LEFT;

            // check for connectivity with all four neighbors and two adjacent diagonal neighbors
            if (!(aboveLeft || aboveRight) && belowLeft && belowRight) return Connectivity.ABOVE_INSETS;
            if (!(aboveRight || belowRight) && aboveLeft && belowLeft) return Connectivity.RIGHT_INSETS;
            if (!(belowLeft || belowRight) && aboveLeft && aboveRight) return Connectivity.BELOW_INSETS;
            if (!(aboveLeft || belowLeft) && aboveRight && belowRight) return Connectivity.LEFT_INSETS;

            // check for connectivity with all four neighbors and two opposite diagonal neighbors
            if (!(aboveLeft || belowRight) && aboveRight && belowLeft) return Connectivity.NEGATIVE_SLOPE_INSETS;
            if (!(aboveRight || belowLeft) && aboveLeft && belowRight) return Connectivity.POSITIVE_SLOPE_INSETS;

            // check for connectivity with all four neighbors and three diagonal neighbors
            if (!aboveLeft && aboveRight && belowLeft && belowRight) return Connectivity.ABOVE_LEFT_INSET;
            if (!aboveRight && belowLeft && belowRight && aboveLeft) return Connectivity.ABOVE_RIGHT_INSET;
            if (!belowLeft && belowRight && aboveLeft && aboveRight) return Connectivity.BELOW_LEFT_INSET;
            if (!belowRight && aboveLeft && aboveRight && belowLeft) return Connectivity.BELOW_RIGHT_INSET;
        }

        // if none of the above worked, return default
        return Connectivity.DEFAULT;
    }

    /**
     * Lists all possible types of connectivity. To see the conditions to satisfy any given type of connectivity, see
     * the getConnectivity() method
     */
    private enum Connectivity {
        DEFAULT, NONE, RIGHT_CAP, LEFT_CAP, ABOVE_CAP, BELOW_CAP, COLUMN, ROW, TOP_LEFT_CORNER, TOP_LEFT_CORNER_INSET,
        TOP_RIGHT_CORNER, TOP_RIGHT_CORNER_INSET, BOTTOM_LEFT_CORNER, BOTTOM_LEFT_CORNER_INSET, BOTTOM_RIGHT_CORNER,
        BOTTOM_RIGHT_CORNER_INSET, LEFT_EDGE, LEFT_EDGE_ABOVE_INSET, LEFT_EDGE_BELOW_INSET, LEFT_EDGE_T_INTERSECTION,
        ABOVE_EDGE, ABOVE_EDGE_LEFT_INSET, ABOVE_EDGE_RIGHT_INSET, ABOVE_EDGE_T_INTERSECTION, RIGHT_EDGE,
        RIGHT_EDGE_ABOVE_INSET, RIGHT_EDGE_BELOW_INSET, RIGHT_EDGE_T_INTERSECTION, BELOW_EDGE, BELOW_EDGE_LEFT_INSET,
        BELOW_EDGE_RIGHT_INSET, BELOW_EDGE_T_INTERSECTION, ABOVE_LEFT_INSET, ABOVE_RIGHT_INSET, BELOW_LEFT_INSET,
        BELOW_RIGHT_INSET, LEFT_INSETS, ABOVE_INSETS, RIGHT_INSETS, BELOW_INSETS, NEGATIVE_SLOPE_INSETS,
        POSITIVE_SLOPE_INSETS, FOUR_WAY_INTERSECTION, ALL_INSET_BUT_ABOVE_LEFT, ALL_INSET_BUT_ABOVE_RIGHT,
        ALL_INSET_BUT_BELOW_LEFT, ALL_INSET_BUT_BELOW_RIGHT
    }

    /**
     * Creates the correct texture for a block info using the given connectivity retrieved from getConnectivity().
     *
     * @param base      the base texture to use
     * @param overlays  the mapping from overlay type to overlay texture of the block info that was used to get the given
     *                  connectivity
     * @param c         the connectivity of the corresponding block
     * @param cutRadius the cut radius to use
     * @param cut       flags determining what corners to cut (starting at the top-left and continuing counter-clockwise) as
     *                  given by getConnectivity()
     * @param slope     whether the corresponding block should be cut as a slope
     * @param sp        the block formatting shader program created via beginBlockFormatting
     * @param m         the square model to use for formatting block textures
     * @return the created texture
     */
    private static Texture createTexture(Texture base, Map<BlockInfo.OverlayType, Texture> overlays, Connectivity c,
                                         float cutRadius, List<Boolean> cut, boolean slope, ShaderProgram sp,
                                         Model m) {
        // create empty lists for overlay to apply and their corresponding rotations
        List<Texture> overlaysToApply = new ArrayList<>();
        List<Integer> rotations = new ArrayList<>();

        // check for proper cut flag list size
        if (cut.size() != 4) // if the flag list is not the correct size
            Utils.handleException(new Exception("Invalid size of cut flag list: '" + cut.size() + "'"), Block.class,
                    "createTexture", true); // crash

        // determine which overlays need applied and at what rotation
        if (!c.equals(Connectivity.DEFAULT)) { // default connectivity gets no texture formatting

            // get enum value as string for easier .contains() calls
            String cs = c.toString();

            // no connectivity
            if (c.equals(Connectivity.NONE)) { // if it has no connectivity
                overlaysToApply.add(overlays.get(BlockInfo.OverlayType.single)); // use a single overlay
                rotations.add(0); // with no rotation
            }

            // cap connectivity
            else if (cs.contains("CAP")) {
                // for caps, add the cap overlay with the appropriate rotation
                overlaysToApply.add(overlays.get(BlockInfo.OverlayType.cap));
                int r = 0;
                switch (c) {
                    case LEFT_CAP:
                        r = 1;
                        break;
                    case BELOW_CAP:
                        r = 2;
                        break;
                    case RIGHT_CAP:
                        r = 3;
                        break;
                }
                rotations.add(r);
            }

            // corner connectivity
            else if (cs.contains("CORNER")) {
                // for corners, add the corner and the opposite corner's inset if there is one
                overlaysToApply.add(overlays.get(BlockInfo.OverlayType.corner));
                boolean inset = cs.contains("INSET"); // determine if the opposite corner should have an inset
                if (inset) overlaysToApply.add(overlays.get(BlockInfo.OverlayType.inset));
                switch (c) {
                    case TOP_LEFT_CORNER:
                    case TOP_LEFT_CORNER_INSET:
                        rotations.add(0);
                        if (inset) rotations.add(2);
                        break;
                    case BOTTOM_LEFT_CORNER:
                    case BOTTOM_LEFT_CORNER_INSET:
                        rotations.add(1);
                        if (inset) rotations.add(3);
                        break;
                    case BOTTOM_RIGHT_CORNER:
                    case BOTTOM_RIGHT_CORNER_INSET:
                        rotations.add(2);
                        if (inset) rotations.add(0);
                        break;
                    case TOP_RIGHT_CORNER:
                    case TOP_RIGHT_CORNER_INSET:
                        rotations.add(3);
                        if (inset) rotations.add(1);
                        break;
                }
            }

            // row or column connectivity
            else if (c == Connectivity.ROW || c == Connectivity.COLUMN) {
                // for rows or columns add to edges
                overlaysToApply.add(overlays.get(BlockInfo.OverlayType.edge));
                overlaysToApply.add(overlays.get(BlockInfo.OverlayType.edge));
                // and rotate them depending on whether its a row or a columns
                if (c == Connectivity.ROW) {
                    rotations.add(0);
                    rotations.add(2);
                } else {
                    rotations.add(1);
                    rotations.add(3);
                }
            }

            // edge connectivity
            else if (cs.contains("EDGE")) {
                boolean inset = cs.contains("INSET"); // determine whether the edge has an additional inset
                boolean tInt = cs.contains("T_INTERSECTION"); // determine whether it is a t-intersection

                // add the correct overlays baesd on edge, inset, and/or t-intersection
                overlaysToApply.add(overlays.get(BlockInfo.OverlayType.edge));
                if (inset || tInt) overlaysToApply.add(overlays.get(BlockInfo.OverlayType.inset));
                if (tInt) overlaysToApply.add(overlays.get(BlockInfo.OverlayType.inset));

                /* for each kind of edge, add the correct rotation for the edge itself as well as the rotations for the
                 * additional insets if there is one (or two in the case of t-intersection) */
                if (cs.contains("ABOVE_EDGE")) {
                    rotations.add(0);
                    if (c == Connectivity.ABOVE_EDGE_LEFT_INSET || tInt) rotations.add(1);
                    if (c == Connectivity.ABOVE_EDGE_RIGHT_INSET || tInt) rotations.add(2);
                } else if (cs.contains("LEFT_EDGE")) {
                    rotations.add(1);
                    if (c == Connectivity.LEFT_EDGE_ABOVE_INSET || tInt) rotations.add(3);
                    if (c == Connectivity.LEFT_EDGE_BELOW_INSET || tInt) rotations.add(2);
                } else if (cs.contains("BELOW_EDGE")) {
                    rotations.add(2);
                    if (c == Connectivity.BELOW_EDGE_LEFT_INSET || tInt) rotations.add(0);
                    if (c == Connectivity.BELOW_EDGE_RIGHT_INSET || tInt) rotations.add(3);
                } else if (cs.contains("RIGHT_EDGE")) {
                    rotations.add(3);
                    if (c == Connectivity.RIGHT_EDGE_ABOVE_INSET || tInt) rotations.add(0);
                    if (c == Connectivity.RIGHT_EDGE_BELOW_INSET || tInt) rotations.add(2);
                }
            }

            // only inset connectivity
            else {
                // create flags for which insets need to be added (in counter-clockwise order starting from top-left)
                boolean ins0 = true, ins1 = true, ins2 = true, ins3 = true;

                if (cs.contains("ALL_INSET_BUT")) { // for ALL_INSETS_BUT, just remove the excluded corner
                    switch (c) {
                        case ALL_INSET_BUT_ABOVE_LEFT:
                            ins0 = false;
                            break;
                        case ALL_INSET_BUT_BELOW_LEFT:
                            ins1 = false;
                            break;
                        case ALL_INSET_BUT_BELOW_RIGHT:
                            ins2 = false;
                            break;
                        case ALL_INSET_BUT_ABOVE_RIGHT:
                            ins3 = false;
                            break;
                    }

                } else if (cs.contains("SLOPE")) { // for slopes, remove the two insets off of the slope
                    switch (c) {
                        case NEGATIVE_SLOPE_INSETS: // negative slope means keep the top left and bottom right
                            ins1 = ins3 = false;
                            break;
                        case POSITIVE_SLOPE_INSETS: // positive slope means keep the bottom left and top right
                            ins0 = ins2 = false;
                            break;
                    }

                } else if (cs.contains("INSETS")) { // for adjacent insets, remove the opposite two insets
                    switch (c) {
                        case ABOVE_INSETS:
                            ins1 = ins2 = false;
                            break;
                        case LEFT_INSETS:
                            ins2 = ins3 = false;
                            break;
                        case RIGHT_INSETS:
                            ins0 = ins1 = false;
                            break;
                        case BELOW_INSETS:
                            ins0 = ins3 = false;
                            break;
                    }

                } else { // for just one inset, remove all but the single specified inset
                    switch (c) {
                        case ABOVE_LEFT_INSET:
                            ins1 = ins2 = ins3 = false;
                            break;
                        case BELOW_LEFT_INSET:
                            ins0 = ins2 = ins3 = false;
                            break;
                        case BELOW_RIGHT_INSET:
                            ins0 = ins1 = ins3 = false;
                            break;
                        case ABOVE_RIGHT_INSET:
                            ins0 = ins1 = ins2 = false;
                            break;
                    }
                }

                // add the overlays and rotations based on the inset values computed above
                Texture i = overlays.get(BlockInfo.OverlayType.inset);
                if (ins0) { // top left inset
                    overlaysToApply.add(i);
                    rotations.add(0);
                }
                if (ins1) { // bottom left inset
                    overlaysToApply.add(i);
                    rotations.add(1);
                }
                if (ins2) { // bottom right inset
                    overlaysToApply.add(i);
                    rotations.add(2);
                }
                if (ins3) { // top right inset
                    overlaysToApply.add(i);
                    rotations.add(3);
                }
            }
        }

        // format texture according to overlays, rotation, and cut
        return applyBlockTextureFormatting(base, overlaysToApply, rotations, cut.get(0), cut.get(3), cut.get(1),
                cut.get(2), slope, cutRadius, sp, m);
    }

    /**
     * Denotes the beginning of block formatting by creating a block formatting shader program
     *
     * @return returns the block formatting shader program
     */
    private static ShaderProgram beginBlockFormatting() {
        // create shader program
        ShaderProgram sp = new ShaderProgram(new Utils.Path("/shaders/format_block_vertex.glsl", true),
                new Utils.Path("/shaders/format_block_fragment.glsl", true));
        // register uniforms
        sp.registerUniform("base");
        for (int i = 0; i < MAX_OVERLAYS; i++) {
            sp.registerUniform("overlays[" + i + "]");
            sp.registerUniform("rotations[" + i + "]");
        }
        sp.registerUniform("frames");
        sp.registerUniform("cutTopLeft");
        sp.registerUniform("cutTopRight");
        sp.registerUniform("cutBottomLeft");
        sp.registerUniform("cutBottomRight");
        sp.registerUniform("cutRadius");
        sp.registerUniform("slope");
        return sp; // return created and initialized shader program
    }

    /**
     * Denotes the ending of block formatting by cleaning up the given shader program and resetting the GL viewport to
     * the correct size based on the given window
     *
     * @param sp the shader program that was used for block formatting
     */
    private static void endBlockFormatting(ShaderProgram sp) {
        sp.cleanup(); // cleanup the shader program
        // change GL viewport back to the window's framebuffer size
        glViewport(0, 0, Global.gameWindow.getFBWidth(), Global.gameWindow.getFBHeight());
    }

    /**
     * Modifies a block texture based on the given parameters. This can be called multiple times to apply multiple
     * overlay textures. To reduce wasted GPU power, only cut during the first call to this method in such scenarios.
     * This method requires that the block formatting shader program has been created and initialized. This can be
     * done by calling beginBlockFormatting(). This method also takes a model which should be a simple square model
     * whose width and height should be 2. The shader program and model are taken as parameters with the idea that the
     * calling method will be calling this many times at once and should thus keep a reference to both instead of
     * re-creating them every call
     *
     * @param base           the base block texture
     * @param overlays       the textures to overlay onto the base texture. If empty, no overlay textures will be
     * @param rotations      how to rotate the overlays, kept as a parallel list, when applying it to the base texture.
     *                       The following values are accepted: 0 - no rotation; 1 - 90 degrees of rotation; 2 - 180
     *                       degrees of rotation; 3 - 270 degreees of rotation
     * @param cutTopLeft     whether to apply a cut to the top-left corner
     * @param cutTopRight    whether to apply a cut to the top-right corner
     * @param cutBottomLeft  whether to apply a cut to the bottom-left corner
     * @param cutBottomRight whether to apply a cut to the bottom-right corner
     * @param slope          whether the corresponding block should be cut as a slope
     * @param cutRadius      the radius to use when applying cuts. See BlockInfo's constructor for more info on cuts and cut
     *                       radius
     * @param sp             the block formatting shader program created via beginBlockFormatting
     * @param m              the square model to use for formatting block textures
     * @return the formatted texture
     */
    public static Texture applyBlockTextureFormatting(Texture base, List<Texture> overlays, List<Integer> rotations,
                                                      boolean cutTopLeft, boolean cutTopRight, boolean cutBottomLeft,
                                                      boolean cutBottomRight, boolean slope, float cutRadius,
                                                      ShaderProgram sp, Model m) {
        // create the FBO and the texture attachment
        int[] IDs = Utils.createFBOWithTextureAttachment(base.getWidth(), base.getHeight());

        // combine the textures by rendering them to the frame buffer using the block formatting shader program
        glBindFramebuffer(GL_FRAMEBUFFER, IDs[0]); // bind the frame buffer object
        glViewport(0, 0, base.getWidth(), base.getHeight()); // set the viewport to the size of the texture
        glClear(GL_COLOR_BUFFER_BIT);
        sp.bind(); // bind the shader program
        sp.setUniform("base", 0); // set the base texture sampler uniform
        sp.setUniform("cutRadius", cutRadius); // set the cut radius uniform
        sp.setUniform("cutTopLeft", cutTopLeft ? 1 : 0); // set the top-left cutting uniform
        sp.setUniform("cutTopRight", cutTopRight ? 1 : 0); // set the top-right cutting uniform
        sp.setUniform("cutBottomLeft", cutBottomLeft ? 1 : 0); // set the bottom-left cutting uniform
        sp.setUniform("cutBottomRight", cutBottomRight ? 1 : 0); // set the bottom-right cutting uniform
        // set the frames uniform based on the base texture's animation properties
        sp.setUniform("frames", base instanceof AnimatedTexture ? ((AnimatedTexture) base).getFrameCount() : 1);
        sp.setUniform("slope", slope ? 1 : 0); // set slopes uniform
        glActiveTexture(GL_TEXTURE0); // set active texture to the one in slot 0
        glBindTexture(GL_TEXTURE_2D, base.getID()); // bind base texture to slot 0
        for (int i = 0; i < MAX_OVERLAYS; i++) {
            glActiveTexture(GL_TEXTURE1 + i); // activate the corresponding texture slot
            if (i < overlays.size()) { // if there are more overlays
                // set the overlay and rotation uniforms
                sp.setUniform("overlays[" + i + "]", i + 1);
                sp.setUniform("rotations[" + i + "]", rotations.get(i));
                glBindTexture(GL_TEXTURE_2D, overlays.get(i).getID()); // bind the overlay texture
            } else { // if there are no more overlays
                // unbind textures
                glBindTexture(GL_TEXTURE_2D, 0);
                // set uniforms to appropriate values to denote no more overlays
                sp.setUniform("rotations[" + i + "]", -1);
                sp.setUniform("overlays[" + i + "]", 0);
            }
        }
        m.render(); // render the model
        sp.unbind(); // unbind the shader program

        // post render
        glBindFramebuffer(GL_FRAMEBUFFER, 0); // unbind the frame buffer object
        glDeleteFramebuffers(IDs[0]); // delete the frame buffer object
        Texture formatted = new Texture(IDs[1], base.getWidth(), base.getHeight()); // create the formatted texture
        if (base instanceof AnimatedTexture) { // if the base texture was animated
            AnimatedTexture at = (AnimatedTexture) base; // cast it to an animated texture
            return formatted.animate(at.getFrameCount(), at.getFrameTime(), true); // animate formatted tex
        } else return formatted; // otherwise return vanilla texture
    }

    /**
     * Extends a normal model by providing optimizations for rendering many blocks at once. Specifically, it will simply
     * render a bunch of blocks at once using the same model, only updating the position in between each rather than
     * enabling and then disabling VAOs and VBOs for each individual block
     */
    public static class BlockModel extends Model {

        /**
         * Constructor
         */
        public BlockModel() {
            super(Model.getGridRectModelCoords(1, 1), Model.getStdRectTexCoords(), Model.getStdRectIdx());
        }

        /**
         * Renders the set of blocks corresponding to the given list of block positions. Note that all blocks in the
         * given list should have the same material and that this method should be called once for each material
         *
         * @param sp      the shader program to use to render
         * @param blocks  the positions of the blocks to render
         * @param camView the axis-aligned bounding box to check the block's position against. If the block is not
         *                within the camera's view, it will not be rendered
         * @return the amount of blocks rendered
         */
        public int renderBlocks(ShaderProgram sp, List<Pair<Integer>> blocks, PhysicsEngine.AABB camView) {
            int renderCount = 0; // keep track of amount of blocks rendered
            glBindVertexArray(this.ids[0]); // bind vao
            glEnableVertexAttribArray(0); // enable model coordinate vbo
            glEnableVertexAttribArray(1); // enable texture coordinate vbo
            for (Pair<Integer> b : blocks) { // loop through all blocks
                if (camView.contains(b.x, b.y)) { // if the block is within the camera's view
                    // set the position of the block in the shader program
                    sp.setUniform("x", Transformation.getCenterOfCellComponent(b.x));
                    sp.setUniform("y", Transformation.getCenterOfCellComponent(b.y));
                    glDrawElements(GL_TRIANGLES, this.idx, GL_UNSIGNED_INT, 0); // draw model at that position
                    renderCount++; // iterate render count
                }
            }
            glDisableVertexAttribArray(0); // disable model coordinate vbo
            glDisableVertexAttribArray(1); // disable texture coordinate vbo
            glBindVertexArray(0); // disable vao
            return renderCount; // return amount of rendered blocks
        }
    }

    /**
     * Encapsulates info about a block as laid out in a node-file. For info on node-files, see utils.Node. For info on
     * how nodes are loaded, see utils.NodeLoader. See the constructor for info on how to format a block info node. When
     * corresponding blocks are created, the block info is no longer used. Thus, block info is just used for loading
     * purposes
     */
    public static class BlockInfo {

        /**
         * Defines the types of texture overlays. See BlockInfo's constructor for more info on overlays
         */
        public enum OverlayType {
            corner, edge, cap, single, inset, slope
        }

        /**
         * Members
         */
        public List<Utils.Path> texturePaths = new ArrayList<>(); // a list of texture paths to randomize over
        public float[] color = new float[]{1f, 1f, 1f, 1f};       // block color
        public Material.BlendMode bm;                             // how to blend color and texture in the block
        public Map<OverlayType, Texture> overlayTextures;         // map from overlay types to corresponding textures
        public float animTime;                                    // length in seconds of a frame if block is animated
        public float cutRadius;                                   // radius of cuts made to corner blocks
        public int animFrames;                                    // how many frames there are if block is animated
        public boolean slopes;                                    // whether the block is a slope in corners
        public boolean connectsWithEdge;                          // whether blocks conncect with edge of block map

        /**
         * Constructs the block info by compiling the information from a given node. BlockInfo nodes can use res(from)
         * statements. See utils.NodeLoader for info on res(from) statements. A block info node can have the following
         * children:
         * <p>
         * - texture_paths [optional][default: no texture]: specifies what paths to look for textures at. This node
         * itself should have one or more children nodes formatted as path nodes. If more than one texture path is
         * specified, a random one will be chosen when the corresponding block is created. See utils.Utils.Path for
         * more information on path nodes
         * <p>
         * - overlay_info [optional][default: no overlays]: specifies necessary information to use overlays on the
         * block. Overlays are textures that can be applied on top of the normal texture during edge/corner detection.
         * For a example, if the block is positioned such that it is a corner piece of a group of blocks, it will apply
         * the texture at the path: [overlay_path]_corner.[ext] on top of the normal texture, if it exists. The
         * extension will be assumed from the first provided texture path's extension. Overlays allow for more aesthetic
         * corners/edges in groups of blocks. Note that overlays can only be applied to textured blocks and that
         * overlays cannot be animated though the underlying texture may still be. An overlay_info node should have the
         * following children: (1): a valid path node describing the overlay_path base path for overlay image files, and
         * (2) 'extension': the image file extension, including the dot (ex: '.png'). The following are the possible
         * overlay texture paths that may be searched for and the circumstances under which they would be searched for:
         * - [overlay_path]_edge: used for edges - when a side of the block contains no neighbor and no adjacent sides
         * also contain no neighbor. If two adjacent sides contain no neighbor, a corner overlay is used. If two
         * opposite sides contain no neighbor, two edge overlays will be used - one for each edge. The texture for edges
         * should have the edge on the top by default
         * - [overlay_path]_corner: used only for corners - when only two adjacent sides of the block contain no
         * neighbor. If three sides (adjacent or not) contain no neighbor, a cap overlay is used. The texture for
         * corners should have the corner on the top-left by default
         * - [overlay_path]_slope: used in place of the corner overlays if the block slopes. The texture for slopes
         * should have a positive slope with the sides of the triangle on the bottom and right by default
         * - [overlay_path]_cap: used only when three sides of the block contain no neighbor. Adjacency is implied as it
         * is impossible to have three collectively non-adjacent sides of a rectangle. The texture for caps should have
         * the cap on the top by default
         * - [overlay_path]_single: used when all four sides of the block contain no neighbor
         * - [overlay_path]_inset: used when two adjacent sides contain a neighbor but the diagonal between the two
         * adjacent sides is empty. For example, if the block is neighbored by other blocks on its left and above but
         * there is no block diagonally above and to the left, and inset will be used on the top-left corner of the
         * block. The texture for insets should have the inset on the top-left by default
         * <p>
         * - color [optional][default: 1f 1f 1f 1f]: specifies what color to use for the block
         * <p>
         * - blend_mode [optional][default: none]: specifies how to blend color and texture. The options are: (1) none -
         * no blending will occur. The block will appear as its texture if it has one, or its color if there is no
         * texture. (2) multiplicative - the components of the block color and the components of the texture will be
         * multiplied to create a final color. (3) averaged - the components of the block color and the components of
         * the texture will be averaged to create a final color
         * <p>
         * - animation_frames [optional][default: 1][0, 20]: specifies how many animation frames are in the texture.
         * Frames should be placed in horizontal order and should be equal widths. If 1 (by default), no animation will
         * occur
         * <p>
         * - animation_time [optional][default: 1.0f][0.01f, INFINITY]: specifies how long (in seconds) each frame
         * should appear if the block is animated
         * <p>
         * - cut_radius [optional][default: 0.5f][0f, 1f]: specifies the radius of the circle to use for cutting
         * corners. 0f represents no cutting and 1f represents cutting any content outside of a circle whose radius is
         * half of the entire block off of the texture. For example, if this is set to 0.5f and a corresponding block is
         * at the top-right corner of a group of blocks, then anything outside of the radius of 1/4 of the block's total
         * side relative to the top-right corner will be cut off, if within the top-right quadrant of the corresponding
         * circle. Note that cuts are only applied to textured blocks
         * <p>
         * - slopes [optional][default: false]: specifies whether the block should become sloped when in a corner
         * position
         * <p>
         * - connect_with_edge [optional][default: false]: specifies whether this block, when on the edge, should
         * treat the edge as more blocks (useful for block backdrops) when calculating connectivity
         *
         * @param data the node containing the info to create the blocks info with
         */
        public BlockInfo(Node data) {

            /*
             * Load block information using node loader
             */
            data = NodeLoader.checkForFromStatement("BlockInfo", data);
            Map<String, Object> blockInfo = NodeLoader.loadFromNode("BlockInfo", data,
                    new NodeLoader.LoadItem[]{
                            new NodeLoader.LoadItem<>("texture_paths", null, Node.class)
                                    .useTest((v, sb) -> {
                                boolean issue = false;
                                for (Node child : ((Node) v).getChildren()) {
                                    Utils.Path p = new Utils.Path(child);
                                    if (!p.exists()) {
                                        sb.append("Texture at path does not exist: '").append(p).append('\n');
                                        issue = true;
                                    } else this.texturePaths.add(p);
                                }
                                return !issue;
                            }),
                            new NodeLoader.LoadItem<>("overlay_info", null, Node.class),
                            new NodeLoader.LoadItem<>("color", "1f 1f 1f 1f", String.class)
                                    .useTest((v, sb) -> {
                                float[] c = Utils.strToColor(v);
                                if (c == null) {
                                    sb.append("Must be four valid rgba float values separated by a space");
                                    sb.append("\nFor example: '1f 0f 1f 0.5' for a half-transparent purple");
                                    return false;
                                }
                                this.color = c;
                                return true;
                            }),
                            new NodeLoader.LoadItem<>("blend_mode", "none", String.class)
                                    .setAllowedValues(new String[]{"none", "multiplicative", "averaged"}),
                            new NodeLoader.LoadItem<>("animation_frames", 1, Integer.class)
                                    .setLowerBound(1).setUpperBound(20),
                            new NodeLoader.LoadItem<>("animation_time", 1f, Float.class)
                                    .setLowerBound(0.1f),
                            new NodeLoader.LoadItem<>("cut_radius", 0.5f, Float.class)
                                    .setLowerBound(0f).setUpperBound(1f),
                            new NodeLoader.LoadItem<>("slopes", false, Boolean.class),
                            new NodeLoader.LoadItem<>("connect_with_edge", false, Boolean.class)
                    });

            /*
             * Apply loaded information
             */
            // save blend mode as member
            this.bm = Material.BlendMode.valueOf(((String) blockInfo.get("blend_mode")).toUpperCase());
            this.animFrames = (Integer) blockInfo.get("animation_frames"); // save animation frames as member
            this.animTime = (Float) blockInfo.get("animation_time"); // save animation time as member
            this.cutRadius = (Float) blockInfo.get("cut_radius"); // save cut radius as member
            this.slopes = (Boolean) blockInfo.get("slopes"); // save slopes flag as member
            this.connectsWithEdge = (Boolean) blockInfo.get("connect_with_edge"); // save edge connection flag as member
            if (this.texturePaths.size() > 0) { // if the block is textured
                Node overlayData = (Node) blockInfo.get("overlay_info"); // get overlay info
                if (overlayData != null) { // if overlay info was supplied, load overlay info using node loader
                    overlayData = NodeLoader.checkForFromStatement("overlay_info", overlayData);
                    Map<String, Object> overlayInfo = NodeLoader.loadFromNode("overlay_info", overlayData,
                            new NodeLoader.LoadItem[]{
                                    new NodeLoader.LoadItem<>("resource_path", null, Node.class),
                                    new NodeLoader.LoadItem<>("data_dir_path", null, Node.class),
                                    new NodeLoader.LoadItem<>("extension", null, String.class)
                                            .makeRequired()
                            });
                    for (Node c : overlayData.getChildren()) // for each child in the overlay data node
                        if (c.getName().toLowerCase().contains("path")) // if a path is found
                            createOverlays(new Utils.Path(c), (String) overlayInfo.get("extension")); // create overlays
                }
            }
        }

        /**
         * Creates overlay textures for the block info using the parsed overlay path. This method should not be called
         * if the block info doesn't have a texture or an overlay path
         */
        private void createOverlays(Utils.Path path, String extension) {
            this.overlayTextures = new HashMap<>(); // create a new mapping for the overlay textures
            // assume the file extension for the overlay image is the same as the first texture path
            for (OverlayType ot : OverlayType.values()) { // for each kind of overlay
                Utils.Path sp = path.add("_" + ot.toString() + extension); // create the supposed path
                // if that path exists, create a texture using the image at the path
                if (sp.exists()) this.overlayTextures.put(ot == OverlayType.slope && this.slopes
                                ? OverlayType.corner : ot, new Texture(sp)); // slopes put in place of corners
            }
        }

        /**
         * Creates a material based off of the block info. The resulting material should be re-used as much as possible
         *
         * @param animated whether to allow the resulting material to have an animated texture if the block info
         *                 supports it
         * @return a material based off of the block inf
         */
        public Material createMaterial(boolean animated) {
            Texture t = null; // texture is null if none specified
            if (this.texturePaths.size() > 0) { // if there are texture paths
                // choose a random texture path and create a texture with it
                t = new Texture(this.texturePaths.get((int) (Math.random() * this.texturePaths.size())));
                // animate the texture if the block info supports it and the animated flag is set to true
                if (this.animated() && animated) t = t.animate(this.animFrames, this.animTime, true);
            }
            return new Material(t, this.color, this.bm); // create and return corresponding material
        }

        /**
         * @return whether the corresponding blocks are animated (whether there is more than one animation frame)
         */
        public boolean animated() {
            return this.animFrames > 1;
        }

        /**
         * Cleans up the block info by cleaning up overlay textures if they exist
         */
        public void cleanup() {
            // cleanup overlay textures if there are any
            if (this.overlayTextures != null) for (Texture t : this.overlayTextures.values()) t.cleanup();
        }
    }
}