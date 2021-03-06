package utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/*
 * Node.java
 * Ambulare
 * Jacob Oaks
 * 4/14/20
 */

/**
 * A tree-like data structure for storing data that can easily be loaded and stored in files
 * A file containing data able to be read and parsed into a node is called a node-file. By convention, node-files
 * should have the extension '.node', but they are technically allowed to have any extension
 */
public class Node {

    /**
     * Members
     */
    private static char DIVIDER_CHAR = ':'; // the character that divides a line in a node-file into the name and value
    private static char INDENT_CHAR = '\t'; // the character used for indenting in a node-file
    private static char COMMENT_CHAR = '#'; // lines starting with this character in node-files will be ignored
    private List<Node> children;            // list of child nodes
    private String name, value;             // name and values

    /**
     * Constructs this node by giving it all of its properties
     *
     * @param name     the name of the node (the string before the dividing character in a node-file)
     * @param value    the value of the node (the string after the dividing node in a node-file)
     * @param children the starting list of children for the node
     */
    public Node(String name, String value, List<Node> children) {
        this.name = name;
        this.value = value; // set value
        this.children = children; // set children
    }

    /**
     * Constructs this node by giving it just a name and a value
     *
     * @param name  the name of the node (the string before the dividing character in a node-file)
     * @param value the value of the node (the string after the dividing node in a node-file)
     */
    public Node(String name, String value) {
        this(name, value, new ArrayList<>());
    }

    /**
     * Constructs this node by giving it just a name
     *
     * @param name the name of the node (the string before the dividing character in a node-file)
     */
    public Node(String name) {
        this(name, "");
    }

    /**
     * Constructs the node without any set properties
     */
    public Node() {
        this("");
    }

    /**
     * @return the children of this node, or an empty array list if there are no children
     */
    public List<Node> getChildren() {
        return this.children == null ? new ArrayList<>() : this.children;
    }

    /**
     * @return the amount of children this node has
     */
    public int getChildCount() {
        return this.children.size();
    }

    /**
     * @return whether or not this node has children
     */
    public boolean hasChildren() {
        return this.children.size() > 0;
    }

    /**
     * Adds a child to this node
     *
     * @param child the child to add
     */
    public void addChild(Node child) {
        this.children.add(child); // add child
    }

    /**
     * Adds a child to this node
     *
     * @param name  the name of the child node to add
     * @param value the value of the child node to add
     */
    public void addChild(String name, String value) {
        this.addChild(new Node(name, value)); // create and add child
    }

    /**
     * Resets the nodes list of children to an empty list
     */
    public void resetChildren() {
        this.children = new ArrayList<>(); // reset to an empty list
    }

    /**
     * If this node has no children, the given children will become its children. Otherwise, the given
     * children will be added one-by-one to this node's list of children.
     *
     * @param children the children to consider
     */
    public void setAddChildren(List<Node> children) {
        if (this.children.size() == 0) this.children = children; // if empty, just replace children (quicker)
        else this.children.addAll(children); // otherwise, add one-by-one
    }

    /**
     * Retrieves the child of this node at the given index.
     *
     * @param index the index of the child to retrieve
     * @return the child at the given index if it exists
     */
    public Node getChild(int index) {
        if (index > this.children.size() || index < 0) { // check for invalid index
            Utils.handleException(new Exception("Unable to access index " + index + " in child array of size " +
                    this.children.size()), this.getClass(), "getChild", true); // throw exception if invalid index
        }
        return this.children.get(index); // return appropriate child otherwise
    }

    /**
     * Searches for a child of this node with the given name
     *
     * @param name the name to search for
     * @return the first child with the matching name, or null if there are none
     */
    public Node getChild(String name) {
        for (Node child : this.children) if (child.getName().equals(name)) return child; // look for matching name
        return null; // return null if can't find
    }

    /**
     * Searches for a child of this node with the given name and, as opposed to getChild, will crash if cannot find
     *
     * @param name the name to search for
     * @return the first child with the matching name
     */
    public Node needChild(String name) {
        for (Node child : this.children) if (child.getName().equals(name)) return child; // look for matching name
        Utils.handleException(new Exception("Unable to find child with name " + name), this.getClass(), "needChild",
                true); // throw exception if can't find
        return null; // here just to make compiler be quiet
    }

    /**
     * @return the name of this node
     */
    public String getName() {
        return this.name;
    }

    /**
     * Updates the name of this node
     *
     * @param name the new name to assign to this node
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the value of this node
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Updates the value of this node
     *
     * @param value the new value to assign to this node
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Converts this node to a string (by returning the same representation that would be found in a node-file)
     *
     * @return the string version of this node
     */
    @Override
    public String toString() {
        StringWriter sw = new StringWriter(); // create string writer
        layoutNode(sw, this, new StringBuilder()); // layout the node onto the string writer
        return sw.toString(); // convert string writer to a string and return the result
    }

    /**
     * Converts a resource node-file to a node (if the node-file is properly formatted) at the given path
     *
     * @param path the path to the node-file
     * @return the created node, or null if the path doesn't exist
     */
    public static Node pathContentsToNode(Utils.Path path) {
        List<String> data = Utils.pathContentsToStringList(path); // read resource
        if (data == null) return null; // if path doesn't exist, return null
        trimData(data); // trim away comments and empty lines
        Node node = new Node(); // create root node
        parseNode(node, data, 0, 0); // parse read data from node-file into root node
        return node; // return parsed node
    }

