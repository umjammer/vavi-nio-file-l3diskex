package l3diskex.basicfmt;

import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;


/** ディレクトリアクセス */
public class DiskBasicDir<T extends Directory> {

    private final DiskBasic basic;
    private final DiskBasicFat fat;

    /** フォーマットタイプ */
    private DiskBasicFormat formatType;
    /** ルートディレクトリの仮想的なアイテム */
    private DiskBasicDirItem<T> root;
    /** 現ディレクトリのアイテム */
    private DiskBasicDirItem<T> currentItem;

    public DiskBasicDir(DiskBasic basic) {
        this.basic = basic;
        this.fat = basic.getFat();

        this.root = null;
        this.formatType = null;
        this.currentItem = null;
    }

    /**
     * Creates a new directory item.
     *
     * @return New DiskBasicDirItem
     */
    public DiskBasicDirItem<T> newItem() throws IOException {

        int formatType = FORMAT_TYPE_UNKNOWN;
        if (this.formatType != null) formatType = this.formatType.getTypeNumber();

        ServiceLoader<DiskBasicDirItem> serviceLoader = ServiceLoader.load(DiskBasicDirItem.class);
        for (DiskBasicDirItem<T> basicDirItem : serviceLoader) {
            if (basicDirItem.isSupported(formatType)) {
                basicDirItem.init(basic);
                basicDirItem.clearData();
                return basicDirItem;
            }
        }

        //logger.log(Level.ERROR, "Unknown type is defined in basic_type.xml.");
        //item = new DiskBasicDirItem(basic);
        return null;
    }

    /**
     * Creates and assigns a new directory item.
     *
     * @param sector    Sector
     * @param sectorPos Position within the sector
     * @param data      Buffer within the sector
     * @param dataPos   Buffer pointer
     * @return New DiskBasicDirItem or null
     */
    public DiskBasicDirItem<T> newItem(DiskImageSector sector, int sectorPos,
                                       byte[] data, int dataPos) throws IOException {

        int formatType = FORMAT_TYPE_UNKNOWN;
        if (this.formatType != null) formatType = this.formatType.getTypeNumber();

        ServiceLoader<DiskBasicDirItem> serviceLoader = ServiceLoader.load(DiskBasicDirItem.class);
        for (DiskBasicDirItem<T> basicDirItem : serviceLoader) {
            if (basicDirItem.isSupported(formatType)) {
                basicDirItem.init(basic, sector, sectorPos, data, dataPos);
                basicDirItem.clearData();
                return basicDirItem;
            }
        }

        //logger.log(Level.ERROR, "Unknown type is defined in basic_type.xml.");
        //item = new DiskBasicDirItem(basic, sector, sectorPos, data, dataPos);
        return null;
    }

    /**
     * Creates and assigns a new directory item with group/sector info.
     *
     * @param num       Sequential number
     * @param groupItem Track number data
     * @param sector    Sector
     * @param sectorPos Position within the sector
     * @param data      Buffer within the sector
     * @param next      Next sector info
     * @param unuse     Is unused (output array)
     * @return New DiskBasicDirItem or null
     */
    public DiskBasicDirItem<T> newItem(int num, DiskBasicGroupItem groupItem,
                                       DiskImageSector sector, int sectorPos,
                                       byte[] data, int dataPos,
                                       SectorParam next, boolean[] unuse) throws IOException {
        int formatType = FORMAT_TYPE_UNKNOWN;
        if (this.formatType != null) formatType = this.formatType.getTypeNumber();

        ServiceLoader<DiskBasicDirItem> serviceLoader = ServiceLoader.load(DiskBasicDirItem.class);
        for (DiskBasicDirItem<T> basicDirItem : serviceLoader) {
            if (basicDirItem.isSupported(formatType)) {
                basicDirItem.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);
                basicDirItem.clearData();
                return basicDirItem;
            }
        }

