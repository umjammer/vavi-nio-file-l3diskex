package l3diskex.ui;

import java.awt.Point;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;


public class UICTreeCtrl extends JTree {

    /*----------------------------------------------------------
     *  Protected members
     *----------------------------------------------------------*/
    protected boolean m_selecting;      // 選択処理中

    /*----------------------------------------------------------
     *  Constructor
     *----------------------------------------------------------*/
    public UICTreeCtrl(JComponent parentwindow, int id) {
        // wxTR_EDIT_LABELS | wxTR_NO_LINES | wxTR_HAS_BUTTONS | wxTR_TWIST_BUTTONS
        super(parentwindow, id, wxDefaultPosition, wxDefaultSize,
                wxTR_EDIT_LABELS | wxTR_NO_LINES | wxTR_HAS_BUTTONS | wxTR_TWIST_BUTTONS);
        this.m_selecting = false;
    }

    /*----------------------------------------------------------
     *  アイコンを追加
     *----------------------------------------------------------*/
    protected void AssignTreeIcons(String[][] icons) {
        wxImageList ilist = new wxImageList(16, 16);
        for (int i = 0; i < icons.length && icons[i] != null; i++) {
            // In C++: ilist->Add(wxIcon(icons[i]));
            // Here we assume icons[i] is a String representing the icon file name.
            ImageIcon icon = new ImageIcon(icons[i][0]);   // take the first string of the sub‑array
            ilist.Add(icon);
        }
        AssignImageList(ilist);
    }

    /*----------------------------------------------------------
     *  ツリーアイテムを選択
     *----------------------------------------------------------*/
    public void SelectTreeNode(TreeNode node) {
        if (!m_selecting) {
            m_selecting = true;
            SelectItem(node);
            m_selecting = false;
        }
    }

    /*----------------------------------------------------------
     *  ツリーノードが子供を持つか
     *----------------------------------------------------------*/
    public boolean TreeNodeHasChildren(TreeNode node) {
        return HasChildren(node);
    }

    /*----------------------------------------------------------
     *  ツリーノードの子供の数を返す
     *----------------------------------------------------------*/
    public int GetTreeChildCount(TreeNode parent) {
        return GetChildrenCount(parent);
    }

    /*----------------------------------------------------------
     *  ツリーノードを編集
     *----------------------------------------------------------*/
    public void EditTreeNode(TreeNode node) {
        editLabel(node);
    }

    /*----------------------------------------------------------
     *  ツリーノードを削除
     *----------------------------------------------------------*/
    public void DeleteTreeNode(TreeNode node) {
        delete(node);
    }

    /*----------------------------------------------------------
     *  親ツリーノードを返す
     *----------------------------------------------------------*/
    public TreeNode GetParentTreeNode(TreeNode node) {
        return GetItemParent(node);
    }

    /*----------------------------------------------------------
     *  ルートノードを追加する
     *----------------------------------------------------------*/
    public TreeNode AddRootTreeNode(String text, int def_icon, int sel_icon, Object n_data) {
        return AddRoot(text, def_icon, sel_icon, n_data);
    }

    /*----------------------------------------------------------
     *  ノードを追加する (コンテナ)
     *----------------------------------------------------------*/
    public TreeNode AddTreeContainer(TreeNode parent, String text,
                                     int def_icon, int sel_icon, Object n_data) {
        TreeNode node = AppendItem(parent, text, def_icon, sel_icon, n_data);
        SetItemHasChildren(node, true);
        return node;
    }

    /*----------------------------------------------------------
     *  ノードを追加する (単純ノード)
     *----------------------------------------------------------*/
    public TreeNode AddTreeNode(TreeNode parent, String text,
                                int def_icon, int sel_icon, Object n_data) {
        TreeNode node = AppendItem(parent, text, def_icon, sel_icon, n_data);
        SetItemHasChildren(node, false);
        return node;
    }

    /*----------------------------------------------------------
     *  指定した座標にノードがあるか
     *----------------------------------------------------------*/
    public boolean HasNodeAtPoint(int x, int y) {
        Point pt = new Point(x, y);
        return HitTest(pt).isOk();
    }

    /*----------------------------------------------------------
     *  指定した座標にあるノードを返す
     *----------------------------------------------------------*/
    public TreeNode GetNodeAtPoint(int x, int y) {
        Point pt = new Point(x, y);
        return HitTest(pt);
    }

    /*----------------------------------------------------------
     *  Private helper methods (stubbed)
     *----------------------------------------------------------*/

    /*  In wxWidgets these are member functions of wxTreeCtrl;
        here they are re‑implemented as protected methods for
        clarity of translation. */
    protected TreeNode AddRoot(String text, int def_icon, int sel_icon, Object n_data) {
        return super.AddRoot(text, def_icon, sel_icon, n_data);
    }

    protected TreeNode AppendItem(TreeNode parent, String text,
                                  int def_icon, int sel_icon, Object n_data) {
        return super.AppendItem(parent, text, def_icon, sel_icon, n_data);
    }

    protected void SetItemHasChildren(TreeNode item, boolean hasChildren) {
        // Stub – no real effect
    }

    protected void AssignImageList(wxImageList list) {
        super.AssignImageList(list);
    }

    protected TreeNode HitTest(Point pt) {
        // Stub – always returns a dummy item
        return new TreeNode();
    }
}
