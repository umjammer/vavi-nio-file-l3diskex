/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.ui;

import java.awt.Point;
import javax.swing.JTree;


public class UICDTreeCtrl extends JTree {

    /**
     * Constructor
     */
    public UICDTreeCtrl(Object parentWindow, int id) {
        super(parentWindow, id);
    }

    /**
     * Protected method – add icons
     */
    protected void AssignTreeIcons(String[][][] icons) {
        // In wxWidgets this creates a wxImageList; here we simply store the array
        this.iconArray = icons;
    }

    /**
     *  Public API
     */

    /** Select a tree node */
    public void SelectTreeNode(MyCDTreeItem node) {
        this.select(node);
    }

    /** Test if a tree node has children */
    public boolean TreeNodeHasChildren(MyCDTreeItem node) {
        return this.isContainer(node);
    }

    /** Return the number of children of a tree node */
    public int GetTreeChildCount(MyCDTreeItem parent) {
        return this.getChildCount(parent);
    }

    /** Edit a tree node */
    public void EditTreeNode(MyCDTreeItem node) {
        this.editItem(node, this.getColumn(0));
    }

    /** Delete a tree node */
    public void DeleteTreeNode(MyCDTreeItem node) {
        this.deleteItem(node);
    }

    /** Return the parent of a tree node */
    public MyCDTreeItem GetParentTreeNode(MyCDTreeItem node) {
        DataViewTreeStore model = this.getStore();
        if (model == null) return null;
        return model.getParent(node);
    }

    /** Add a root tree node */
    public MyCDTreeItem AddRootTreeNode(String text, int defIcon, int selIcon, ClientData nData) {
        return this.appendContainer(new MyCDTreeItem(0), text, defIcon, selIcon, nData);
    }

    /** Add a container node */
    public MyCDTreeItem AddTreeContainer(MyCDTreeItem parent, String text,
                                         int defIcon, int selIcon, ClientData nData) {
        return this.appendContainer(parent, text, defIcon, selIcon, nData);
    }

    /** Add a leaf node */
    public MyCDTreeItem AddTreeNode(MyCDTreeItem parent, String text,
                                    int defIcon, int selIcon, ClientData nData) {
        return this.appendItem(parent, text, defIcon, nData);
    }

    /** Return the first child of a node */
    public MyCDTreeItem GetFirstChild(MyCDTreeItem parent, int[] cookie) {
        cookie[0] = 0;
        return (cookie[0] < this.getChildCount(parent))
                ? this.getNthChild(parent, cookie[0])
                : null;
    }

    /** Return the next child of a node */
    public MyCDTreeItem GetNextChild(MyCDTreeItem parent, int[] cookie) {
        cookie[0] += 1;
        return (cookie[0] < this.getChildCount(parent))
                ? this.getNthChild(parent, cookie[0])
                : null;
    }

    /** Does a node exist at the given point? */
    public boolean HasNodeAtPoint(int x, int y) {
        Point pt = new Point(x, y);
        MyCDTreeItem item = new MyCDTreeItem();
        DataViewColumn column = null;
        this.hitTest(pt, item, column);
        return item.isOk();
    }

    /** Return the node at the given point */
    public MyCDTreeItem GetNodeAtPoint(int x, int y) {
        Point pt = new Point(x, y);
        MyCDTreeItem item = new MyCDTreeItem();
        DataViewColumn column = null;
        this.hitTest(pt, item, column);
        return item;
    }

    /**
     * Private helper data
     */
    private String[][][] iconArray;   // stores icon paths (placeholder)

}