        //logger.log(Level.ERROR, "Unknown type is defined in basic_type.xml.");
        //item = new DiskBasicDirItem(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);
        return null;
    }

    /**
     * Returns the root directory item.
     *
     * @return Root directory item
     */
    public DiskBasicDirItem<T> getRootItem() {
        return root;
    }

    /**
     * Returns the list of root directory children.
     *
     * @param dirItem Output array to receive the root item (can be null)
     * @return List of children in the root directory
     */
    public List<DiskBasicDirItem<T>> getRootItems(DiskBasicDirItem<T>[] dirItem) {
        DiskBasicDirItem<T> item = getRootItem();
        if (dirItem != null && dirItem.length > 0) dirItem[0] = item;
        return getChildren(item);
    }

    /**
     * Returns the current directory item.
     *
     * @return Current directory item
     */
    public DiskBasicDirItem<T> getCurrentItem() {
        return currentItem;
    }

    /**
     * Returns the list of current directory children.
     *
     * @param dirItem Output array to receive the current item (can be null)
     * @return List of children in the current directory
     */
    public List<DiskBasicDirItem<T>> getCurrentItems(DiskBasicDirItem<T>[] dirItem /* = null */) {
        DiskBasicDirItem<T> item = getCurrentItem();
        if (dirItem != null && dirItem.length > 0) dirItem[0] = item;
        return getChildren(item);
    }

    /**
     * Returns the list of children in a directory.
     *
     * @param dirItem Directory item
     * @return List of children or null
     */
    public List<DiskBasicDirItem<T>> getChildren(DiskBasicDirItem<T> dirItem) {
        if (dirItem == null) return null;
        return dirItem.getChildren();
    }

    /**
     * Clears all directory items in the current directory.
     */
    public void emptyChildrenInCurrent() {
        emptyChildren(getCurrentItem());
    }

    /**
     * Clears all directory items in a directory.
     *
     * @param dirItem Directory item
     */
    public void emptyChildren(DiskBasicDirItem<T> dirItem) {
        if (dirItem != null) dirItem.emptyChildren();
    }

    /**
     * Sets the root as the current directory.
     */
    public void setCurrentAsRoot() {
        currentItem = root;
    }

    /**
     * Returns the parent directory item of the current directory.
     *
     * @return Parent directory item
     */
    public DiskBasicDirItem<T> getParentItemOnCurrent() {
        return getParentItem(getCurrentItem());
    }

    /**
     * Returns the parent directory item.
     *
     * @param dirItem Directory item
     * @return Parent directory item or null
     */
    public DiskBasicDirItem<T> getParentItem(DiskBasicDirItem<T> dirItem) {
        DiskBasicDirItem<T> item = null;
        if (dirItem != null) {
            item = dirItem.getParent();
        }
        return item;
    }

    /**
     * Returns a directory item pointer by index in the current directory.
     *
     * @param index Index
     * @return Directory item or null
     */
    public DiskBasicDirItem<T> item(int index) {
        List<DiskBasicDirItem<T>> items = getCurrentItems(null);
        if (items == null || index < 0 || index >= items.size()) return null;
        return items.get(index);
    }

    /**
     * Returns an unused directory item in the current directory.
     *
     * @param pItem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItemOnCurrent(DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        return getEmptyItem(getCurrentItem(), getCurrentItems(null), pItem, nextItem);
    }

    /**
     * Returns an unused directory item in the root directory.
     *
     * @param pItem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItemOnRoot(DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        return getEmptyItem(getRootItem(), getRootItems(null), pItem, nextItem);
    }

    /**
     * Returns an unused directory item in the specified directory.
     *
     * @param dirItem  Directory
     * @param pItem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItem(DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        return getEmptyItem(dirItem, getChildren(dirItem), pItem, nextItem);
    }

    /**
     * Returns an unused directory item in the specified directory.
     *
     * @param dirItem  Directory
     * @param children List of directory items in dirItem
     * @param pItem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItem(DiskBasicDirItem<T> dirItem, List<DiskBasicDirItem<T>> children, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        DiskBasicType<T> type = basic.getType();
        return type.getEmptyDirectoryItem(dirItem, children, pItem, nextItem);
    }

    /**
     * Checks if a file with the same name already exists in the current directory.
     *
     * @param filename    Filename
     * @param iCase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileOnCurrent(DiskBasicFileName filename, boolean iCase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        return findFile(getCurrentItem(), filename, iCase, excludeItem, nextItem);
    }

    /**
     * Checks if a file with the same name already exists in the specified directory.
     *
     * @param dirItem     Directory item to search
     * @param filename    Filename
     * @param iCase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFile(DiskBasicDirItem<T> dirItem, DiskBasicFileName filename, boolean iCase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        DiskBasicDirItem<T> matchItem = null;
        List<DiskBasicDirItem<T>> items = dirItem.getChildren();
        if (items != null) {
            for (int pos = 0; pos < items.size(); pos++) {
                DiskBasicDirItem<T> item = items.get(pos);
                if (item != excludeItem && item.isSameFileName(filename, iCase)) {
                    matchItem = item;
                    if (nextItem != null && nextItem.length > 0) {
                        pos++;
                        if (pos < items.size()) {
                            nextItem[0] = items.get(pos);
                        } else {
                            nextItem[0] = null;
                        }
                    }
                    break;
                }
            }
        }
        return matchItem;
    }

    /**
     * Checks if a file with the same name already exists in the current directory.
     *
     * @param targetItem  Target item to compare with
     * @param iCase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileOnCurrent(DiskBasicDirItem<T> targetItem, boolean iCase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        return findFile(getCurrentItem(), targetItem, iCase, excludeItem, nextItem);
    }

    /**
     * Checks if a file with the same name already exists in the specified directory.
     *
     * @param dirItem     Directory item to search
     * @param targetItem  Target item to compare with
     * @param icase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFile(DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> targetItem, boolean icase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<T> matchItem = null;
        List<DiskBasicDirItem<T>> items = dirItem.getChildren();
        if (items != null) {
            for (int pos = 0; pos < items.size(); pos++) {
                DiskBasicDirItem<T> item = items.get(pos);
                if (item != excludeItem && item.isSameFileName(targetItem, icase)) {
                    matchItem = item;
                    if (nextItem != null && nextItem.length > 0) {
                        pos++;
                        if (pos < items.size()) {
                            nextItem[0] = items.get(pos);
                        } else {
                            nextItem[0] = null;
                        }
                    }
                    break;
                }
            }
        }
        return matchItem;
    }

    /**
     * Checks if a file with the same name (excluding extension) exists in the current directory.
     *
     * @param name        Filename (excluding extension)
     * @param iCase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findNameOnCurrent(String name, boolean iCase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        return findName(getCurrentItem(), name, iCase, excludeItem, nextItem);
    }

    /**
     * Checks if a file with the same name (excluding extension) exists in the specified directory.
     *
     * @param dirItem     Directory item to search
     * @param name        Filename (excluding extension)
     * @param iCase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findName(DiskBasicDirItem<T> dirItem, String name, boolean iCase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<T> matchItem = null;
        List<DiskBasicDirItem<T>> items = dirItem.getChildren();
        if (items != null) {
            for (int pos = 0; pos < items.size(); pos++) {
                DiskBasicDirItem<T> item = items.get(pos);
                if (item != excludeItem && item.isSameName(name, iCase)) {
                    matchItem = item;
                    if (nextItem != null && nextItem.length > 0) {
                        pos++;
                        if (pos < items.size()) {
                            nextItem[0] = items.get(pos);
                        } else {
                            nextItem[0] = null;
                        }
                    }
                    break;
                }
            }
        }
        return matchItem;
    }

    /**
     * Searches for a file matching the attributes in the current directory.
     *
     * @param fileType Target attributes
     * @param mask     Bitmask to exclude from search
     * @param prevItem Previous matched item (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileByAttrOnCurrent(int fileType, int mask, DiskBasicDirItem<T> prevItem) {
        return findFileByAttr(getCurrentItem(), fileType, mask, prevItem);
    }

    /**
     * Searches for a file matching the attributes in the root directory.
     *
     * @param fileType Target attributes
     * @param mask     Bitmask to exclude from search
     * @param prevItem Previous matched item (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileByAttrOnRoot(int fileType, int mask, DiskBasicDirItem<T> prevItem /* = null */) {
        return findFileByAttr(getRootItem(), fileType, mask, prevItem);
    }

    /**
     * Searches for a file matching the attributes in the specified directory.
     *
     * @param dirItem  Directory item to search
     * @param fileType Target attributes
     * @param mask     Bitmask to exclude from search
     * @param prevItem Previous matched item (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileByAttr(DiskBasicDirItem<T> dirItem, int fileType, int mask, DiskBasicDirItem<T> prevItem) {
        if (dirItem == null) return null;

        DiskBasicDirItem<T> matchItem = null;
        int start = 0;
        List<DiskBasicDirItem<T>> items = dirItem.getChildren();
        if (items != null) {
            if (prevItem != null) {
                start = items.indexOf(prevItem);
                start++;
            }
            for (int pos = start; pos < items.size(); pos++) {
                DiskBasicDirItem<T> item = items.get(pos);
                if (item.getFileAttr().matchType(mask, fileType & mask)) {
                    matchItem = item;
                    break;
                }
            }
        }
        return matchItem;
    }

    /**
     * Checks the root directory.
     *
     * @param type         DISK BASIC type
     * @param startSector  Starting sector number
     * @param endSector    Ending sector number
     * @param isFormatting Is formatting
     * @return Check result (&lt; 0.0 on error)
     */
    public double checkRoot(DiskBasicType<T> type, int startSector, int endSector, boolean isFormatting) throws IOException {
        DiskBasicGroups rootGroups = new DiskBasicGroups();
        return type.checkRootDirectory(startSector, endSector, rootGroups, isFormatting);
    }

    /**
     * Assigns the root directory.
     *
     * @param type        DISK BASIC type
     * @param startSector Starting sector number
     * @param endSector   Ending sector number
     * @return True if valid
     */
    public boolean assignRoot(DiskBasicType<T> type, int startSector, int endSector) throws IOException {
        DiskBasicGroups rootGroups = new DiskBasicGroups();
        this.root = null; // Let GC handle it

        this.root = newItem();
        boolean valid = type.assignRootDirectory(startSector, endSector, rootGroups, root);
        if (valid) {
            // Set groups
            root.setGroups(rootGroups);
            // Directory tree confirmed
            root.validDirectory(true);
            // Hold groups
            currentItem = root;
        }
        return valid;
    }

    /**
     * Assigns the root directory.
     *
     * @param type DISK BASIC type
     * @return True if valid
     */
    public boolean assignRoot(DiskBasicType<T> type) throws IOException {
        DiskBasicGroups rootGroups = new DiskBasicGroups();
        this.root = null; // Let GC handle it

        this.root = newItem();
        boolean valid = type.assignDirectory(true, rootGroups, root);
        if (valid) {
            // Set groups
            root.setGroups(rootGroups);
            // Directory tree confirmed
            root.validDirectory(true);
            // Hold groups
            currentItem = root;
        }
        return valid;
    }

    /**
     * Releases the root directory.
     *
     * @param type DISK BASIC type
     * @return True
     */
    public boolean releaseRoot(DiskBasicType<T> type) {
        this.root = null;
        return true;
    }

    /**
     * Checks the directory.
     *
     * @param type       DISK BASIC type
     * @param groupItems List of groups
     * @return Check result (&lt; 0.0 on error)
     */
    public double check(DiskBasicType<T> type, DiskBasicGroups groupItems) throws IOException {
        return type.checkDirectory(false, groupItems);
    }

    /**
     * Assigns the directory.
     *
     * @param type       DISK BASIC type
     * @param groupItems List of groups
     * @param dirItem    Directory item
     * @return True if valid
     */
    public boolean assign(DiskBasicType<T> type, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        boolean valid = type.assignDirectory(false, groupItems, dirItem);
        if (valid) {
            // Directory tree confirmed
            dirItem.validDirectory(true);
        }
        return valid;
    }

    /**
     * Assigns the directory.
     *
     * @param type    DISK BASIC type
     * @param dirItem Directory item
     * @return True if valid
     */
    public boolean assign(DiskBasicType<T> type, DiskBasicDirItem<T> dirItem) throws IOException {
        DiskBasicGroups groupItems = new DiskBasicGroups();
        dirItem.getAllGroups(groupItems);
        return assign(type, groupItems, dirItem);
    }

    /**
     * Assigns the directory.
     *
     * @param dirItem Directory item
     * @return True if valid
     */
    public boolean assign(DiskBasicDirItem<T> dirItem) throws IOException {
        boolean valid = true;
        DiskBasicType<T> type = basic.getType();

        if (!type.isRootDirectory(dirItem.getStartGroup(0))) {
            // Assign subdirectory items
            if (dirItem.getChildren() == null) {
                DiskBasicGroups groups = new DiskBasicGroups();
                dirItem.getAllGroups(groups);

                if (check(type, groups) < 0.0) {
                    return false;
                }
                valid = assign(type, groups, dirItem);
            }
        }
        return valid;
    }

    /**
     * Reassigns the directory area (reloads).
     *
     * @param type    DISK BASIC type
     * @param dirItem Directory item
     * @return True if valid
     */
    public boolean reassign(DiskBasicType<T> type, DiskBasicDirItem<T> dirItem) throws IOException {
        boolean valid = true;

        // Delete child directory items
        emptyChildren(dirItem);
        // Reassign
        if (getParentItem(dirItem) != null) {
            valid = assign(type, dirItem);
        } else {
            valid = assignRoot(type, basic.getDirStartSector(), basic.getDirEndSector());
        }
        return valid;
    }

    /**
     * Reassigns the directory area (reloads).
     *
     * @param dirItem Directory item
     * @return True if valid
     */
    public boolean reassign(DiskBasicDirItem<T> dirItem) throws IOException {
        DiskBasicType<T> type = basic.getType();
        return reassign(type, dirItem);
    }

    /**
     * Initializes the root directory.
     */
    public void clearRoot() {
        fill(basic.getDirStartSector(), basic.getDirEndSector(), basic.getFillCodeOnDir());
    }

    /**
     * Fills the root directory area with a specified code.
     *
     * @param startSector Starting sector number
     * @param endSector   Ending sector number
     * @param code        Code to fill with
     */
    public void fill(int startSector, int endSector, byte code) {
        for (int secPos = startSector; secPos <= endSector; secPos++) {
            DiskImageSector sector = basic.getManagedSector(secPos - 1);
            if (sector == null) {
                break;
            }
            sector.fill(code);
        }
    }

    /**
     * Changes the current directory.
     *
     * @param dstItem Array holding the destination directory item (input/output)
     * @return True on success
     */
    public boolean change(DiskBasicDirItem<T>[] dstItem) throws IOException {
        if (dstItem == null || dstItem.length == 0 || dstItem[0] == null) {
            return false;
        }

        DiskBasicDirItem<T> dest = dstItem[0];
        DiskBasicType<T> type = basic.getType();

        if (type.isRootDirectory(dest.getStartGroup(0))) {
            // Move to root directory
            dest = root;
            currentItem = root;

        } else {
            // Move to subdirectory
            if (dest.getChildren() == null) {
                DiskBasicGroups groups = new DiskBasicGroups();
                dest.getAllGroups(groups);

                if (check(type, groups) < 0.0) {
                    return false;
                }
                assign(type, groups, dest);
            }
            // To make "." and ".." actual directory items
            // If it's the same as the parent directory, use that item
            DiskBasicDirItem<T> pitem = dest;
            for (int i = 0; i < 2; i++) {
                pitem = pitem.getParent();
                if (pitem == null) break;

                if (dest.getStartGroup(0) == pitem.getStartGroup(0)) {
                    dest = pitem;
                    break;
                }
            }
            currentItem = dest;
        }
        dstItem[0] = dest; // Update the reference in the array
        return true;
    }

    /**
     * Checks if the directory can be expanded.
     *
     * @param dirItem Directory
     * @return True if expandable
     */
    public boolean canExpand(DiskBasicDirItem<T> dirItem) {
        DiskBasicType<T> type = basic.getType();
        return getParentItem(dirItem) != null ? type.canExpandDirectory() : type.canExpandRootDirectory();
    }

    /**
     * Expands the directory.
     *
     * @param dirItem Directory
     * @return True on success
     */
    public boolean expand(DiskBasicDirItem<T> dirItem) throws IOException {
        boolean valid = false;
        if (dirItem != null) {
            valid = basic.expandDirectory(dirItem);
            if (valid) {
                // Re-read directory area
                valid = reassign(dirItem);
            }
        }
        return valid;
    }

    /**
     * Sets the format type.
     *
     * @param val Format type
     */
    public void setFormatType(DiskBasicFormat val) {
        formatType = val;
    }

    /**
     * Gets the format type.
     *
     * @return Format type
     */
    public DiskBasicFormat getFormatType() {
        return formatType;
    }

    /**
     * Calculates the occupied size of the directory.
     *
     * @return Occupied size in bytes
     */
    public int calcSize() {
        int size = 0;
        List<DiskBasicDirItem<T>> items = getCurrentItems(null);
        if (items != null) {
            int count = items.size();
            if (count == 0) {
                return 0;
            }
            // Use int to avoid overflow when casting int to int
            int dataSize = items.getFirst().getDataSize();
            size = dataSize * count;
            for (int i = (count - 1); i >= 0; i--) {
                DiskBasicDirItem<T> item = items.get(i);
                if (item.isUsed()) {
                    break;
                }
                size -= dataSize;
            }
        }
        return size;
    }
}