    /**
     * Removes comment lines and empty from a list of strings (from a node-file) to be parsed into a node
     *
     * @param data the data to remove comment lines from
     */
    private static void trimData(List<String> data) {
        for (int j = 0; j < data.size(); j++) { // for each line
            String line = data.get(j); // get the line
            for (int i = 0; i <= line.length(); i++) { // go through each character
                if (i >= line.length()) { // if reached end of line without finding content
                    data.remove(line); // remove the line
                    j--; // decrement j so as to not skip lines
                    break; // no need to look further in the line
                }
                if (line.charAt(i) == COMMENT_CHAR) { // if we reach the comment character
                    data.remove(line); // remove the line
                    j--; // decrement j so as to not skip lines
                    break; // no need to look further in the line
                }
                // if a character that isn't a space, indent, or comment found -> this line is okay
                else if (line.charAt(i) != ' ' && line.charAt(i) != INDENT_CHAR) break;
            }
        }
    }

    /**
     * Converts a given node to a node-file to be placed at the given path. This will only for for paths that are
     * relative to the data directory
     *
     * @param node the node to convert to a node-file
     * @param path the path to place the node-file
     */
    public static void nodeToFile(Node node, Utils.Path path) {
        try { // try to open and read file
            if (path.isResRelative()) { // if the given path is resource-relative, log and ignore
                Utils.log("Attempted to write the following node:\n" + node + "To the resource-relative path: "
                        + path + ". Ignoring", Node.class, "nodeToFile", false);
                return; // and return
            }
            Utils.ensureDirs(path); // ensure directories exist
            PrintWriter out = new PrintWriter(path.getFile()); // open node-file
            layoutNode(out, node, new StringBuilder()); // recursively layout node to node-file
            out.close(); // close file
        } catch (Exception e) { // if exception
            Utils.handleException(e, Node.class, "nodeToFile", true); // handle exception
        }
    }

    /**
     * Parses given data into a node recursively
     *
     * @param curr the root node
     * @param data the data
     * @param i    the position to start at in the data
     * @param in   how many indents to expect
     * @return the position in the data after parsing this node
     */
    private static int parseNode(Node curr, List<String> data, int i, int in) {

        // format next line and find dividing point
        String line = data.get(i); // get next line
        line = line.substring(in); // remove indent
        int dividerLocation = -1; // location of the divider in line
        for (int j = 0; j < line.length() && dividerLocation == -1; j++) // look through line
            if (line.charAt(j) == Node.DIVIDER_CHAR) dividerLocation = j; // find divider

        // throw error if no divider found
        if (dividerLocation == -1) { // if no divider found
            Utils.handleException(new Exception("Unable to find divider in line " + i + " of given utils.Node data"),
                    Node.class, "parseNode", true); // throw exception
        }

        // create node and set name if there is one
        Node node = new Node(); // create empty node
        String possibleName = line.substring(0, dividerLocation); // get the possible name
        if (!possibleName.equals("")) { // if name is non-empty
            int spaceCutoff = 0; // count amount of pre-pending spaces to cutoff
            while (line.charAt(spaceCutoff) == ' ') spaceCutoff++; // find where spaces end
            curr.setName(line.substring(spaceCutoff, dividerLocation)); // create node with trimmed name
        }

        // set node value if there is one
        String possibleValue = line.substring(dividerLocation + 1); // get possible value
        if (!possibleValue.equals(" ") && !possibleValue.equals("")) { // if possible value has substance
            curr.setValue(possibleValue.substring(1)); // set value (remove first space)
        }

        // check for more file
        if (i + 1 < data.size()) { // if not eof
            if (data.get(i + 1).contains("{")) { // if the node has children
                i += 2; // iterate twice
                in++; // iterate indent
                while (!data.get(i).contains("}")) { // while there are more children
                    Node child = new Node(); // create child node
                    i = parseNode(child, data, i, in); // recursively read child, keep track of file position
                    curr.addChild(child); // add child
                    if ((i + 1) > data.size())  // if unexpected file stop
                        Utils.handleException(new Exception("Unexpected file stop at line " + i +
                                " of given utils.Node data"), Node.class, "parseNode", true); // throw exception
                    i += 1; // iterate i
                }
            }
        }

        // set node data, return
        node.setName(curr.getName()); // set name
        node.setValue(curr.getValue()); // set value
        node.setAddChildren(curr.getChildren()); // add children
        return i; // return position in data list
    }

    /**
     * Lays out a given node to a given writer recursively
     *
     * @param out  the writer to print out to
     * @param node the node to layout
     * @param in   how many indents to print
     */
    private static void layoutNode(Writer out, Node node, StringBuilder in) {
        try { // attempt to write to writer
            String indentString = in.toString(); // convert indents to a string
            out.write(indentString + node.getName() + Node.DIVIDER_CHAR + " " + node.getValue() + "\n"); // put info
            if (node.hasChildren()) { // if the node has children
                out.write(indentString + "{\n"); // print child opening brace
                in.append(Node.INDENT_CHAR); // indent for children
                for (Node child : node.getChildren()) layoutNode(out, child, in); // recursively layout children
                in.deleteCharAt(in.length() - 1); // remove indent used for children
                out.write(indentString + "}\n"); // print child ending brace
            }
        } catch (Exception e) { // if exception
            Utils.handleException(e, Node.class, "layoutNode", true); // handle
        }
    }

    /**
     * Generates an error string to be used as exception messages when the error is related to parsing nodes
     *
     * @param type the type of data structure trying to be constructed from the node
     * @param msg  the error
     * @param path the path to the problematic node-file
     * @return the compiled string
     */
    public static String getNodeParseErrorMsg(String type, String msg, String path) {
        return "Error loading " + type + " at '" + path + "':\n " + msg; // put pieces together and return
    }
}
