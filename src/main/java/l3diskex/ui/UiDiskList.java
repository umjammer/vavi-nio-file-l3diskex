package l3diskex.ui;

import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.basicfmt.BasicFmt.DiskBasics;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicParam;
import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImageCreator;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskResult;
import vavi.util.win32.WAVE;


public class UiDiskList extends JTree {

    public static class DiskPositionData {
        private final int diskNum;
        private int typeNum;
        private final int sideNum;
        private final int pos;
        private final boolean editable;
        private boolean shown;
        private DiskBasicDirItem ditem;

        public static final int DISKNUM_ROOT = -1;
        public static final int TYPENUM_NODE = -1;
        public static final int TYPENUM_NODE_AB = -2;
        public static final int TYPENUM_NODE_BOTH = -3;
        public static final int TYPENUM_NODE_DIR = -4;

        public DiskPositionData(int nDisknum, int nTypenum, int nSidenum, int nPos, boolean nEditable, DiskBasicDirItem nDitem) {
            diskNum = nDisknum;
            typeNum = nTypenum;
            sideNum = nSidenum;
            pos = nPos;
            editable = nEditable;
            ditem = nDitem;
            shown = false;
        }

        public int getDiskNumber() {
            return diskNum;
        }

        public int getTypeNumber() {
            return typeNum;
        }

        public void setTypeNumber(int val) {
            typeNum = val;
        }

        public int getSideNumber() {
            return sideNum;
        }

        public int getPosition() {
            return pos;
        }

        public boolean getEditable() {
            return editable;
        }

        public boolean isShown() {
            return shown;
        }

        public void setShown(boolean val) {
            shown = val;
        }

        public DiskBasicDirItem getDiskBasicDirItem() {
            return ditem;
        }

        public void setDiskBasicDirItem(DiskBasicDirItem val) {
            ditem = val;
        }
    }

    private final JFrame parent;
    private final UiDiskFrame frame;
    private JPopupMenu popupMenu;
    private DiskImageDisk selectedDisk;
    private boolean diskSelecting;
    private boolean initialized;
    private DefaultMutableTreeNode rootNode;
    private DefaultMutableTreeNode draggingNode;

    public static final int IDM_SAVE_DISK = 1;
    public static final int IDM_ADD_DISK_NEW = 2;
    public static final int IDM_ADD_DISK_FROM_FILE = 3;
    public static final int IDM_REPLACE_DISK_FROM_FILE = 4;
    public static final int IDM_DELETE_DISK_FROM_FILE = 5;
    public static final int IDM_RENAME_DISK = 6;
    public static final int IDM_DELETE_DIRECTORY = 7;
    public static final int IDM_INITIALIZE_DISK = 8;
    public static final int IDM_FORMAT_DISK = 9;
    public static final int IDM_COPY_FILE = 10;
    public static final int IDM_PASTE_FILE = 11;
    public static final int IDM_PROPERTY_DISK = 12;
    public static final int IDM_PROPERTY_BASIC = 13;

    public UiDiskList(UiDiskFrame parentFrame, JFrame parentWindow) {
        super(new DefaultMutableTreeNode());
        parent = parentWindow;
        frame = parentFrame;
        initialized = false;

        setupIconImages();
        setupFonts();
        setupTree();
        makePopupMenu();
        setupKeyListener();

        clearFileName();
        initialized = true;
    }

    private void setupIconImages() {
        // Setup tree icons
    }

    private void setupFonts() {
        // Set default font
        Font font = frame.getDefaultListFont();
        setFont(font);
    }

