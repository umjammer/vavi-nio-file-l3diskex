package l3diskex.basicfmt;

import java.io.IOException;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemC1541;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM;
import l3diskex.basicfmt.diritem.DiskBasicDirItemDOS80;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFLEX;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFM;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFP;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFROST;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFalcom;
import l3diskex.basicfmt.diritem.DiskBasicDirItemHU68K;
import l3diskex.basicfmt.diritem.DiskBasicDirItemL31S;
import l3diskex.basicfmt.diritem.DiskBasicDirItemL32D;
import l3diskex.basicfmt.diritem.DiskBasicDirItemLOSA;
import l3diskex.basicfmt.diritem.DiskBasicDirItemM68FDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMAGICAL;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DiskBasicDirItemVFAT;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSX;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZ;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTFDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD13;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD23;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;


/** ディレクトリアクセス */
public class DiskBasicDir<T extends DirectoryT> {

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
        DiskBasicDirItem item = null;

        DiskBasicFormatType num = FORMAT_TYPE_UNKNOWN;
        if (formatType != null) num = formatType.getTypeNumber();

        switch (num) {
            case FORMAT_TYPE_L3_1S:
                item = new DiskBasicDirItemL31S(basic);
                break;
            case FORMAT_TYPE_L3S1_2D:
                item = new DiskBasicDirItemL32D(basic);
                break;
            case FORMAT_TYPE_FM:
                item = new DiskBasicDirItemFM(basic);
                break;
            case FORMAT_TYPE_MSDOS:
                item = new DiskBasicDirItemVFAT(basic);
                break;
            case FORMAT_TYPE_MSX:
                item = new DiskBasicDirItemMSX(basic);
                break;
            case FORMAT_TYPE_N88:
                item = new DiskBasicDirItemN88(basic);
                break;
            case FORMAT_TYPE_X1HU:
                item = new DiskBasicDirItemX1HU(basic);
                break;
            case FORMAT_TYPE_MZ:
                item = new DiskBasicDirItemMZ(basic);
                break;
            case FORMAT_TYPE_FLEX:
                item = new DiskBasicDirItemFLEX(basic);
                break;
            case FORMAT_TYPE_OS9:
                item = new DiskBasicDirItemOS9(basic);
                break;
            case FORMAT_TYPE_CPM:
                item = new DiskBasicDirItemCPM(basic);
                break;
            case FORMAT_TYPE_PA:
                item = new DiskBasicDirItemN88(basic);
                break;
            case FORMAT_TYPE_SMC:
                item = new DiskBasicDirItemCPM(basic);
                break;
            case FORMAT_TYPE_FP:
                item = new DiskBasicDirItemFP(basic);
                break;
            case FORMAT_TYPE_DOS80:
                item = new DiskBasicDirItemDOS80(basic);
                break;
            case FORMAT_TYPE_FROST:
                item = new DiskBasicDirItemFROST(basic);
                break;
            case FORMAT_TYPE_MAGICAL:
                item = new DiskBasicDirItemMAGICAL(basic);
                break;
            case FORMAT_TYPE_SDOS:
                item = new DiskBasicDirItemSDOS(basic);
                break;
            case FORMAT_TYPE_MDOS:
                item = new DiskBasicDirItemMDOS(basic);
                break;
            case FORMAT_TYPE_XDOS:
                item = new DiskBasicDirItemXDOS(basic);
                break;
            case FORMAT_TYPE_TFDOS:
                item = new DiskBasicDirItemTFDOS(basic);
                break;
            case FORMAT_TYPE_CDOS:
                item = new DiskBasicDirItemCDOS(basic);
                break;
            case FORMAT_TYPE_MZ_FDOS:
                item = new DiskBasicDirItemMZFDOS(basic);
                break;
            case FORMAT_TYPE_HU68K:
                item = new DiskBasicDirItemHU68K(basic);
                break;
            case FORMAT_TYPE_LOSA:
                item = new DiskBasicDirItemLOSA(basic);
                break;
            case FORMAT_TYPE_CDOS2:
                item = new DiskBasicDirItemMSDOS(basic);
                break;
            case FORMAT_TYPE_FALCOM:
                item = new DiskBasicDirItemFalcom(basic);
                break;
            case FORMAT_TYPE_APLEDOS:
                item = new DiskBasicDirItemAppleDOS(basic);
                break;
            case FORMAT_TYPE_PRODOS:
                item = new DiskBasicDirItemProDOS(basic);
                break;
            case FORMAT_TYPE_C1541:
                item = new DiskBasicDirItemC1541(basic);
                break;
            case FORMAT_TYPE_AMIGA:
                item = new DiskBasicDirItemAmiga(basic);
                break;
            case FORMAT_TYPE_M68FDOS:
                item = new DiskBasicDirItemM68FDOS(basic);
                break;
            case FORMAT_TYPE_TRSD23:
                item = new DiskBasicDirItemTRSD23(basic);
                break;
            case FORMAT_TYPE_TRSD13:
                item = new DiskBasicDirItemTRSD13(basic);
                break;
            default:
                //logger.log(Level.ERROR, "Unknown type is defined in basic_type.xml.");
                //item = new DiskBasicDirItem(basic);
                break;
        }
        if (item != null) {
            item.clearData();
        }
        return item;
    }

    /**
     * Creates and assigns a new directory item.
     *
     * @param nSector Sector
     * @param nPos    Position within the sector
     * @param nData   Buffer within the sector
     * @param dataP
     * @return New DiskBasicDirItem or null
     */
    public DiskBasicDirItem<T> newItem(DiskImageSector nSector, int nPos, byte[] nData, int dataP) throws IOException {
        DiskBasicDirItem item = null;

        int num = FORMAT_TYPE_UNKNOWN.getValue();
        if (formatType != null) num = formatType.getTypeNumber().getValue();

        switch (DiskBasicFormatType.valueOf(num)) {
            case FORMAT_TYPE_L3_1S:
                item = new DiskBasicDirItemL31S(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_L3S1_2D:
                item = new DiskBasicDirItemL32D(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_FM:
                item = new DiskBasicDirItemFM(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_MSDOS:
                item = new DiskBasicDirItemVFAT(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_MSX:
                item = new DiskBasicDirItemMSX(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_N88:
                item = new DiskBasicDirItemN88(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_X1HU:
                item = new DiskBasicDirItemX1HU(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_MZ:
                item = new DiskBasicDirItemMZ(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_FLEX:
                item = new DiskBasicDirItemFLEX(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_OS9:
                item = new DiskBasicDirItemOS9(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_CPM:
                item = new DiskBasicDirItemCPM(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_PA:
                item = new DiskBasicDirItemN88(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_SMC:
                item = new DiskBasicDirItemCPM(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_FP:
                item = new DiskBasicDirItemFP(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_DOS80:
                item = new DiskBasicDirItemDOS80(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_FROST:
                item = new DiskBasicDirItemFROST(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_MAGICAL:
                item = new DiskBasicDirItemMAGICAL(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_SDOS:
                item = new DiskBasicDirItemSDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_MDOS:
                item = new DiskBasicDirItemMDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_XDOS:
                item = new DiskBasicDirItemXDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_TFDOS:
                item = new DiskBasicDirItemTFDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_CDOS:
                item = new DiskBasicDirItemCDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_MZ_FDOS:
                item = new DiskBasicDirItemMZFDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_HU68K:
                item = new DiskBasicDirItemHU68K(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_LOSA:
                item = new DiskBasicDirItemLOSA(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_CDOS2:
                item = new DiskBasicDirItemVFAT(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_FALCOM:
                item = new DiskBasicDirItemFalcom(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_APLEDOS:
                item = new DiskBasicDirItemAppleDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_PRODOS:
                item = new DiskBasicDirItemProDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_C1541:
                item = new DiskBasicDirItemC1541(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_AMIGA:
                item = new DiskBasicDirItemAmiga(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_M68FDOS:
                item = new DiskBasicDirItemM68FDOS(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_TRSD23:
                item = new DiskBasicDirItemTRSD23(basic, nSector, nPos, nData, dataP);
                break;
            case FORMAT_TYPE_TRSD13:
                item = new DiskBasicDirItemTRSD13(basic, nSector, nPos, nData, dataP);
                break;
            default:
                //logger.log(Level.ERROR, "Unknown type is defined in basic_type.xml.");
                //item = new DiskBasicDirItem(basic, nSector, nPos, nData, dataP);
                break;
        }
        return item;
    }

    /**
     * Creates and assigns a new directory item with group/sector info.
     *
     * @param nNum    Sequential number
     * @param nGitem  Track number data
     * @param nSector Sector
     * @param nPos    Position within the sector
     * @param nData   Buffer within the sector
     * @param nNext   Next sector info
     * @param nUnuse  Is unused (output array)
     * @return New DiskBasicDirItem or null
     */
    public DiskBasicDirItem<T> newItem(int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nPos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse) throws IOException {
        DiskBasicDirItem item = null;

        DiskBasicFormatType num = FORMAT_TYPE_UNKNOWN;
        if (formatType != null) num = formatType.getTypeNumber();

        // nUnuse is a boolean[] to simulate bool& in C++
        if (nUnuse == null || nUnuse.length == 0) {
            // Error handling for missing output parameter
            return null;
        }

        switch (num) {
            case FORMAT_TYPE_L3_1S:
                item = new DiskBasicDirItemL31S(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_L3S1_2D:
                item = new DiskBasicDirItemL32D(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_FM:
                item = new DiskBasicDirItemFM(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_MSDOS:
                item = new DiskBasicDirItemVFAT(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_MSX:
                item = new DiskBasicDirItemMSX(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_N88:
                item = new DiskBasicDirItemN88(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_X1HU:
                item = new DiskBasicDirItemX1HU(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_MZ:
                item = new DiskBasicDirItemMZ(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_FLEX:
                item = new DiskBasicDirItemFLEX(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_OS9:
                item = new DiskBasicDirItemOS9(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_CPM:
                item = new DiskBasicDirItemCPM(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_PA:
                item = new DiskBasicDirItemN88(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_SMC:
                item = new DiskBasicDirItemCPM(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_FP:
                item = new DiskBasicDirItemFP(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_DOS80:
                item = new DiskBasicDirItemDOS80(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_FROST:
                item = new DiskBasicDirItemFROST(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_MAGICAL:
                item = new DiskBasicDirItemMAGICAL(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_SDOS:
                item = new DiskBasicDirItemSDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_MDOS:
                item = new DiskBasicDirItemMDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_XDOS:
                item = new DiskBasicDirItemXDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_TFDOS:
                item = new DiskBasicDirItemTFDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_CDOS:
                item = new DiskBasicDirItemCDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_MZ_FDOS:
                item = new DiskBasicDirItemMZFDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_HU68K:
                item = new DiskBasicDirItemHU68K(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_LOSA:
                item = new DiskBasicDirItemLOSA(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_CDOS2:
                item = new DiskBasicDirItemVFAT(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_FALCOM:
                item = new DiskBasicDirItemFalcom(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_APLEDOS:
                item = new DiskBasicDirItemAppleDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_PRODOS:
                item = new DiskBasicDirItemProDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_C1541:
                item = new DiskBasicDirItemC1541(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_AMIGA:
                item = new DiskBasicDirItemAmiga(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_M68FDOS:
                item = new DiskBasicDirItemM68FDOS(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_TRSD23:
                item = new DiskBasicDirItemTRSD23(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            case FORMAT_TYPE_TRSD13:
                item = new DiskBasicDirItemTRSD13(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
            default:
                //logger.log(Level.ERROR, "Unknown type is defined in basic_type.xml.");
                //item = new DiskBasicDirItem(basic, nNum, nGitem, nSector, nPos, nData, dataP, nNext, nUnuse);
                break;
        }
        return item;
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
     * @param idx Index
     * @return Directory item or null
     */
    public DiskBasicDirItem<T> itemPtr(int idx) {
        List<DiskBasicDirItem<T>> items = getCurrentItems(null);
        if (items == null || idx < 0 || idx >= items.size()) return null;
        return items.get(idx);
    }

    /**
     * Returns an unused directory item in the current directory.
     *
     * @param pitem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItemOnCurrent(DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        return getEmptyItem(getCurrentItem(), getCurrentItems(null), pitem, nextItem);
    }

    /**
     * Returns an unused directory item in the root directory.
     *
     * @param pitem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItemOnRoot(DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        return getEmptyItem(getRootItem(), getRootItems(null), pitem, nextItem);
    }

    /**
     * Returns an unused directory item in the specified directory.
     *
     * @param dirItem  Directory
     * @param pitem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItem(DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        return getEmptyItem(dirItem, getChildren(dirItem), pitem, nextItem);
    }

    /**
     * Returns an unused directory item in the specified directory.
     *
     * @param dirItem  Directory
     * @param children List of directory items in dirItem
     * @param pitem    Temporary directory item with filename/attributes
     * @param nextItem Output array for the item after the unused one (can be null)
     * @return Unused directory item or null
     */
    public DiskBasicDirItem<T> getEmptyItem(DiskBasicDirItem<T> dirItem, List<DiskBasicDirItem<T>> children, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        DiskBasicType<T> type = basic.getType();
        return type.getEmptyDirectoryItem(dirItem, children, pitem, nextItem);
    }

    /**
     * Checks if a file with the same name already exists in the current directory.
     *
     * @param filename    Filename
     * @param icase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileOnCurrent(DiskBasicFileName filename, boolean icase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        return findFile(getCurrentItem(), filename, icase, excludeItem, nextItem);
    }

    /**
     * Checks if a file with the same name already exists in the specified directory.
     *
     * @param dirItem     Directory item to search
     * @param filename    Filename
     * @param icase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFile(DiskBasicDirItem<T> dirItem, DiskBasicFileName filename, boolean icase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        DiskBasicDirItem<T> matchItem = null;
        List<DiskBasicDirItem<T>> items = dirItem.getChildren();
        if (items != null) {
            for (int pos = 0; pos < items.size(); pos++) {
                DiskBasicDirItem<T> item = items.get(pos);
                if (item != excludeItem && item.isSameFileName(filename, icase)) {
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
     * @param icase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findFileOnCurrent(DiskBasicDirItem<T> targetItem, boolean icase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        return findFile(getCurrentItem(), targetItem, icase, excludeItem, nextItem);
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
     * @param icase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findNameOnCurrent(String name, boolean icase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem<T>[] nextItem) {
        return findName(getCurrentItem(), name, icase, excludeItem, nextItem);
    }

    /**
     * Checks if a file with the same name (excluding extension) exists in the specified directory.
     *
     * @param dirItem     Directory item to search
     * @param name        Filename (excluding extension)
     * @param icase       Case insensitive flag
     * @param excludeItem Item to exclude from search (can be null)
     * @param nextItem    Output array for the item after the matched one (can be null)
     * @return Matched directory item or null
     */
    public DiskBasicDirItem<T> findName(DiskBasicDirItem<T> dirItem, String name, boolean icase, DiskBasicDirItem<T> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<T> matchItem = null;
        List<DiskBasicDirItem<T>> items = dirItem.getChildren();
        if (items != null) {
            for (int pos = 0; pos < items.size(); pos++) {
                DiskBasicDirItem<T> item = items.get(pos);
                if (item != excludeItem && item.isSameName(name, icase)) {
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
        // C++: delete root;
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
            valid = assignRoot(type, basic.diskBasicParam.getDirStartSector(), basic.diskBasicParam.getDirEndSector());
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
        fill(basic.diskBasicParam.getDirStartSector(), basic.diskBasicParam.getDirEndSector(), basic.diskBasicParam.getFillCodeOnDir());
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