    private void setupTree() {
        // Configure JTree properties
        setRootVisible(true);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)getLastSelectedPathComponent();
                if (node != null) {
                    setDataOnItemNode(node, SetDataOnItemNodeFlags.NODE_SELECTED);
                }
            }
        });
    }

    private void setupKeyListener() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch(e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        showDiskAttr();
                        break;
                    case KeyEvent.VK_DELETE:
                        selectDeleting();
                        break;
                }
            }
        });
    }

    private final int[] ICON_INDICES = {
            ICON_FOR_TREE_NONE,
            ICON_FOR_TREE_SINGLE,
            ICON_FOR_TREE_ROOT,
            ICON_FOR_TREE_CLOSE,
            ICON_FOR_TREE_OPEN
    };

    // Event handler methods
    public void onCopyFile(ActionEvent e) {
        copyToClipboard();
    }

    public void onPasteFile(ActionEvent e) {
        pasteFromClipboard();
    }

    public void onBeginDrag(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
        dragDataSource(node);
    }

    public void onContextMenu(MouseEvent e) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            setSelectionPath(path);
            showPopupMenu();
        }
    }

    public void onSelectionChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
        changeSelection(node);
    }

    public void onItemExpanding(TreeExpansionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
        expandItemNode(node);
    }

    public void onStartEditing(TreeModelEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.getChildren()[0];
        DiskPositionData data = (DiskPositionData)node.getUserObject();
        if (!data.getEditable()) {
            cancelEditing();
        }
    }

    public void onEditingDone(TreeModelEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.getChildren()[0];
        DiskPositionData data = (DiskPositionData)node.getUserObject();
        if (data.getDiskNumber() < 0 || !data.getEditable()) {
            return;
        }

        String newName = node.toString();
        if (selectedDisk.setDiskName(WAVE.data.getDiskNumber(), newName)) {
            ((DefaultTreeModel)getModel()).nodeChanged(node);
        }
    }

    // Other methods converted from C++...
    public void saveDisk() {
        showSaveDiskDialog();
    }

    public void replaceDisk() {
        DiskPositionData data = getSelectedNodeData();
        if (data == null) return;

        String caption = getSubCaption(WAVE.data.getTypeNumber(), WAVE.data.getPosition());
        frame.showReplaceDiskDialog(WAVE.data.getDiskNumber(), WAVE.data.getPosition(), caption);
    }

    public void deleteDisk() {
        if (selectedDisk == null) return;

        DiskPositionData data = getSelectedNodeData();

        int ans = JOptionPane.showConfirmDialog(
                this,
                String.format("'%s' will be deleted. Do you really want to delete it?", selectedDisk.getName()),
                "Delete Disk",
                JOptionPane.YES_NO_OPTION
        );

        if (ans == JOptionPane.YES_OPTION) {
            boolean success = frame.getDiskImage().delete(WAVE.data.getDiskNumber());
            if (success) {
                frame.updateDataOnWindow(false);
                frame.closeAllFileAttr();
            }
        }
    }

    public void setFileName(String filename) {
        DiskImageCreator.DiskImageDisks disks = frame.getDiskImage().getDisks();
        if (disks == null) return;

        deleteAllItems();

        DefaultMutableTreeNode root = addRootNode(filename, ICON_FOR_TREE_ROOT, ICON_FOR_TREE_NONE,
                new DiskPositionData(DISKNUM_ROOT, TYPENUM_NODE, -1, 0, false, null));
        rootNode = root;

        for (int i = 0; i < disks.size(); i++) {
            DiskImageDisk disk = disks.get(i);
            // Add single disk
            addContainer(root, disk.getName(), ICON_FOR_TREE_SINGLE, ICON_FOR_TREE_NONE,
                    new DiskPositionData(i, TYPENUM_NODE, TYPENUM_NODE, TYPENUM_NODE, true));
        }

        expandNode(root);
        selectNode(root);
        frame.clearDiskAttrData();
    }

    public void setFilePath(String filepath) {
        if (filepath != null && !filepath.isEmpty()) {
            setNodeText(rootNode, filepath);
        } else {
            setNodeText(rootNode, "(none)");
        }
    }

    public int getSelectedDiskNumber() {
        if (selectedDisk == null) return -1;
        DiskPositionData data = getNodeData(getLastSelectedPathComponent());
        if (data == null) return -1;
        return data.getDiskNumber();
    }

    public int getSelectedDiskSide() {
        if (selectedDisk == null) return -1;
        DiskPositionData data = getNodeData(getLastSelectedPathComponent());
        if (data == null) return -1;
        return data.getPosition();
    }

    public void getSelectedDisk(int[] diskInfo) {
        if (selectedDisk == null) return;
        DiskPositionData data = getNodeData(getLastSelectedPathComponent());
        if (data == null) return;
        diskInfo[0] = data.getDiskNumber();
        diskInfo[1] = data.getPosition();
    }

    public boolean isSelectedDiskSide() {
        return diskSelecting && selectedDisk != null && selectedDisk.isReversible();
    }

    public void showDiskAttr() {
        if (selectedDisk == null) return;

        DiskParamDialog dlg = new DiskParamDialog(this, frame.getDiskImage(), selectedDisk);
        int result = dlg.showDialog();

        if (result == JOptionPane.OK_OPTION) {
            selectedDisk.setName(dlg.getDiskName());
            selectedDisk.setDensity(dlg.getDensityValue());
            selectedDisk.setWriteProtect(dlg.isWriteProtected());
            selectedDisk.setModified(true);

            // Update disk name
            setDiskName(selectedDisk.getName());

            // Update disk attributes
            frame.setDiskAttrData(selectedDisk);
        }
    }

    // Additional helper methods...

    private DiskPositionData getNodeData(Object node) {
        if (node instanceof DefaultMutableTreeNode) {
            Object data = ((DefaultMutableTreeNode)node).getUserObject();
            if (data instanceof DiskPositionData) {
                return (DiskPositionData)data;
            }
        }
        return null;
    }

    private void setNodeText(DefaultMutableTreeNode node, String text) {
        getModel().valueForPathChanged(new TreePath(node.getPath()), text);
    }

    private void expandNode(DefaultMutableTreeNode node) {
        expandPath(new TreePath(node.getPath()));
    }

    private void selectNode(DefaultMutableTreeNode node) {
        setSelectionPath(new TreePath(node.getPath()));
    }

    public void deleteChildrenOnSelectedDisk() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)getLastSelectedPathComponent();
        if(node == null) return;

        DiskPositionData data = (DiskPositionData)node.getUserObject();
        if(data == null) return;

        int diskNumber = data.getDiskNumber();
        node = findNodeByDiskNumber(rootNode, diskNumber);
        if(node == null) return;

        data = (DiskPositionData)node.getUserObject();

        collapsePath(new TreePath(node.getPath()));
        node.removeAllChildren();
        data.setTypeNumber(CD_TYPENUM_NODE);
        data.setShown(false);
        setSelectionPath(new TreePath(node.getPath()));
    }

    public void refreshSelectedDisk(DiskBasicParam newParam) {
        deleteChildrenOnSelectedDisk();
        reSelect(newParam);
    }

    public void refreshSelectedSide(DiskBasicParam newParam) {
        if(newParam != null && !newParam.canMountEachSides()) {
            deleteChildrenOnSelectedDisk();
        }
        reSelect(newParam);
    }

    public void setFileName() {
        setFileName(frame.getFileName());
    }

    public void clearFileName() {
        removeAll();

        DefaultMutableTreeNode root = addRootTreeNode("(none)");
        rootNode = root;

        expandPath(new TreePath(root.getPath()));

        selectedDisk = null;
        diskSelecting = false;
    }

    public void makePopupMenu() {
        popupMenu = new JPopupMenu();

        JMenuItem saveDisk = new JMenuItem("Save Disk...");
        saveDisk.addActionListener(e -> showSaveDiskDialog());
        popupMenu.add(saveDisk);

        JMenu addDiskMenu = new JMenu("Add Disk");
        JMenuItem newDisk = new JMenuItem("New Disk...");
        newDisk.addActionListener(e -> frame.showAddNewDiskDialog());
        JMenuItem fromFile = new JMenuItem("From File...");
        fromFile.addActionListener(e -> frame.showAddFileDialog());
        addDiskMenu.add(newDisk);
        addDiskMenu.add(fromFile);
        popupMenu.add(addDiskMenu);

        JMenuItem replaceDisk = new JMenuItem("Replace Disk...");
        replaceDisk.addActionListener(e -> replaceDisk());
        popupMenu.add(replaceDisk);

        JMenuItem deleteDisk = new JMenuItem("Delete Disk...");
        deleteDisk.addActionListener(e -> deleteDisk());
        popupMenu.add(deleteDisk);

        JMenuItem renameDisk = new JMenuItem("Rename Disk");
        renameDisk.addActionListener(e -> renameDisk());
        popupMenu.add(renameDisk);

        JMenuItem deleteDir = new JMenuItem("Delete Directory...");
        deleteDir.addActionListener(e -> deleteDirectory());
        popupMenu.add(deleteDir);

        JMenuItem initDisk = new JMenuItem("Initialize...");
        initDisk.addActionListener(e -> initializeDisk());
        popupMenu.add(initDisk);

        JMenuItem formatDisk = new JMenuItem("Format...");
        formatDisk.addActionListener(e -> formatDisk());
        popupMenu.add(formatDisk);

        popupMenu.addSeparator();

        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(e -> copyToClipboard());
        popupMenu.add(copy);

        JMenuItem paste = new JMenuItem("Paste...");
        paste.addActionListener(e -> pasteFromClipboard());
        popupMenu.add(paste);

        popupMenu.addSeparator();

        JMenuItem diskInfo = new JMenuItem("Disk Information...");
        diskInfo.addActionListener(e -> showDiskAttr());
        popupMenu.add(diskInfo);

        JMenuItem basicInfo = new JMenuItem("BASIC Information...");
        basicInfo.addActionListener(e -> frame.showBasicAttr());
        popupMenu.add(basicInfo);
    }

    public void showPopupMenu() {
        if (popupMenu == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)getLastSelectedPathComponent();
        if (node == null) return;

        DiskPositionData data = (DiskPositionData)node.getUserObject();
        boolean opened = (data != null);

        // Enable/disable menu items based on selection
        boolean diskSelected = (opened && selectedDisk != null);

        Component[] items = popupMenu.getComponents();
        for (Component item : items) {
            if (item instanceof JMenuItem) {
                JMenuItem menuItem = (JMenuItem)item;
                String text = menuItem.getText();

                if (text.contains("Add Disk")) {
                    menuItem.setEnabled(opened);
                } else if (text.contains("Replace") || text.contains("Save") ||
                        text.contains("Delete") || text.contains("Rename") ||
                        text.contains("Initialize") || text.contains("Disk Information")) {
                    menuItem.setEnabled(diskSelected);
                } else if (text.contains("Format") || text.contains("BASIC Information")) {
                    menuItem.setEnabled(diskSelected && diskSelecting);
                } else if (text.contains("Delete Directory")) {
                    menuItem.setEnabled(diskSelected && data.getTypeNumber() == TYPENUM_NODE_DIR);
                }
            }
        }

        // Show popup menu at current mouse location
        Point p = getMousePosition();
        if (p != null) {
            popupMenu.show(this, p.x, p.y);
        }
    }

    public boolean dragDataSource(TreePath selectedPath) {
        String tempDirName = "";
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();

        // Create composite data object
        Path fileObj = new Path();
        boolean success = createFileObject(node, tempDirName, "Dragging...", "Dragged.", fileObj);

        if (!success) {
            return false;
        }

        // Remember dragged node
        draggingNode = node;

        // Create drag source
        DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(this,
                DnDConstants.ACTION_COPY_OR_MOVE,
                new DragGestureListener() {
                    @Override
                    public void dragGestureRecognized(DragGestureEvent dge) {
                        ds.startDrag(dge, DragSource.DefaultCopyDrop, fileObj,
                                new DragSourceAdapter());
                    }
                });

        return true;
    }

    public boolean createFileObject(DefaultMutableTreeNode node, String tempDirName,
                                    String startMsg, String endMsg, Path fileObj) {

        List<TreePath> selectedItems = new ArrayList<>();
        selectedItems.add(new TreePath(node.getPath()));

        String tempDataPath = "";
        String tempAttrPath = "";

        // Create temp folder
        if (!frame.createTemporaryFolder(tempDirName, tempDataPath, tempAttrPath)) {
            return false;
        }

        // Export files
        exportDataFiles(selectedItems, tempDataPath, tempAttrPath, startMsg, endMsg, fileObj);

        return true;
    }

    public int exportDataFiles(List<TreePath> selectedItems, String dataDir,
                               String attrDir, String startMsg, String endMsg, Path fileObj) {

        frame.startExportCounter(0, startMsg);

        // Build list of selected files
        int selCount = selectedItems.size();
        List<DiskBasicDirItem> dirItems = new ArrayList<>();

        for (int i = 0; i < selCount; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                    selectedItems.get(i).getLastPathComponent();
            DiskPositionData data = (DiskPositionData)node.getUserObject();
            DiskBasicDirItem item = data.getDiskBasicDirItem();
            if (item != null) {
                dirItems.add(item);
            }
        }

        int status = 0;

        // Export files
        if (!dirItems.isEmpty()) {
            DiskBasic basic = dirItems.get(0).getBasic();
            if (basic != null) {
                status = frame.exportDataFiles(basic, dirItems, dataDir, attrDir,
                        fileObj, 0);
                if (status != 0) {
                    basic.showErrorMessage();
                }
            } else {
                status = -1;
            }
        }

        frame.finishExportCounter(endMsg);

        return status;
    }

    public boolean dropDataFiles(JComponent base, int x, int y, List<String> paths, boolean dirIncluded) {
        if (paths.isEmpty()) {
            return false;
        }

        Point basePoint = SwingUtilities.convertPoint(base, x, y, this);
        TreePath path = getClosestPathForLocation(basePoint.x, basePoint.y);

        if (path == null) {
            return false;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();

        // Don't drop on dragged node
        if (node.equals(draggingNode)) {
            draggingNode = null;
            return true;
        }

        DiskPositionData data = (DiskPositionData)node.getUserObject();
        DiskBasicDirItem dirItem = data.getDiskBasicDirItem();

        if (dirItem == null) {
            if (dirIncluded) {
                return false;
            }
            // Add as new disk if dropped on disk image
            return frame.preAddDiskFile(paths.get(0));
        }

        DiskBasic dirBasic = dirItem.getBasic();
        if (dirBasic == null) {
            return false;
        }

        // Check if formatted and writable
        if (!dirBasic.isFormatted()) {
            return false;
        }
        if (!dirBasic.isWritableIntoDisk()) {
            dirBasic.showErrorMessage();
            return false;
        }

        // Assign directory structure
        dirBasic.assignDirectory(dirItem);

        // Import files into target directory
        return frame.importDataFiles(paths, dirBasic, dirItem, dirIncluded,
                "Dropping...", "Dropped.");
    }

    public boolean copyToClipboard() {
        TreePath path = getSelectionPath();
        if (path == null) {
            return false;
        }

        String tempDir = "";
        Path fileObj = new Path();
        boolean success = createFileObject(path, tempDir, "Copying...", "Copied.", fileObj);

        if (success) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(fileObj, null);
            return true;
        }

        return false;
    }

    public boolean pasteFromClipboard() {
        TreePath path = getSelectionPath();
        if (path == null) {
            return false;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        DiskPositionData data = (DiskPositionData)node.getUserObject();
        DiskBasicDirItem dirItem = data.getDiskBasicDirItem();

        if (dirItem == null) {
            return false;
        }

        DiskBasic dirBasic = dirItem.getBasic();
        if (dirBasic == null) {
            return false;
        }

        if (!dirBasic.isFormatted() || !dirBasic.isWritableIntoDisk()) {
            dirBasic.showErrorMessage();
            return false;
        }

        dirBasic.assignDirectory(dirItem);

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        DataFlavor fileFlavor = DataFlavor.javaFileListFlavor;

        if (clipboard.isDataFlavorAvailable(fileFlavor)) {
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>)clipboard.getData(fileFlavor);
                List<String> paths = files.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());

                return frame.importDataFiles(paths, dirBasic, dirItem, false,
                        "Pasting...", "Pasted.");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public void changeSelection(int diskNumber, int sideNumber) {
        DefaultMutableTreeNode matchNode = findNodeByDiskAndSideNumber(rootNode, diskNumber, sideNumber);
        if (matchNode != null) {
            selectTreeNode(matchNode);
            changeSelection(matchNode);
        }
    }

    public DefaultMutableTreeNode findNodeByDiskAndSideNumber(DefaultMutableTreeNode node,
                                                              int diskNumber, int sideNumber, int depth) {

        if (depth >= 100 || !node.getChildCount()) {
            return null;
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();
            DiskPositionData data = (DiskPositionData)child.getUserObject();

            if (data != null && data.getDiskNumber() == diskNumber) {
                if (data.getSideNumber() == sideNumber) {
                    return child;
                } else if (child.getChildCount() > 0) {
                    DefaultMutableTreeNode found = findNodeByDiskAndSideNumber(child,
                            diskNumber, sideNumber, depth + 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    public void selectTreeNode(DefaultMutableTreeNode node) {
        if (node != null) {
            TreePath path = new TreePath(node.getPath());
            setSelectionPath(path);
            scrollPathToVisible(path);
        }
    }

    public DefaultMutableTreeNode addRootTreeNode(String text) {
        return addRootTreeNode(text, IconForTree.ROOT.ordinal(), IconForTree.NONE.ordinal(),
                new DiskPositionData(CD_DISKNUM_ROOT, CD_TYPENUM_NODE, -1, 0, false, null));
    }

    public DefaultMutableTreeNode addRootTreeNode(String text, int icon1, int icon2,
                                                  DiskPositionData data) {

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(text);
        root.setUserObject(data);

        DefaultTreeModel model = new DefaultTreeModel(root);
        setModel(model);

        return root;
    }

    public DefaultMutableTreeNode addTreeContainer(DefaultMutableTreeNode parent,
                                                   String text, int icon1, int icon2, DiskPositionData data) {

        DefaultMutableTreeNode child = new DefaultMutableTreeNode(text);
        child.setUserObject(data);

        ((DefaultTreeModel)getModel()).insertNodeInto(child, parent, parent.getChildCount());

        return child;
    }

    public void refreshRootDirectoryNode(DiskImageDisk disk, DefaultMutableTreeNode node) {
        if (node == null) return;

        DiskPositionData data = (DiskPositionData)node.getUserObject();
        DiskBasic basic = disk.getDiskBasic(data.getSideNumber());
        if (basic == null) return;

        DiskBasicDirItem rootItem = basic.getRootDirectory();
        data.setDiskBasicDirItem(rootItem);
        if (rootItem == null) return;

        refreshDirectorySub(disk, node, data, rootItem);
    }

    private void refreshDirectoryName(DiskImageDisk disk) {
        if (disk == null) return;
        refreshDirectoryName(rootNode, disk.getNumber());
    }

    private void refreshDirectoryName(DefaultMutableTreeNode node, int diskNumber, int depth) {
        if (depth >= 100 || !node.getChildCount()) {
            return;
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();
            DiskPositionData data = (DiskPositionData)child.getUserObject();

            if (data != null && data.getDiskNumber() == diskNumber) {
                if (data.getTypeNumber() == CD_TYPENUM_NODE_DIR) {
                    DiskBasicDirItem dirItem = data.getDiskBasicDirItem();
                    if (dirItem != null) {
                        setNodeText(child, dirItem.getFileNameStr());
                    }
                }
                refreshDirectoryName(child, diskNumber, depth + 1);
            }
        }
    }

    public boolean initializeDisk() {
        if (selectedDisk == null) return false;
        if (selectedDisk.isWriteProtected()) {
            DiskResult err = new DiskResult();
            err.setError(DiskResult.ERR_WRITE_PROTECTED);
            err.show();
            return false;
        }

        DiskPositionData data = getNodeData(getLastSelectedPathComponent());

        int ans = JOptionPane.YES_OPTION;
        String diskName = "'" + selectedDisk.getName() + "'";
        int selectedSide = data.getPosition();
        boolean found = selectedDisk.existTrack(selectedSide);
        boolean status = false;

        if (found) {
            // Initialize existing track
            if (selectedSide >= 0) {
                diskName += Utils.getSideStr(selectedSide,
                        data.getTypeNumber() != CD_TYPENUM_NODE_AB);
            }

            String msg = String.format("All files and data will be deleted on %s. " +
                    "Do you really want to initialize it?", diskName);
            ans = JOptionPane.showConfirmDialog(this, msg, "Initialize Disk",
                    JOptionPane.YES_NO_OPTION);

            if (ans == JOptionPane.YES_OPTION) {
                status = selectedDisk.initialize(selectedSide);
                frame.clearRPanelData();

                DiskBasics basics = selectedDisk.getDiskBasics();
                if (basics != null) {
                    basics.clearParseAndAssign(selectedSide);
                }
                refreshSelectedDisk();
                frame.increaseUniqueNumber();
            }
        } else {
            // Create new track
            if (selectedSide >= 0) {
                // Rebuild selected side only
                DiskParamDialog dlg = new DiskParamDialog(this, frame.getDiskImage(),
                        DiskParamDialog.REBUILD_TRACKS, 0, selectedDisk, null, null, 0);

                int rc = dlg.showDialog();
                if (rc == JOptionPane.OK_OPTION) {
                    DiskParam param = new DiskParam();
                    dlg.getParam(param);
                    selectedDisk.getFile().setBasicTypeHint(dlg.getCategory());
                    status = selectedDisk.rebuild(param, selectedSide);

                    setFileName();
                    frame.increaseUniqueNumber();
                }
            } else {
                // Show parameter selection dialog
                DiskParamDialog dlg = new DiskParamDialog(this, frame.getDiskImage(),
                        DiskParamDialog.REBUILD_TRACKS, -1, selectedDisk, null, null,
                        DiskParamDialog.SHOW_ALL);

                int rc = dlg.showDialog();
                if (rc == JOptionPane.OK_OPTION) {
                    DiskParam param = new DiskParam();
                    dlg.getParam(param);
                    selectedDisk.setName(dlg.getDiskName());
                    selectedDisk.setDensity(dlg.getDensityValue());
                    selectedDisk.setWriteProtect(dlg.isWriteProtected());
                    selectedDisk.getFile().setBasicTypeHint(dlg.getCategory());
                    status = selectedDisk.rebuild(param, selectedSide);

                    setFileName();
                    frame.increaseUniqueNumber();
                }
            }
        }

        return status;
    }

    public boolean formatDisk() {
        if (m_selected_disk == null) return false;

        UiDiskPositionData cd = (UiDiskPositionData) getItemData(getSelection());
        int selected_side = cd.getPosition();

        DiskBasic current_basic = m_selected_disk.getDiskBasic(selected_side);

        if (!current_basic.isWritableIntoDisk()) {
            current_basic.showErrorMessage();
            return false;
        }

        BasicSelBox dlg = new BasicSelBox(this, -1, m_selected_disk, current_basic, BasicSelBox.SHOW_ATTR_CONTROLS);
        int ans = dlg.showModal();
        if (ans != BasicSelBox.OK) {
            return false;
        }

        DiskBasic new_basic = new DiskBasic();
        new_basic.parseBasic(m_selected_disk, selected_side, dlg.getBasicParam(), true);
        if (!new_basic.isFormattable()) {
            new_basic.showErrorMessage();
            return false;
        }

        ans = MessageBox.YES;
        String diskname = "'" + m_selected_disk.getName() + "'";
        diskname += new_basic.getSelectedSideStr();
        String msg = String.format("All files and datas will delete on %s. Do you really want to format it?", diskname);
        ans = MessageBox.show(msg, "Format", MessageBox.YES_NO);

        int sts = 0;
        if (ans == MessageBox.YES) {
            DiskBasicIdentifiedData data = new DiskBasicIdentifiedData(
                    dlg.getVolumeName(),
                    dlg.getVolumeNumber(),
                    dlg.getVolumeDate()
            );

            m_selected_disk.initialize(current_basic.getSelectedSide());
            m_selected_disk.setModify();

            current_basic.clearParseAndAssign();
            if (!current_basic.getBasicTypeName().equals(new_basic.getBasicTypeName())) {
                m_selected_disk.getFile().setBasicTypeHint(new_basic.getBasicCategoryName());
                current_basic.parseBasic(m_selected_disk, selected_side, dlg.getBasicParam(), true);
            }

            sts = current_basic.formatDisk(data);
            if (sts != 0) {
                current_basic.showErrorMessage();
            }
            frame.clearFatAreaData();
            refreshSelectedDisk();

            frame.increaseUniqueNumber();
        }

        return (sts >= 0);
    }

    public void showSaveDiskDialog() {
        UiDiskPositionData cd = (UiDiskPositionData) getItemData(getSelection());
        if (cd == null) return;
        frame.showSaveDiskDialog(cd.getDiskNumber(), cd.getPosition(), cd.getTypeNumber() != UiDiskPositionData.CD_TYPENUM_NODE_AB);
    }

    public void renameDisk() {
        ListModel node = setSelectedItemAtDiskImage();
        if (!isValidItem(node)) return;
        editTreeNode(node);
    }

    public ListModel setSelectedItemAtDiskImage() {
        ListModel invalid = new UiDiskListItem();
        if (m_selected_disk == null) return invalid;
        ListModel node = getSelection();
        if (!isValidItem(node)) return invalid;
        ListModel cd = (UiDiskPositionData) getItemData(node);
        if (cd == null) return invalid;
        if (cd.getPosition() >= 0) {
            node = getParentTreeNode(node);
            if (!isValidItem(node)) return invalid;
            UiDiskPositionData pcd = (UiDiskPositionData) getItemData(node);
            if (pcd.getDiskNumber() != cd.getDiskNumber()) return invalid;
        }
        return node;
    }

    public void setDiskName(String val) {
        ListModel item = setSelectedItemAtDiskImage();
        if (!isValidItem(item)) return;
        setItemText(item, val);
    }

    public void changeCharCode(String name) {
        DiskImage image = frame.getDiskImage();
        if (image == null) return;
        DiskImageCreator.DiskImageDisks disks = image.getDisks();
        if (disks == null) return;
        for (int i = 0; i < disks.count(); i++) {
            refreshDirectoryName(disks.item(i));
        }
    }

    public void setListFont(Object font) {
        setFont(font);
        refresh();
    }

    public boolean dragDataSource(ListModel sel_node) {
        String tmp_dir_name = "";

        Path file_object = new Path();
        boolean sts = createFileObject(sel_node, tmp_dir_name, "dragging...", "dragged.", file_object);

        if (!sts) {
            return false;
        }
        m_dragging_node = sel_node;

        DropSource dragSource = new DropSource(file_object, frame);
        dragSource.doDragDrop();

        return true;
    }

    public boolean createFileObject(UiDiskListItem sel_node, String tmp_dir_name, String start_msg, String end_msg, Path file_object) {
        List<UiDiskListItem> selected_items = new ArrayList<>();
        selected_items.add(sel_node);

        String tmp_data_path = "";
        String tmp_attr_path = "";
        if (!frame.createTemporaryFolder(tmp_dir_name, tmp_data_path, tmp_attr_path)) {
            return false;
        }

        exportDataFiles(selected_items, tmp_data_path, tmp_attr_path, start_msg, end_msg, file_object);

        return true;
    }

    public int exportDataFiles(List<UiDiskListItem> selected_items, String data_dir, String attr_dir, String start_msg, String end_msg, Path file_object) {
        frame.startExportCounter(0, start_msg);

        int selcount = selected_items.size();
        List<DiskBasicDirItem> dir_items = new ArrayList<>();
        for (int i = 0; i < selcount; i++) {
            UiDiskPositionData cd = (UiDiskPositionData) getItemData(selected_items.get(i));
            DiskBasicDirItem item = cd.getDiskBasicDirItem();
            if (item == null) {
                continue;
            }
            dir_items.add(item);
        }

        int sts = 0;
        do {
            if (dir_items.size() <= 0) {
                break;
            }
            DiskBasic basic = dir_items.get(0).getBasic();
            if (basic == null) {
                sts = -1;
                break;
            }
            sts = frame.exportDataFiles(basic, dir_items, data_dir, attr_dir, file_object, 0);
            if (sts != 0) {
                basic.showErrorMessage();
            }
        } while (false);

        frame.finishExportCounter(end_msg);

        return sts;
    }

    public boolean dropDataFiles(Object base, int x, int y, List<String> paths, boolean dir_included) {
        if (paths.isEmpty()) {
            return false;
        }

        int bx = 0;
        int by = 0;
        UiDiskFrame.getPositionFromBaseWindow(base, this, bx, by);
        UiDiskListItem node = getNodeAtPoint(x - bx, y - by);
        if (!isValidItem(node)) {
            return false;
        }
        boolean sts = (node == m_dragging_node);
        m_dragging_node = null;
        if (sts) {
            return true;
        }

        UiDiskPositionData cd = (UiDiskPositionData) getItemData(node);
        DiskBasicDirItem dir_item = cd.getDiskBasicDirItem();
        if (dir_item == null) {
            if (dir_included) {
                return false;
            }
            return frame.preAddDiskFile(paths.get(0));
        }
        DiskBasic dir_basic = dir_item.getBasic();
        if (dir_basic == null) {
            return false;
        }

        if (!dir_basic.isFormatted()) {
            return false;
        }
        if (!dir_basic.isWritableIntoDisk()) {
            dir_basic.showErrorMessage();
            return false;
        }

        dir_basic.assignDirectory(dir_item);

        return frame.importDataFiles(paths, dir_basic, dir_item, dir_included, "dropping...", "dropped.");
    }

    public void getSelectedDisk(int[] disk_number, int[] side_number) {
        if (m_selected_disk == null) return;
        UiDiskPositionData cd = (UiDiskPositionData) getItemData(getSelection());
        if (cd == null) return;
        disk_number[0] = cd.getDiskNumber();
        side_number[0] = cd.getPosition();
    }

    public boolean isSelectedDiskImage() {
        return (m_selected_disk != null);
    }

    public boolean isSelectedDisk() {
        return m_disk_selecting;
    }
}
