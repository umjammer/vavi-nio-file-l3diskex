package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import l3diskex.Common;
import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.find;
import static l3diskex.Parambase.MyAttributes.findType;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.Parambase.MyAttributes.findValue;
import static l3diskex.Parambase.MyAttributes.getIndexByValue;
import static l3diskex.Parambase.MyAttributes.getTypeByIndex;
import static l3diskex.Parambase.MyAttributes.getValueByIndex;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_EXTENSION_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/** One directory item */
public abstract class DiskBasicDirItem<T extends Directory> {

    private static final Logger logger = System.getLogger(DiskBasicDirItem.class.getName());

    /** Directory data */
    protected static class DiskBasicDirData<TYPE extends Directory> {

        private Class<TYPE> clazz;
        private TYPE data;
        private byte[] raw;
        private int size;

        public DiskBasicDirData() {
            data = null;
        }

        /**
         * Memory allocation
         * Does not initialize memory
         */
        public void alloc(Class<TYPE> clazz) {
            try {
                this.clazz = clazz;
                this.size = (int) clazz.getDeclaredField("SIZE").get(null);
                this.raw = new byte[this.size];
            } catch (Exception e) {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
        }

        /**
         * Pointer assignment
         *
         * @param data Pointer
         */
        public void attach(Class<TYPE> clazz, byte[] data, int offset) {
            if (this.raw == null) alloc(clazz);
            if (data != null) {
                System.arraycopy(data, offset, raw, 0, Math.min(this.size, data.length - offset));
            }
        }

        public boolean copy(byte[] srcData) {
            return copy(srcData, 0xff_fffff, false, 0);
        }

        public boolean copy(byte[] srcData, int len) {
            return copy(srcData, len, false, 0);
        }

        /**
         * Copy
         *
         * @param srcData Original data
         * @param len     Data size
         * @param invert  Whether to invert
         * @param start   Copy starting position
         */
        public boolean copy(byte[] srcData, int len, boolean invert, int start) {
            if (data == null) return false;
            System.arraycopy(srcData, start, raw, 0, len);
            if (invert) invert(len, start);
            return true;
        }

        public boolean fill(int ch) {
            return fill(ch, 0xff_fffff, false, 0);
        }

        public boolean fill(int ch, int len) {
            return fill(ch, len, false, 0);
        }

        /**
         * Fill
         *
         * @param ch     Character to fill with
         * @param len    Data size
         * @param invert Whether to invert
         * @param start  Copy starting position
         */
        public boolean fill(int ch, int len, boolean invert, int start /* = 0 */) {
            if (raw == null) return false;
            byte[] dst = raw;
            if (len > dst.length) len = dst.length;
            Arrays.fill(dst, start, len, (byte) ch);
            if (invert) invert(len, start);
            return true;
        }

        /**
         * Inversion
         *
         * @param len   Data size
         * @param start Copy starting position
         */
        public void invert(int len, int start) {
            byte[] arr = raw;
            if (len > arr.length) len = arr.length;
            for (int i = start; i < len; i++) {
                arr[i] = (byte) (~arr[i]);
            }
        }

        /** Returns data */
        public TYPE data() {
            if (data == null) {
                try {
                    this.data = clazz.getDeclaredConstructor().newInstance();
                    Serdes.Util.deserialize(new ByteArrayInputStream(raw), this.data);
                } catch (Exception e) {
                    logger.log(Level.TRACE, "clazz: " + clazz + ", " + raw.length);
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
            return data;
        }

        /** Whether data is valid */
        public boolean isValid() {
            return raw != null;
        }

        public int getDataSize() {
            return size;
        }

        public byte[] getRawData() {
            return raw;
        }
    }

    /** bit0: Whether it is used */
    public static final int USED_ITEM = 0x0001;
    /** bit1: Whether to display in list */
    public static final int VISIBLE_LIST = 0x0002;
    /** bit2: Whether to display in tree */
    public static final int VISIBLE_TREE = 0x0004;

    protected DiskBasic basic;
    protected DiskBasicType<T> type;

    /** Parent directory */
    protected DiskBasicDirItem<T> parent;
    /** Child directory */
    protected List<DiskBasicDirItem<T>> children;
    /** Whether the above directory tree is confirmed */
    protected boolean validDir;

    /** Sequential number */
    protected int num;
    /** Position within sector (bytes) */
    protected int position;
    /** Flags bit0: whether used, bit1: whether display in list, bit2: whether display in tree */
    protected int flags;
    /** Occupied groups */
    protected DiskBasicGroups groups;
    /** Sector where the directory is located */
    protected DiskImageSector sector;
    /** Holds attributes not present within the directory entry (model dependent) */
    public int externalAttr;

    public DiskBasicDirItem() {
        this.basic = null;
        this.type = null;
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = 0;
        sector = null;
        externalAttr = 0;
        flags = VISIBLE_LIST | VISIBLE_TREE;
        groups = new DiskBasicGroups();
    }

    public abstract boolean isSupported(int formatType);

    /**
     * Create directory item DATA is allocated internally
     *
     * @param basic DISK BASIC
     */
    public void init(DiskBasic basic) throws IOException {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = 0;
        sector = null;
        externalAttr = 0;
        flags = VISIBLE_LIST | VISIBLE_TREE;
        groups = new DiskBasicGroups();
    }

    /**
     * Create directory item DATA is assigned from disk image
     *
     * @param basic     DISK BASIC
     * @param sector    Sector
     * @param sectorPos Position within sector
     * @param data      Directory entry within sector
     */
    public void init(DiskBasic basic,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos) throws IOException {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = sectorPos;
        this.sector = sector;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    /**
     * Create directory item DATA is assigned from disk image
     *
     * @param basic     DISK BASIC
     * @param num       Sequential number
     * @param groupItem Data such as track number
     * @param sector    Sector
     * @param sectorPos Position of directory entry within sector
     * @param data      Directory entry within sector
     * @param next      Next sector
     * @param unuse     [out] Whether it is unused
     */
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next, boolean[] unuse) throws IOException {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        this.num = num;
        position = sectorPos;
        this.sector = sector;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    public void setData(int nNum, DiskBasicGroupItem gItem,
                        DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos,
                        SectorParam next /* = null */) throws IOException {
        num = nNum;
        position = sectorPos;
        this.sector = sector; // no duplicate
    }

    /** Create child directory */
    public void createChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
    }

    /**
     * Add child directory
     *
     * @param newItem New item
     */
    public void addChild(DiskBasicDirItem<T> newItem) {
        createChildren();
        children.add(newItem);
    }

    public DiskBasicDirItem<T> getParent() {
        return parent;
    }

    public void setParent(DiskBasicDirItem<T> newitem) {
        parent = newitem;
    }

    public List<DiskBasicDirItem<T>> getChildren() {
        return children;
    }

    /** Clear child directory list */
    public void emptyChildren() {
        if (children != null) {
            children.clear();
            children = null;
        }
        validDir = false;
    }

    public boolean isValidDirectory() {
        return validDir;
    }

    public void validDirectory(boolean val) {
        validDir = val;
    }

    public abstract boolean check(boolean[] last);

    /** Check directory item */
    public static boolean checkData(byte[] buf, int len, boolean[] last) {
        byte prev = 0;
        boolean valid = false;
        for (int i = 0; i < len; i++) {
            if (buf[i] == 0 || buf[i] == (byte) 0xff) {
                valid = true;
                break;
            }
            if (i != 0 && buf[i] != prev) {
                valid = true;
                break;
            }
            prev = buf[i];
        }
        return valid;
    }

    /**
     * Whether the item can be deleted
     *
     * @return true if it can be deleted
     */
    public boolean isDeletable() {
        return getFileAttr().unmatchType(FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue());
    }

    public abstract boolean delete() throws IOException;

    public boolean hasEndMark() {
        return false;
    }

    public void setEndMark(DiskBasicDirItem<?> nextItem) throws IOException {
    }

    /** Reset internal variables, etc. */
    public void refresh() throws IOException {
        // Update flags
        used(checkUsed(false));

        // Recalculate file size
        calcFileSize();
    }

    /**
     * Whether the item can be loaded/exported
     *
     * @return true if it can be loaded
     */
    public boolean isLoadable() {
        return isDeletable();
    }

    /**
     * Whether the item can be copied
     *
     * @return true if it can be copied
     */
    public boolean isCopyable() {
        // Volume label not allowed
        return getFileAttr().matchType(FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue(), 0);
    }

    /**
     * Whether the item can be overwritten
     *
     * @return true if it can be overwritten
     */
    public boolean isOverWritable() {
        // Directory, volume label not allowed
        return isCopyable();
    }

    /**
     * Returns the position where the file name is stored
     *
     * @param num  Name buffer number
     * @param size [out] Buffer size
     * @param len  [out] Size usable as file name
     * @return Destination buffer pointer
     */
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    /**
     * Returns the position where the file name is stored
     *
     * @param num Name buffer number
     * @param len [out] Size usable as file name
     * @return Destination buffer pointer
     */
    protected byte[] getFileNamePos(int num, int[] len) {
        int[] size = new int[1];
        return getFileNamePos(num, size, len);
    }

    /**
     * Returns the position where the extension is stored
     *
     * @param len [out] Buffer size
     * @return Destination buffer pointer
     */
    protected byte[] getFileExtPos(int[] len) {
        len[0] = 0;
        return null;
    }

    protected int getFileType1() {
        return 0;
    }

    public int getFileType2() {
        return 0;
    }

    protected int getFileType3() {
        return 0;
    }

    protected abstract void setFileType1(int val);

    protected void setFileType2(int val) {
    }

    protected void setFileType3(int val) {
    }

    /**
     * Set file name
     * <p>
     * filename data bits may be inverted
     *
     * @param filename [in,out] File name
     * @param size     Buffer size
     * @param length   Length
     */
    protected void setNativeName(byte[] filename, int size, int length) {
        int[] nl = new int[1];
        int[] ns = new int[1];
        byte[] n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            if (ns[0] > size) ns[0] = size;
            System.arraycopy(filename, 0, n, 0, ns[0]);
        }
    }

    /**
     * Set extension
     * <p>
     * fileext data bits may be inverted
     *
     * @param fileExt [in,out] Extension
     * @param size    Buffer size
     * @param length  Length
     */
    protected void setNativeExt(byte[] fileExt, int size, int length) {
        int[] el = new int[1];
        byte[] e = getFileExtPos(el);
        if (e != null && el[0] > 0) {
            if (el[0] > size) el[0] = size;
            System.arraycopy(fileExt, 0, e, 0, el[0]);
        }
    }

    /**
     * Returns file name (excluding extension)
     *
     * @return File name
     */
    public String getFileNamePlainStr() {
        byte[] name = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        nl[0] = name.length;
        el[0] = 0;
        getNativeFileName(name, nl, null, el);
        String dst = "";
        convCharsToString(name, nl[0]);
        return dst;
    }

    /**
     * Returns extension
     *
     * @return Extension
     */
    protected String getFileExtPlainStr() {
        byte[] ext = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        nl[0] = 0;
        el[0] = ext.length;
        getNativeFileName(null, nl, ext, el);
        String dst = "";
        convCharsToString(ext, el[0]);
        return dst;
    }

    /**
     * Convert file name to internal file name For search
     *
     * @param filename File name (Unicode)
     * @param name     [out] Internal file name buffer
     * @param nLen     [in,out] Above buffer size / returns string length
     * @param ext      [out] Internal extension name buffer
     * @param elen     [in,out] Above buffer size / returns string length
     * @return true: OK, false: Contains characters that cannot be converted
     */
    protected boolean toNativeFileName(String filename, byte[] name, int[] nLen, byte[] ext, int[] elen) {
        byte[] tmp = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        String namestr = filename;
        if (basic.toUpperAfterRenamed()) {
            namestr = namestr.toUpperCase();
        }
        int tmplen = convStringToChars(namestr, tmp, tmp.length);
        if (tmplen < 0) return false;
        if (ext != null && elen[0] > 0) {
            int[] extLen = new int[1];
            getFileExtPos(extLen);
            elen[0] = extLen[0];
        }
        splitFileName(tmp, tmp.length, tmplen, name, nLen[0], nl, ext, elen[0], el, basic.getExtensionPreCode());
        nLen[0] = nl[0];
        elen[0] = el[0];
        return true;
    }

    /**
     * Generate internal file name from file path
     *
     * Before showing the dialog during import
     * On models without extension, set extension part as file name as is
     *
     * @param filepath File path
     * @return File name
     */
    protected String remakeFileNameAndExtStr(String filepath) {
        String newName = "";
        Path path = Paths.get(filepath);
        String filename = path.getFileName().toString();
        int[] nl = new int[1];
        int[] el = new int[1];
        for (int num = 0; num < 2; num++) {
            int[] n = new int[1];
            if (getFileNamePos(num, n) == null || n[0] == 0) {
                break;
            }
            nl[0] += n[0];
        }
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        el[0] = extLen[0];
        int lastDot = filename.lastIndexOf('.');
        if (el[0] == 0) {
            newName = filename.substring(0, Math.min(filename.length(), nl[0]));
        } else {
            String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
            String ext = lastDot >= 0 ? filename.substring(lastDot + 1) : "";
            newName = name.substring(0, Math.min(name.length(), nl[0]));
            if (!ext.isEmpty()) {
                newName += ".";
                newName += ext.substring(0, Math.min(ext.length(), el[0]));
            }
        }
        if (basic.toUpperBeforeDialog()) {
            newName = newName.toUpperCase();
        }
        convertFileNameBeforeImportDialog(newName);
        return newName;
    }

    /**
     * Generate internal file name from file path
     *
     * Before showing the dialog during import
     * On models without extension, the extension is removed
     *
     * @param filepath File path
     * @return File name
     */
    protected String remakeFileNameOnlyStr(String filepath) {
        String newName = "";
        Path path = Paths.get(filepath);
        String filename = path.getFileName().toString();
        int[] nl = new int[1];
        int[] el = new int[1];
        int[] nameLen = new int[1];
        getFileNamePos(0, nameLen);
        nl[0] = nameLen[0];
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        el[0] = extLen[0];
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot + 1) : "";
        newName = name.substring(0, Math.min(name.length(), nl[0]));
        if (el[0] > 0) {
            if (!ext.isEmpty()) {
                newName += (char) basic.getExtensionPreCode();
                newName += ext.substring(0, Math.min(ext.length(), el[0]));
            }
        }
        if (basic.toUpperBeforeDialog()) {
            newName = newName.toUpperCase();
        }
        convertFileNameBeforeImportDialog(newName);
        return newName;
    }

    /**
     * Add extension based on attributes
     * <p>
     * Applied to the file name of the file to be exported
     *
     * @param fileType File attribute
     * @param mask     File attribute bitmask
     * @param filename [in,out] File name
     * @param dupli    Whether to allow duplication of the same extension name
     */
    protected void addExtensionByFileAttr(int fileType, int mask, String[] filename, boolean dupli /* = false */) {
        Path path = Paths.get(filename[0]);
        String ext = getFileExtension(path.getFileName().toString());
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext, fileType, mask);
        if (sa == null) {
            sa = findType(basic.getAttributesByExtension(), fileType, mask);
        }
        if (sa != null) {
            if (dupli || !ext.equalsIgnoreCase(sa.getName())) {
                filename[0] += ".";
                if (Utils.isUpperString(filename[0])) {
                    filename[0] += sa.getName().toUpperCase();
                } else {
                    filename[0] += sa.getName().toLowerCase();
                }
            }
        }
    }

    /**
     * Add extension based on attributes
     * <p>
     * Applied to the file name of the file to be exported
     *
     * @param fileType File attribute
     * @param mask     File attribute bitmask
     * @param filename [in,out] File name
     * @param external Extended attribute Compare with value
     * @param dupli    Whether to allow duplication of the same extension name
     */
    protected void addExtensionByFileAttr(int fileType, int mask, String filename, int external, boolean dupli) {
        Path path = Paths.get(filename);
        String ext = getFileExtension(path.getFileName().toString());
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext, fileType, mask);
        if (sa == null) {
            sa = find(basic.getAttributesByExtension(), fileType, mask, external);
        }
        if (sa == null) {
            sa = findType(basic.getAttributesByExtension(), fileType, mask);
        }
        if (sa != null) {
            if (dupli || !ext.equalsIgnoreCase(sa.getName())) {
                filename += ".";
                if (Utils.isUpperString(filename)) {
                    filename += sa.getName().toUpperCase();
                } else {
                    filename += sa.getName().toLowerCase();
                }
            }
        }
    }

    protected boolean getFileAttrName(int pos, Map<String, Object> list, String[] attr) {
        return getFileAttrName(pos, list, attr, -1);
    }

    /**
     * Returns attribute string
     *
     * @param pos        Position (if negative, search from external definition)
     * @param list       Name & value list
     * @param defaultPos Position of default name to set when not matching (-1 for not setting)
     * @param attr       [out] Matched name
     * @return true if matching the list
     */
    protected boolean getFileAttrName(int pos, Map<String, Object> list, String[] attr, int defaultPos) {
        boolean match = true;
        if (pos >= 0) {
            attr[0] = Utils.keyAt(list, pos);
        } else {
            MyAttribute sa = findValue(basic.getSpecialAttributes(), -pos);
            if (sa != null) {
                attr[0] = sa.getName();
            } else {
                match = false;
                if (defaultPos >= 0) {
                    attr[0] = Utils.keyAt(list, defaultPos);
                }
            }
        }
        return match;
    }

    /**
     * Whether attributes can be determined from extension. If so, remove the trailing extension.
     *
     * Applied to the file name of the file to be imported
     *
     * <pre> For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar (trimming last extension)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     */
    protected boolean trimExtensionByExtensionAttr(String[] filename) {
        String ext = getFileExtension(filename[0]);
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext);
        if (sa != null) {
            filename[0] = removeExtension(filename[0]);
        }
        return sa != null;
    }

    /**
     * Whether attributes can be determined from extension.
     *
     * If so, and if two extensions are connected, remove the trailing extension.
     *
     * Applied to the file name of the file to be imported
     *
     * <pre>
     *  For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar.bas (no change filename)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     */
    protected boolean trimLastExtensionByExtensionAttr(String filename) {
        String ext = getFileExtension(filename);
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext);
        if (sa != null) {
            String name = removeExtension(filename);
            String subext = getFileExtension(name);
            if (!subext.isEmpty()) {
                filename = name;
            }
        }
        return sa != null;
    }

    /**
     * Whether attributes can be determined from extension.
     * <p>
     * If so, and if two extensions are connected, remove the trailing extension.
     * <p>
     * Applied to the file name of the file to be imported
     * <pre>
     *  For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar.bas (no change filename)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     *
     * @param filename  File name
     * @param list      Extension name & value list
     * @param listFirst First position of the list
     * @param listLast  Last position of the list
     * @param outfile   [out] File name after editing
     * @param attr      [out] Attribute
     * @param pos       [out] List position (becomes negative for user-specified attributes)
     */
    protected boolean trimLastExtensionByExtensionAttr(String filename, Map<String, Object> list, int listFirst, int listLast, String[] outfile, int[] attr, int[] pos) {
        boolean match = false;
        int t1 = 0;
        int p1 = -1;
        String ext = getFileExtension(filename).toUpperCase();
        int idx = Utils.indexOf(list, ext);
        if (idx >= listFirst && idx <= listLast) {
            match = true;
            t1 = (int) list.get(idx);
            p1 = idx;
        } else {
            MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext);
            if (sa != null) {
                match = true;
                t1 = sa.getType();
            }
        }
        if (match) {
            if (outfile != null) outfile[0] = removeExtension(filename);
            if (attr != null) attr[0] = t1;
            if (pos != null) pos[0] = p1;
        }
        return match;
    }

    /**
     * Whether attributes can be determined from extension. If so, remove the trailing extension.
     * <p>
     * Applied to the file name of the file to be imported
     *
     * <pre> For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar (trimming last extension)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     *
     * @param filename  File name
     * @param list      Extension name & value list
     * @param listFirst First position of the list
     * @param listLast  Last position of the list
     * @param outfile   [out] File name after editing
     * @param attr      [out] Attribute
     * @param pos       [out] List position (becomes negative for user-specified attributes)
     */
    protected boolean isContainAttrByExtension(String filename, Map<String, Object> list, int listFirst, int listLast, String[] outfile, int[] attr, int[] pos) {
        boolean match = false;
        int t1 = 0;
        int p1 = -1;
        String ext = getFileExtension(filename).toUpperCase();
        int idx = Utils.indexOf(list, ext);
        if (idx >= listFirst && idx <= listLast) {
            match = true;
            t1 = (int) Utils.valueAt(list, idx);
            p1 = idx;
        } else {
            MyAttribute sa = findUpperCase(basic.getSpecialAttributes(), ext);
            if (sa != null) {
                match = true;
                t1 = sa.getValue();
            } else {
                sa = findUpperCase(basic.getAttributesByExtension(), ext);
                if (sa != null) {
                    match = true;
                    t1 = sa.getValue();
                }
            }
        }
        if (match) {
            if (outfile != null) outfile[0] = removeExtension(filename);
            if (attr != null) attr[0] = t1;
            if (pos != null) pos[0] = p1;
        }
        return match;
    }

    /**
     * Create attribute choices (for property dialog)
     *
     * @param basic    DiskBasic
     * @param list     Extension name & value list
     * @param endPos   Final position of the list
     * @param fileType Attribute value (optional)
     *                 If an attribute value is set and it does not match the list, it is added to the choices.
     * @param types    [out] Choice list
     */
    public static void createChoiceForAttrDialog(DiskBasic basic, Map<String, Object> list, int endPos, List<String> types, int fileType) {
        boolean matchValue = false;
        for (Map.Entry<String, Object> e : list.entrySet()) {
            types.add(e.getKey());
            if (fileType >= 0 && (int) e.getValue() == fileType) {
                matchValue = true;
            }
        }
        List<MyAttribute> attrs = basic.getSpecialAttributes();
        for (MyAttribute attr : attrs) {
            String str = attr.getName() + String.format(" 0x%02x", attr.getValue());
            if (!attr.getDescription().isEmpty()) {
                str += " (" + attr.getDescription() + ")";
            }
            types.add(str);
            if (fileType >= 0 && attr.getValue() == fileType) {
                matchValue = true;
            }
        }
        if (fileType >= 0 && !matchValue) {
            types.add(String.format("0x%02x", fileType));
        }
    }

    /**
     * Select attribute choice (for property dialog)
     *
     * @param basic      DiskBasic
     * @param selPos     Selection position (if negative, attribute position of external settings)
     * @param endPos     Final position of the list
     * @param unknownPos Position of "Unknown"
     * @return Position of choice
     */
    public static int selectChoiceForAttrDialog(DiskBasic basic, int selPos, int endPos, int unknownPos) {
        if (selPos < 0) {
            List<MyAttribute> attrs = basic.getSpecialAttributes();
            int nType = getIndexByValue(attrs, -selPos);
            if (nType >= 0) {
                selPos = nType + endPos;
            }
        }
        if (selPos < 0) {
            selPos = unknownPos;
        }
        return selPos;
    }

    /** Returns attributes from the position in the list (for property dialog) */
    public static int calcSpecialOriginalTypeFromPos(DiskBasic basic, int pos, int endPos) {
        int val = -1;
        if (pos >= endPos) {
            pos -= endPos;
            List<MyAttribute> attrs = basic.getSpecialAttributes();
            int count = attrs.size();
            if (pos < count) {
                val = getValueByIndex(attrs, pos);
            }
        }
        return val;
    }

    /** Returns attributes from the position in the list (for property dialog) */
    public static int calcSpecialFileTypeFromPos(DiskBasic basic, int pos, int endPos) {
        int t = 0;
        if (pos >= endPos) {
            pos -= endPos;
            List<MyAttribute> attrs = basic.getSpecialAttributes();
            int count = attrs.size();
            if (pos < count) {
                t = getTypeByIndex(attrs, pos);
            }
        }
        return t;
    }

    /** Ratio of normal codes in the file name (0.0-1.0) */
    public double normalCodesInFileName() {
        int count = 0;
        boolean invert = basic.isDataInverted();
        int[] l = new int[1];
        byte[] n = getFileNamePos(0, l);
        for (int i = 0; i < l[0]; i++) {
            byte c = n[i];
            if (invert) {
                c = (byte) (~c);
            }
            if (c >= 0x20) {
                count++;
            }
        }
//logger.log(Level.INFO, "%s, %d, %s".formatted(new String(n, 0, l[0]), count, getClass().getSimpleName()));
        return l[0] > 0 ? (double) count / (double) l[0] : 0.0;
    }

    public boolean isFileNameEditable() {
        return true;
    }

    /**
     * Set file name Separate from extension with "."
     *
     * @param filename File name (Unicode)
     */
    public void setFileNameStr(String filename) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nLen = new int[] {name.length};
        int[] eLen = new int[] {ext.length};
        toNativeFileName(filename, name, nLen, ext, eLen);
        setNativeFileName(name, name.length, nLen[0], ext, ext.length, eLen[0]);
    }

    /**
     * Set file name as is
     * The extension part is cleared
     *
     * @param filename File name (Unicode)
     */
    public void setFileNamePlain(String filename) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nlen = new int[] {name.length};
        int[] elen = new int[] {0};
        toNativeFileName(filename, name, nlen, null, elen);
        setNativeFileName(name, name.length, nlen[0], ext, ext.length, 0);
    }

    /**
     * Set extension
     *
     * @param fileExt Extension (Unicode)
     */
    public void setFileExtPlain(String fileExt) {
        byte[] ext = new byte[256];
        int[] nlen = new int[] {0};
        int[] elen = new int[] {ext.length};
        toNativeFileName(fileExt, ext, elen, null, nlen);
        setNativeFileName(null, 0, 0, ext, ext.length, elen[0]);
    }

    /**
     * Set file name and extension
     *
     * @param name  [in,out] File name
     * @param nSize File name buffer size
     * @param nLen  File name length
     * @param ext   [in,out] Extension
     * @param eSize Extension buffer size
     * @param eLen  Extension length
     *              Data bits are inverted in this function
     */
    public void setNativeFileName(byte[] name, int nSize, int nLen, byte[] ext, int eSize, int eLen) {
        boolean invert = basic.isDataInverted();
        char space = (char) basic.getDirSpaceCode(); // Space code
        char term = (char) basic.getDirTerminateCode(); // Termination code
        if (name != null) {
            if (nSize > nLen) {
                name[nLen] = (byte) term;
                nLen++;
            }
            if (nSize > nLen) {
                Arrays.fill(name, nLen, nSize, (byte) space);
            }
            if (invert) {
                Common.invertMemory(name, nSize);
            }
            setNativeName(name, nSize, nLen);
        }
        if (ext != null) {
            if (eSize > eLen) {
                ext[eLen] = (byte) term;
                eLen++;
            }
            if (eSize > eLen) {
                Arrays.fill(ext, eLen, eSize, (byte) space);
            }
            if (invert) {
                Common.invertMemory(ext, eSize);
            }
            setNativeExt(ext, eSize, eLen);
        }
    }

    /**
     * Copy file name
     *
     * @param src Directory item
     */
    public void copyFileName(DiskBasicDirItem<T> src) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nLen = new int[] {name.length};
        int[] eLen = new int[1];
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        eLen[0] = extLen[0];
        src.getNativeFileName(name, nLen, ext, eLen);
        setNativeFileName(name, name.length, nLen[0], ext, ext.length, eLen[0]);
    }

    /**
     * Returns file name Name + "." + Extension
     *
     * @return File name
     */
    public String getFileNameStr() {
        byte[] name = new byte[256], ext = new byte[256];
        int[] nl = new int[] {name.length};
        int[] el = new int[] {ext.length};

        getNativeFileName(name, nl, ext, el);

        String dst = convCharsToString(name, nl[0]);

        if (el[0] > 0) {
            dst += (char) basic.getExtensionPreCode();
            dst += convCharsToString(ext, el[0]);
        }

        return dst;
    }

    /**
     * Returns file name Name + "." + Extension During export
     *
     * @return File name
     */
    public String getFileNameStrForExport() {
        byte[] name = new byte[256], ext = new byte[256];
        int[] nl = new int[] {name.length};
        int[] el = new int[] {ext.length};

        getNativeFileName(name, nl, ext, el);

        String dst = convCharsToString(name, nl[0]);

        if (el[0] > 0) {
            dst += ".";
            dst += convCharsToString(ext, el[0]);
        }

        return dst;
    }

    /**
     * Get file name Name + "." + Extension
     * Extension is not added if it exceeds buffer
     *
     * @param filename Destination buffer
     * @param length   Destination buffer size
     */
    public void getFileName(byte[] filename, int length) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nl = new int[] {name.length};
        int[] el = new int[] {ext.length};
        getNativeFileName(name, nl, ext, el);
        System.arraycopy(name, 0, filename, 0, Math.min(length, nl[0]));
        if (el[0] > 0 && (nl[0] + el[0] + 1) < length) {
            filename[nl[0]] = basic.getExtensionPreCode();
            nl[0]++;
            System.arraycopy(ext, 0, filename, nl[0], el[0]);
        }
    }

    /**
     * Get file name
     * <p>
     * Keep data bits inverted
     *
     * @param filename [in,out] File name
     * @param size     Buffer size
     * @param length   [out] Length
     */
    protected void getNativeName(byte[] filename, int size, int[] length) {
        int[] s = new int[1];
        int[] l = new int[1];
        byte[] n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            System.arraycopy(n, 0, filename, 0, s[0]);
        }
        length[0] = l[0];
    }

    /**
     * Get extension
     * <p>
     * Keep data bits inverted
     *
     * @param fileExt [in,out] Extension
     * @param size    Buffer size
     * @param length  [out] Length
     */
    protected void getNativeExt(byte[] fileExt, int size, int[] length) {
        int[] l = new int[1];
        byte[] e = getFileExtPos(l);
        if (e != null && l[0] > 0) {
            System.arraycopy(e, 0, fileExt, 0, l[0]);
        }
        length[0] = l[0];
    }

    /**
     * Get file name and extension
     * Each space is right trimmed
     *
     * @param name [out] File name buffer
     * @param nLen [in,out] Above buffer size / returns string length
     * @param ext  [out] Extension name buffer
     * @param eLen [in,out] Above buffer size / returns string length
     *             Data bits are inverted in this function
     */
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        int[] nl = new int[1];
        int[] el = new int[1];
        boolean invert = basic.isDataInverted();
        char trim = (char) basic.getDirTrimmingCode(); // Trimming code
        char space = (char) basic.getDirSpaceCode(); // Space code
        char term = (char) basic.getDirTerminateCode(); // Termination code
        if (name != null && nLen[0] > 0) {
            Arrays.fill(name, 0, nLen[0], (byte) 0);
            getNativeName(name, nLen[0], nl);
            if (invert) {
                Common.invertMemory(name, nl[0]);
            }
            nl[0] = Common.trimRight(name, nl[0], (byte) trim);
            nl[0] = Common.trimRight(name, nl[0], (byte) space);
            nl[0] = Common.trimRight(name, nl[0], (byte) term);
            if (isUsed()) nLen[0] = Common.shrinkString(name, nl[0]);
            else nLen[0] = nl[0];
        }
        if (ext != null && eLen[0] > 0) {
            Arrays.fill(ext, 0, eLen[0], (byte) 0);
            getNativeExt(ext, eLen[0], el);
            if (invert) {
                Common.invertMemory(ext, el[0]);
            }
            el[0] = Common.trimRight(ext, el[0], (byte) trim);
            el[0] = Common.trimRight(ext, el[0], (byte) space);
            el[0] = Common.trimRight(ext, el[0], (byte) term);
            if (isUsed()) eLen[0] = Common.shrinkString(ext, el[0]);
            else eLen[0] = el[0];
        }
    }

    /**
     * Whether file names (excluding extension) match
     *
     * @param name  File name
     * @param caseInsensitive Whether to ignore case
     */
    public boolean isSameName(String name, boolean caseInsensitive) {
        if (!isUsedAndVisible()) return false;
        byte[] sName = new byte[256];
        byte[] dName = new byte[256];
        int[] sNLen = new int[] {sName.length};
        int[] dNLen = new int[] {dName.length};
        int[] sELen = new int[] {0};
        int[] dELen = new int[] {0};
        toNativeFileName(name, dName, dNLen, null, dELen);
        getNativeFileName(sName, sNLen, null, sELen);
        if (caseInsensitive) {
            toUpper(sName, sNLen[0]);
            toUpper(dName, dNLen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sName, Math.max(sNLen[0], dNLen[0])), Arrays.copyOf(dName, Math.max(sNLen[0], dNLen[0])));
    }

    /**
     * Whether same file names
     * Whether match by File name + Extension + Extended attribute
     *
     * @param filename File name
     * @param caseInsensitive    Whether to ignore case
     * @see #getOptionalName()
     */
    public boolean isSameFileName(DiskBasicFileName filename, boolean caseInsensitive) {
        if (!isUsedAndVisible()) return false;
        byte[] sName = new byte[256];
        byte[] sExt = new byte[256];
        byte[] dName = new byte[256];
        byte[] dExt = new byte[256];
        int[] sNLen = new int[] {sName.length};
        int[] ELen = new int[] {sExt.length};
        int[] dNLen = new int[] {dName.length};
        int[] dELen = new int[] {dExt.length};
        toNativeFileName(filename.getName(), dName, dNLen, dExt, dELen);
        getNativeFileName(sName, sNLen, sExt, ELen);
        if (caseInsensitive) {
            toUpper(sName, sNLen[0]);
            toUpper(sExt, ELen[0]);
            toUpper(dName, dNLen[0]);
            toUpper(dExt, dELen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sName, Math.max(sNLen[0], dNLen[0])), Arrays.copyOf(dName, Math.max(sNLen[0], dNLen[0]))) &&
                ((dELen[0] == 0 && ELen[0] == 0) || Arrays.equals(Arrays.copyOf(sExt, Math.max(ELen[0], dELen[0])), Arrays.copyOf(dExt, Math.max(ELen[0], dELen[0])))) &&
                (getOptionalName() == filename.getOptional());
    }

    /**
     * Whether same file names
     * Whether match by File name + Extension + Extended attribute
     *
     * @param src   Comparison target
     * @param iCase Whether to ignore case
     * @see #getOptionalName()
     */
    public boolean isSameFileName(DiskBasicDirItem<T> src, boolean iCase) {
        if (!isUsedAndVisible()) return false;
        byte[] sName = new byte[256];
        byte[] sExt = new byte[256];
        byte[] dName = new byte[256];
        byte[] dExt = new byte[256];
        int[] sNLen = new int[] {sName.length};
        int[] sELen = new int[] {sExt.length};
        int[] dNLen = new int[] {dName.length};
        int[] dELen = new int[] {dExt.length};
        src.getNativeFileName(dName, dNLen, dExt, dELen);
        getNativeFileName(sName, sNLen, sExt, sELen);
        if (iCase) {
            toUpper(sName, sNLen[0]);
            toUpper(sExt, sELen[0]);
            toUpper(dName, dNLen[0]);
            toUpper(dExt, dELen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sName, Math.max(sNLen[0], dNLen[0])), Arrays.copyOf(dName, Math.max(sNLen[0], dNLen[0]))) &&
                ((dELen[0] == 0 && sELen[0] == 0) || Arrays.equals(Arrays.copyOf(sExt, Math.max(sELen[0], dELen[0])), Arrays.copyOf(dExt, Math.max(sELen[0], dELen[0])))) &&
                (src.getOptionalName() == getOptionalName());
    }

    /**
     * Make lowercase to uppercase
     *
     * @param str  [in,out] String
     * @param size Length
     */
    public static void toUpper(byte[] str, int size) {
        Common.toUpper(str, size);
    }

    /**
     * Size of File name + Extension
     *
     * @return Size
     */
    public int getFileNameStrSize() {
        int nl = 0;
        int el = 0;
        int[] l = new int[1];
        int num = 0;
        do {
            l[0] = 0;
            getFileNamePos(num, l);
            if (l[0] == 0) break;
            nl += l[0];
            num++;
        } while (l[0] > 0);
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        el = extLen[0];
        if (el > 0) el++;
        return nl + el;
    }

    public void convertFileNameBeforeImportDialog(String filename) {
    }

    public void setOptionalName(int val) {
    }

    public int getOptionalName() {
        return 0;
    }

    /**
     * Copy string to buffer
     *
     * @param src   File name
     * @param sSize File name size
     * @param sLen  File name length
     * @param dst   [out] Destination buffer
     * @param dSize Above buffer size
     * @param dLen  [out] Above length
     */
    public static void memoryCopy(byte[] src, int sSize, int sLen, byte[] dst, int dSize, int[] dLen) {
        if (dst == null || dSize == 0) {
            dLen[0] = 0;
            return;
        }
        if (dSize > sLen) Arrays.fill(dst, sLen, dSize, (byte) 0);
        if (sLen > 0) System.arraycopy(src, 0, dst, 0, sLen);
        dLen[0] = sLen;
    }

    /**
     * Copy string to buffer Separate extension with spChr (usually ".")
     *
     * @param src    File name
     * @param sSize  File name size
     * @param sLen   File name length
     * @param dName  [out] Destination file name buffer
     * @param dNSize Above file name buffer size
     * @param dNLen  [out] Above file name length
     * @param dExt   [out] Destination extension buffer
     * @param dESize Above extension buffer size
     * @param dELen  [out] Above extension length
     * @param spChr  Character used for split
     */
    public static void splitFileName(byte[] src, int sSize, int sLen, byte[] dName, int dNSize, int[] dNLen, byte[] dExt, int dESize, int[] dELen, byte spChr) {
        int pos = -1;
        for (int i = sLen - 1; i >= 0; i--) {
            if (src[i] == spChr) {
                pos = i;
                break;
            }
        }
        if (dESize > 0 && pos >= 0) {
            memoryCopy(src, sSize, pos, dName, dNSize, dNLen);
            pos++;
            memoryCopy(Arrays.copyOfRange(src, pos, src.length), sSize - pos, sLen - pos, dExt, dESize, dELen);
        } else {
            memoryCopy(src, sSize, sLen, dName, dNSize, dNLen);
            memoryCopy(null, 0, 0, dExt, dESize, dELen);
        }
    }

    /**
     * Set attribute
     *
     * @param fileType Attribute
     */
    public void setFileAttr(DiskBasicFileType fileType) {
    }

    public void setFileAttr(int formatType, int fileType, int originalType0) {
        setFileAttr(formatType, fileType, originalType0, 0, 0);
    }

    public void setFileAttr(int formatType, int fileType, int originalType0, int originalType1) {
        setFileAttr(formatType, fileType, originalType0, originalType1, 0);
    }

    /**
     * Set attribute
     *
     * @param formatType    Format type DiskBasicFormatType
     * @param fileType      Combination of common attributes file_type_mask
     * @param originalType0 Original attribute
     * @param originalType1 Original attribute continued 1
     * @param originalType2 Original attribute continued 2
     */
    public void setFileAttr(int formatType, int fileType, int originalType0, int originalType1, int originalType2) {
        setFileAttr(new DiskBasicFileType(formatType, fileType, originalType0, originalType1, originalType2));
    }

    /**
     * Returns attribute
     *
     * @return Attribute
     */
    public DiskBasicFileType getFileAttr() {
        return new DiskBasicFileType();
    }

    /**
     * Returns attribute string (for file list screen display)
     *
     * @return String
     */
    public String getFileAttrStr() {
        return "";
    }


    public void setExternalAttr(int val) {
        externalAttr = val;
    }

    public int getExternalAttr() {
        return externalAttr;
    }

    /**
     * Whether normal file Used in check for directory deletion
     *
     * @return true if normal file
     */
    public boolean isNormalFile() {
        DiskBasicFileType attr = getFileAttr();
        if (attr.isVolume()) {
            return false;
        } else if (attr.isDirectory()) {
            String name = getFileNameStr();
            if (name.equals(".") || name.equals("..")) {
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    /**
     * Whether directory
     *
     * @return true if directory
     */
    public boolean isDirectory() {
        return getFileAttr().isDirectory();
    }

    /**
     * Set file size
     *
     * @param val Size
     */
    public void setFileSize(int val) {
        groups.setSize(val);
    }

    /**
     * Returns file size
     *
     * @return Size
     */
    public int getFileSize() {
        return groups.getSize();
    }

    /**
     * Set directory size
     *
     * @param val Size
     */
    public void setDirectorySize(int val) {
        setFileSize(val);
    }

    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
    }

    /**
     * Get all groups of the specified directory
     *
     * @param groupItems [out] Group list
     */
    public void getAllGroups(DiskBasicGroups groupItems) throws IOException {
        groupItems.clear();
        getUnitGroups(0, groupItems);
    }

    /**
     * Set group count
     *
     * @param val Count
     */
    public void setGroupSize(int val) {
        groups.setNums(val);
    }

    /**
     * Returns group count
     *
     * @return Count
     */
    public int getGroupSize() {
        return groups.getNums();
    }


    public void setStartGroup(int fileunitNum, int val) {
        setStartGroup(fileunitNum, val, 0);
    }

    /**
     * Set first group number
     *
     * @param fileUnitNum File number
     * @param val         Number
     * @param size        Size (model dependent)
     */
    public void setStartGroup(int fileUnitNum, int val, int size) {
    }

    /**
     * Set first group number
     *
     * @param fileUnitNum File number
     */
    public int getStartGroup(int fileUnitNum) {
        return 0;
    }

    /**
     * Set extra group number (model dependent)
     *
     * @param val Number
     */
    public void setExtraGroup(int val) {
    }

    /**
     * Returns extra group number (model dependent)
     *
     * @return Number
     */
    public int getExtraGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /** Set extra group list (model dependent) */
    public void setExtraGroups(DiskBasicGroups grps) {
    }

    /** Get extra group number (model dependent) */
    public void getExtraGroups(List<Integer> arr) {
    }

    /** Returns extra group list (model dependent) */
    public void getExtraGroups(DiskBasicGroups[] grps) {
    }

    /**
     * Set next group number (model dependent)
     *
     * @param val Number
     */
    public void setNextGroup(int val) {
    }

    /** Returns next group number (model dependent) */
    public int getNextGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Set last group number (model dependent)
     *
     * @param val Number
     */
    public void setLastGroup(int val) {
    }

    /**
     * Returns last group number (model dependent)
     *
     * @return Number
     */
    public int getLastGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Set parent group number (model dependent)
     *
     * @param val Number
     */
    public void setParentGroup(int val) {
    }

    /**
     * Returns parent group number (model dependent)
     *
     * @return Number
     */
    public int getParentGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /** Returns group list count */
    public int getGroupCount() {
        return groups.size();
    }

    /** Returns group list */
    public DiskBasicGroups getGroups() {
        return groups;
    }

    /** Set group list */
    public void setGroups(DiskBasicGroups vals) {
        groups = vals;
    }

    /** Returns group list item */
    public DiskBasicGroupItem getGroup(int index) {
        return groups.get(index);
    }

    public void clearChainSector(DiskBasicDirItem<T> pItem /* = null */) throws IOException {
    }

    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<T> pItem /* = null */) throws IOException {
    }

    public void setChainSector(DiskImageSector sector, int num, byte[] data, DiskBasicDirItem<T> pItem /* = null */) throws IOException {
    }

    public void setChainSector(int num, int pos, byte[] data, DiskBasicDirItem<T> pitem /* = null */) {
    }

    public void addChainGroupNumber(int idx, int val) throws IOException {
    }

    public boolean hasCreateDateTime() {
        return false;
    }

    public boolean hasCreateDate() {
        return false;
    }

    public boolean hasCreateTime() {
        return false;
    }

    public boolean hasModifyDateTime() {
        return false;
    }

    public boolean hasModifyDate() {
        return false;
    }

    public boolean hasModifyTime() {
        return false;
    }

    public boolean hasAccessDateTime() {
        return false;
    }

    public boolean hasAccessDate() {
        return false;
    }

    public boolean hasAccessTime() {
        return false;
    }

    public static final int DATETIME_NONE = 0;
    public static final int DATETIME_CREATE = 0x01;
    public static final int DATETIME_MODIFY = 0x02;
    public static final int DATETIME_CREATE_MODIFY = 0x03;
    public static final int DATETIME_ACCESS = 0x04;
    public static final int DATETIME_CREATE_ACCESS = 0x05;
    public static final int DATETIME_MODIFY_ACCESS = 0x06;
    public static final int DATETIME_ALL = 0x07;

    public int canIgnoreDateTime() {
        return DATETIME_NONE;
    }

    /**
     * Get creation date
     *
     * @param tm [out] Date
     */
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    /**
     * Get creation time
     *
     * @param tm [out] Time
     */
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    /**
     * Get creation date and time
     *
     * @param tm [out] Date and time
     */
    public void getFileCreateDateTime(LocalDateTime tm) {
        getFileCreateDate(tm);
        getFileCreateTime(tm);
    }

    /** Returns creation date and time */
    public LocalDateTime getFileCreateDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDateTime(tm);
        return tm;
    }

    /**
     * Returns creation date of file as string
     *
     * @return Date string
     */
    @Deprecated
    public String getFileCreateDateStr() {
        return "";
    }

    /**
     * Returns creation time of file as string
     *
     * @return Time string
     */
    @Deprecated
    public String getFileCreateTimeStr() {
        return "";
    }

    /**
     * Returns creation date and time of file as string
     *
     * @return Date and time string, "---" if none
     */
    @Deprecated
    public String getFileCreateDateTimeStr() {
        String str = getFileCreateDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileCreateTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    /**
     * Get modification date
     *
     * @param tm [out] Date
     */
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    /**
     * Get modification time
     *
     * @param tm [out] Time
     */
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    /**
     * Get modification date and time
     *
     * @param tm [out] Date and time
     */
    public void getFileModifyDateTime(LocalDateTime tm) {
        getFileModifyDate(tm);
        getFileModifyTime(tm);
    }

    /** Returns modification date and time */
    public LocalDateTime getFileModifyDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileModifyDateTime(tm);
        return tm;
    }

    /**
     * Modification date title name (for dialog)
     *
     * @return Title string
     */
    @Deprecated
    public String getFileModifyDateStr() {
        return "";
    }

    /**
     * Returns modification date of file as string
     *
     * @return Date string
     */
    @Deprecated
    public String getFileModifyTimeStr() {
        return "";
    }

    /**
     * Returns modification date and time of file as string
     *
     * @return Date and time string, "---" if none
     */
    @Deprecated
    public String getFileModifyDateTimeStr() {
        String str = getFileModifyDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileModifyTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    /**
     * Get access date
     *
     * @return Date
     */
    public LocalDate getFileAccessDate() {
        return LocalDate.now();
    }

    /**
     * Get access time
     *
     * @return Time
     */
    public LocalTime getFileAccessTime() {
        return LocalTime.now();
    }

    /**
     * Get access date and time
     *
     * @param tm [out] Date and time
     */
    public void getFileAccessDateTime(LocalDateTime tm) {
        getFileAccessDate();
        getFileAccessTime();
    }

    /** Returns access date and time */
    public LocalDateTime getFileAccessDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileAccessDateTime(tm);
        return tm;
    }

    /**
     * Returns access date of file as string
     *
     * @return Date string
     */
    @Deprecated
    public String getFileAccessDateStr() {
        return "";
    }

    /**
     * Returns access time of file as string
     *
     * @return Time string
     */
    @Deprecated
    public String getFileAccessTimeStr() {
        return "";
    }

    /**
     * Returns access date and time of file as string
     *
     * @return Date and time string, "---" if none
     */
    @Deprecated
    public String getFileAccessDateTimeStr() {
        String str = getFileAccessDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileAccessTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    /**
     * Set creation date and time
     *
     * @param tm Date and time
     */
    public void setFileCreateDate(LocalDateTime tm) {
    }

    public void setFileCreateTime(LocalDateTime tm) {
    }

    public void setFileCreateDateTime(LocalDateTime tm) {
        setFileCreateDate(tm);
        setFileCreateTime(tm);
    }

    /**
     * Title name of creation date (for dialog)
     *
     * @return Title string
     */
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

    /**
     * Set modification date and time
     *
     * @param tm Date and time
     */
    public void setFileModifyDate(LocalDateTime tm) {
    }

    public void setFileModifyTime(LocalDateTime tm) {
    }

    public void setFileModifyDateTime(LocalDateTime tm) {
        setFileModifyDate(tm);
        setFileModifyTime(tm);
    }

    public String getFileModifyDateTimeTitle() {
        return "Modified Date";
    }

    public void setFileAccessDate(LocalDateTime tm) {
    }

    public void setFileAccessTime(LocalDateTime tm) {
    }

    /**
     * Set access date and time
     *
     * @param tm Date and time
     */
    public void setFileAccessDateTime(LocalDateTime tm) {
        setFileAccessDate(tm);
        setFileAccessTime(tm);
    }

    /**
     * Title name of access date (for dialog)
     *
     * @return Title string
     */
    @Deprecated
    public String getFileAccessDateTimeTitle() {
        return "Accessed Date";
    }

    /** Returns display order of date and time (for dialog) */
    public int getFileDateTimeOrder(int idx) {
        return idx;
    }

    /** Returns date and time (for file list) */
    @Deprecated
    public String getFileDateTimeStr() {
        return getFileCreateDateTimeStr();
    }

    /** Write date and time to the specified file */
    public void writeFileDateTime(String path) {
        if (!(hasCreateDateTime() || hasModifyDateTime() || hasAccessDateTime())) {
            return;
        }
        if (!Files.exists(Paths.get(path))) {
            return;
        }
        // Implementation depends on file system capabilities
    }

    /** Read date and time from the specified file */
    public void readFileDateTime(String path, DiskBasicDirItemAttr dateTime) {
        LocalDateTime tm = LocalDateTime.now();
        if (!(hasCreateDateTime() || hasModifyDateTime() || hasAccessDateTime())) {
            return;
        }
        dateTime.setCreateDateTime(tm);
        dateTime.setModifyDateTime(tm);
        dateTime.setAccessDateTime(tm);
    }

    public boolean hasAddress() {
        return false;
    }

    public boolean hasExecuteAddress() {
        return hasAddress();
    }

    public boolean isAddressEditable() {
        return hasAddress();
    }

    public int getStartAddress() {
        return -1;
    }

    public int getEndAddress() {
        return -1;
    }

    public int getExecuteAddress() {
        return -1;
    }

    public void setStartAddress(int val) {
    }

    public void setEndAddress(int val) {
    }

    public void setExecuteAddress(int val) {
    }

    public boolean checkUsed(boolean unuse) {
        return false;
    }

    /** Whether it is a used item */
    public boolean isUsed() {
        return (flags & USED_ITEM) != 0;
    }

    /** Set whether it is used */
    public void used(boolean val) {
        flags = val ? (flags | USED_ITEM) : (flags & ~USED_ITEM);
    }

    /** Whether it is an item to be displayed in list */
    public boolean isVisible() {
        return (flags & VISIBLE_LIST) != 0;
    }

    /** Set whether to display in list */
    public void visible(boolean val) {
        flags = val ? (flags | VISIBLE_LIST) : (flags & ~VISIBLE_LIST);
    }

    /** Whether it is an item that is used and to be displayed in list */
    public boolean isUsedAndVisible() {
        return (flags & (USED_ITEM | VISIBLE_LIST)) == (USED_ITEM | VISIBLE_LIST);
    }

    /** Whether it is an item to be displayed in tree */
    public boolean isVisibleOnTree() {
        return (flags & VISIBLE_TREE) != 0;
    }

    /** Set whether to display in tree */
    public void visibleOnTree(boolean val) {
        flags = val ? (flags | VISIBLE_TREE) : (flags & ~VISIBLE_TREE);
    }

    /**
     * Copy file name and attributes
     *
     * @param src Directory item
     */
    public void copyItem(DiskBasicDirItem<T> src) {
        // Copy data
        copyData(src.getRawData());
        // Group
        groups = src.groups;
        // Size
//	    fileSize = src.fileSize;
        // Flag
        flags = src.flags;
        // Other attributes
        externalAttr = src.externalAttr;
    }

    public abstract int getDataSize();

    public abstract T getData();

    // vavi for debug
    public byte[] getRawData() {
        return null;
    }

    public abstract boolean copyData(byte[] val);

    public abstract void clearData();

    /** Initialize directory and make it unused */
    public void initialData() {
        clearData();
    }

    public boolean needCheckEofCode() {
        return false;
    }

    /** Returns termination code of file */
    public byte getEofCode() {
        return basic.getTextTerminateCode();
    }

    public boolean needChainInData() {
        return false;
    }

    /**
     * Necessary processing before exporting data
     *
     * @param filename [in,out] File name
     * @return false This file is excluded
     */
    public boolean preExportDataFile(String[] filename) {
        addExtensionByFileAttr(getFileAttr().getType(), FILE_TYPE_EXTENSION_MASK, filename, false);
        return true;
    }

    /**
     * Necessary processing before importing data
     *
     * @param filename [in,out] File name
     * @return false This file is excluded
     */
    public boolean preImportDataFile(String[] filename) {
        trimLastExtensionByExtensionAttr(filename[0]);
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * Get file size of file number
     * Override is required when multiple files are managed in one entry
     *
     * @param fileUnitNum File number
     * @param iStream     [in,out] Input stream
     * @param fileOffset  Offset in stream
     * @return File size / -1 if none
     */
    public int getFileUnitSize(int fileUnitNum, InputStream iStream, int fileOffset) throws IOException {
        if (fileUnitNum == 0) {
            return iStream.available();
        } else {
            return -1;
        }
    }

    /**
     * Whether file of file number can be accessed
     * Override is required when multiple files are managed in one entry
     *
     * @param fileUnitNum File number
     */
    public boolean isValidFileUnit(int fileUnitNum) {
        return fileUnitNum == 0;
    }

    /** Determine attributes from file name */
    public int convFileTypeFromFileName(String filename) {
        return 0;
    }

    /** Determine attributes from file name */
    public int convOriginalTypeFromFileName(String filename) {
        return 0;
    }

    /** Determine extended attributes from file name */
    public int convOptionalNameFromFileName(String filename) {
        return 0;
    }

    /**
     * Process attribute values
     * <p>
     * Process the input attribute values if necessary after import dialog input
     */
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        return true;
    }

    /** Set other attribute values */
    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
    }

    /**
     * Convert string to byte array. Character encoding is model dependent
     *
     * @return >=0 Number of bytes
     */
    public int convStringToChars(String src, byte[] dst, int len) {
        System.arraycopy(src.getBytes(), 0, dst, 0, len);
        return len;
    }

    /** Convert byte array to string. Character encoding is model dependent */
    public String convCharsToString(byte[] src, int len) {
        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(src, 0, len, sb, -1);
        return sb.toString();
    }

    /**
     * Output file attributes in XML
     *
     * @param path Output XML file path
     */
    public boolean writeFileAttrToXml(String path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("FileInformation");
            doc.appendChild(root);

            DiskBasicFileType fileType = getFileAttr();

            Element external1 = doc.createElement("ExternalAttribute");
            external1.setTextContent(String.format("0x%x", getExternalAttr()));
            root.appendChild(external1);

            LocalDateTime ctm = LocalDateTime.now();
            getFileCreateDateTime(ctm);
            Element time1 = doc.createElement("CreateTime");
            time1.setTextContent(Utils.formatHMSStr(ctm.toLocalTime()));
            root.appendChild(time1);
            Element date1 = doc.createElement("CreateDate");
            date1.setTextContent(Utils.formatYMDStr(ctm.toLocalDate()));
            root.appendChild(date1);

            LocalDateTime mtm = LocalDateTime.now();
            getFileModifyDateTime(mtm);
            time1 = doc.createElement("ModifyTime");
            time1.setTextContent(Utils.formatHMSStr(mtm.toLocalTime()));
            root.appendChild(time1);
            date1 = doc.createElement("ModifyDate");
            date1.setTextContent(Utils.formatYMDStr(mtm.toLocalDate()));
            root.appendChild(date1);

            LocalDateTime atm = LocalDateTime.now();
            getFileAccessDateTime(atm);
            time1 = doc.createElement("AccessTime");
            time1.setTextContent(Utils.formatHMSStr(atm.toLocalTime()));
            root.appendChild(time1);
            date1 = doc.createElement("AccessDate");
            date1.setTextContent(Utils.formatYMDStr(atm.toLocalDate()));
            root.appendChild(date1);

            int val = getExecuteAddress();
            if (val >= 0) {
                Element exec1 = doc.createElement("ExecuteAddress");
                exec1.setTextContent(String.format("0x%x", val));
                root.appendChild(exec1);
            }

            val = getEndAddress();
            if (val >= 0) {
                Element end1 = doc.createElement("EndAddress");
                end1.setTextContent(String.format("0x%x", val));
                root.appendChild(end1);
            }

            val = getStartAddress();
            if (val >= 0) {
                Element start1 = doc.createElement("StartAddress");
                start1.setTextContent(String.format("0x%x", val));
                root.appendChild(start1);
            }

            Element size1 = doc.createElement("Size");
            size1.setTextContent(String.format("%d", getFileSize()));
            root.appendChild(size1);

            for (int i = 2; i >= 0; i--) {
                String label = String.format("OriginalType%d", i);
                Element orig1 = doc.createElement(label);
                orig1.setTextContent(String.format("0x%x", fileType.getOrigin(i)));
                root.appendChild(orig1);
            }

            Element type1 = doc.createElement("Type");
            type1.setTextContent(String.format("0x%x", fileType.getType()));
            root.appendChild(type1);

            byte[] fname = new byte[256];
            byte[] fext = new byte[256];
            int[] fnl = new int[] {fname.length};
            int[] fel = new int[] {fext.length};
            getNativeFileName(fname, fnl, fext, fel);

            Element ext1 = doc.createElement("Ext");
            ext1.setTextContent(Utils.encodeEscape(fext, fel[0]));
            root.appendChild(ext1);

            Element name1 = doc.createElement("Name");
            name1.setTextContent(Utils.encodeEscape(fname, fnl[0]));
            root.appendChild(name1);

            Element fmt1 = doc.createElement("Format");
            fmt1.setTextContent(String.format("%s", fileType.getFormat()));
            root.appendChild(fmt1);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(path));
            transformer.transform(source, result);

            return true;
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Read file attributes from XML
     *
     * @param path XML file path
     * @param attr [out] Attributes within item (for holding date and time)
     * @return true: Normal, false: Cannot read file
     */
    public boolean readFileAttrFromXml(String path, DiskBasicDirItemAttr attr) {
        try {
            if (!Files.exists(Paths.get(path))) return false;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(path));

            Element root = doc.getDocumentElement();
            if (root == null) return false;

            int formatType = 0;
            int fileType = 0;
            int originalType0 = 0;
            int originalType1 = 0;
            int originalType2 = 0;
            int fileSize = 0;
            int startAddr = -1;
            int endAddr = -1;
            int execAddr = -1;
            int externalAttr = 0;
            LocalDateTime tmCTmp = LocalDateTime.now();
            LocalDateTime tmMTmp = LocalDateTime.now();
            LocalDateTime tmATmp = LocalDateTime.now();

            byte[] fileName = new byte[256];
            byte[] fileExt = new byte[256];
            int fileNameLen = fileName.length;
            int fileExtLen = fileExt.length;

            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String name = node.getNodeName();
                    String content = node.getTextContent();

                    switch (name) {
                        case "Format" -> formatType = Integer.parseInt(content);
                        case "Name" -> Utils.decodeEscape(content, fileName, fileNameLen);
                        case "Ext" -> Utils.decodeEscape(content, fileExt, fileExtLen);
                        case "Type" ->
                                fileType = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "OriginalType0" ->
                                originalType0 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "OriginalType1" ->
                                originalType1 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "OriginalType2" ->
                                originalType2 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "Size" -> fileSize = Integer.parseInt(content);
                        case "StartAddress" ->
                                startAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "EndAddress" ->
                                endAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "ExecuteAddress" ->
                                execAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "ExternalAttribute" ->
                                externalAttr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    }
                }
            }

            fileNameLen = Common.getStringLength(fileName, fileNameLen, (byte) 0);
            fileExtLen = Common.getStringLength(fileExt, fileExtLen, (byte) 0);
            setNativeFileName(fileName, fileName.length, fileNameLen, fileExt, fileExt.length, fileExtLen);
            setFileAttr(formatType, fileType, originalType0, originalType1, originalType2);
            setFileSize(fileSize);
            setStartAddress(startAddr);
            setEndAddress(endAddr);
            setExecuteAddress(execAddr);
            setExternalAttr(externalAttr);
            if (attr != null) {
                attr.setCreateDateTime(tmCTmp);
                attr.setModifyDateTime(tmMTmp);
                attr.setAccessDateTime(tmATmp);
            }

            return true;
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }
    }

    public void setSector(DiskImageSector val) {
        sector = val;
    }

    /** Mark the sector the item belongs to as modified (not implemented) */
    public void setModify() {
    }

    public DiskBasic getBasic() {
        return basic;
    }

    public int getNumber() {
        return num;
    }

    public void setNumber(int val) {
        num = val;
    }

    public int getPosition() {
        return position;
    }

    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {
    }

    public void calcFileUnitSize(int fileUnitNum) throws IOException {
    }

    /** Calculate file size and group count */
    public void calcFileSize() throws IOException {
        groups.clear();
        calcFileUnitSize(0);
    }

    /**
     * Check termination code of file and return required size
     *
     * @param iStream  Input stream
     * @param fileSize Original data size of input stream
     * @return Size with termination code added (original size + 1)
     */
    public int checkEofCode(InputStream iStream, int fileSize) throws IOException {
        iStream.skipNBytes(fileSize - 1);
        byte[] c = new byte[1];
        iStream.read(c);
        if (c[0] != getEofCode()) {
            fileSize++;
        }
        iStream.reset();
        return fileSize;
    }

    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        return occupiedSize;
    }

    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        return fileSize;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1) : "";
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(0, lastDot) : filename;
    }

    /** Class to temporarily collect attribute values */
    public static class DiskBasicDirItemAttr {

        private boolean rename;

        private DiskBasicFileName name;
        private boolean ignoreType;

        private DiskBasicFileType type;

        private int startAddr;
        private int endAddr;
        private int execAddr;

        private boolean ignoreDateTime;

        private LocalDateTime createDateTime;
        private LocalDateTime modifyDateTime;
        private LocalDateTime accessDateTime;
        private LocalDateTime externDateTime;

        public DiskBasicDirItemAttr() {
            rename = false;
            name = new DiskBasicFileName();
            ignoreType = false;
            type = new DiskBasicFileType();
            startAddr = -1;
            endAddr = -1;
            execAddr = -1;
            ignoreDateTime = false;
            createDateTime = LocalDateTime.now();
            modifyDateTime = LocalDateTime.now();
            accessDateTime = LocalDateTime.now();
            externDateTime = LocalDateTime.now();
        }

        public void renameable(boolean val) {
            rename = val;
        }

        public void setFileName(DiskBasicFileName val) {
            name = val;
        }

        /**
         * Set file name
         *
         * @param nName     File name
         * @param nOptional Attribute of file name
         */
        public void setFileName(String nName, int nOptional) {
            name.setName(nName);
            name.setOptional(nOptional);
        }

        public void ignoreFileAttr(boolean val) {
            ignoreType = val;
        }

        public void setFileAttr(DiskBasicFileType val) {
            type = val;
        }

        public void setFileAttr(int nFormat, int type, int origin0 /* = 0 */) {
            setFileAttr(nFormat, type, origin0, 0, 0);
        }

        /**
         * Set attributes
         *
         * @param format  Format type
         * @param type    Common attributes
         * @param origin0 Specific attribute
         * @param origin1 Specific attribute continued 1
         * @param origin2 Specific attribute continued 2
         */
        public void setFileAttr(int dormat, int type, int origin0, int origin1, int origin2 /* = 0 */) {
            this.type.setFormat(dormat);
            this.type.setType(type);
            this.type.setOrigin(0, origin0);
            this.type.setOrigin(1, origin1);
            this.type.setOrigin(2, origin2);
        }

        /**
         * Set common attributes
         *
         * @param type Common attribute
         */
        public void setFileType(int type) {
            this.type.setType(type);
        }

        /**
         * Set specific attribute
         *
         * @param idx    0..2
         * @param origin Specific attribute
         */
        public void setFileOriginAttr(int idx, int origin) {
            type.setOrigin(idx, origin);
        }

        /**
         * Set specific attribute
         *
         * @param origin Specific attribute
         */
        public void setFileOriginAttr(int origin) {
            type.setOrigin(origin);
        }

        public void setStartAddress(int val) {
            startAddr = val;
        }

        public void setEndAddress(int val) {
            endAddr = val;
        }

        public void setExecuteAddress(int val) {
            execAddr = val;
        }

        public void ignoreDateTime(boolean val) {
            ignoreDateTime = val;
        }

        public void setCreateDateTime(LocalDateTime tm) {
            createDateTime = tm;
        }

        public void setModifyDateTime(LocalDateTime tm) {
            modifyDateTime = tm;
        }

        public void setAccessDateTime(LocalDateTime tm) {
            accessDateTime = tm;
        }

        public void setExternDateTime(LocalDateTime tm) {
            externDateTime = tm;
        }

        public boolean isRenameable() {
            return rename;
        }

        public DiskBasicFileName getFileName() {
            return name;
        }

        public boolean doesIgnoreFileAttr() {
            return ignoreType;
        }

        public DiskBasicFileType getFileAttr() {
            return type;
        }

        /** Get common attributes */
        public int getFileType() {
            return type.getType();
        }

        /**
         * Get specific attribute
         *
         * @param idx 0..2
         */
        public int getFileOriginAttr(int idx /* = 0 */) {
            return type.getOrigin(idx);
        }

        public int getStartAddress() {
            return startAddr;
        }

        public int getEndAddress() {
            return endAddr;
        }

        public int getExecuteAddress() {
            return execAddr;
        }

        public boolean doesIgnoreDateTime() {
            return ignoreDateTime;
        }

        public LocalDateTime getCreateDateTime() {
            return createDateTime;
        }

        public LocalDateTime getModifyDateTime() {
            return modifyDateTime;
        }

        public LocalDateTime getAccessDateTime() {
            return accessDateTime;
        }

        public LocalDateTime getExternDateTime() {
            return externDateTime;
        }
    }

    /** Process directory items that span sectors */
    public static class DirItemSectorBoundary {

        private static class SectorInfo {

            byte[] data;
            int size;
            int pos;

            SectorInfo() {
                data = null;
                size = 0;
                pos = 0xffff;
            }
        }

        private final SectorInfo[] s;

        public DirItemSectorBoundary() {
            s = new SectorInfo[2];
            for (int i = 0; i < 2; i++) {
                s[i] = new SectorInfo();
            }
            clear();
        }

        /** Initialization */
        public void clear() {
            for (int i = 0; i < 2; i++) {
                s[i].data = null;
                s[i].size = 0;
                s[i].pos = 0xffff;
            }
        }

        /**
         * Set position information of the item
         *
         * @param basic    Disk Basic
         * @param sector   Sector
         * @param position Item position
         * @param itemData Item data
         * @param itemSize Item size
         * @param next     Next sector
         * @return true if there is a sector span
         */
        public <T extends Directory> boolean set(DiskBasic basic, DiskImageSector sector, int position, byte[] itemData, int itemSize, SectorParam next) throws IOException {
            if (sector == null) return false;

            int sPos = position;
            int sSize = sector.getSectorSize();
            byte[] sptr = itemData;
            if (itemData != null) {
                s[0].data = sptr;
                s[0].size = itemSize;
                s[0].pos = 0;
            }
//logger.log(Level.DEBUG, "spos: " + spos + ", itemSize: " + itemSize + ", ssize: " + ssize);
            //               +- spos             +- ssize
            //               |<---- s[1].pos --->|<- s[1].size ->|
            // --------------+-------------------+---------------+-------
            //           curr sector             |           next sector
            // --------------+-------------------+---------------+-------
            //               |<----------- itemSize ------------>|
            //               |<---- s[0].size -->|
            //               +- s[0].data
            //
            if (sPos + itemSize >= sSize) {
                // Sector span
                s[1].pos = sSize - sPos;
                s[1].size = s[0].size - s[1].pos;
                s[0].size = s[1].pos;

                // Next sector
                if (next != null) {
                    DiskBasicType<Directory> type = basic.getType();
                    int nSectorPos = type.getSectorPosFromNum(next.getTrackNumber(), next.getSideNumber(), next.getSectorNumber());
                    int[] side = {next.getSideNumber()}, sec = {next.getSectorNumber()};
                    DiskImageSector nsector = basic.getManagedSector(next.getTrackNumber(), side, sec);
                    if (nsector != null) {
                        sptr = nsector.getSectorBuffer();
                        sSize = nsector.getSectorSize();
                        sPos = basic.getDirStartPosOnSector();
                        sPos += (nSectorPos % basic.getSectorsPerGroup()) == 0 ? basic.getDirStartPosOnGroup() : 0;
                        s[1].data = Arrays.copyOfRange(sptr, 0, s[1].size);
                        s[1].pos = 0;
                    }
                }
                return true;
            }
            return false;
        }

        /**
         * Copy
         *
         * @param dstItem [out] Item data
         */
        public void copyTo(byte[] dstItem) {
            for (int i = 0; i < 2; i++) {
                byte[] src = s[i].data;
                if (dstItem != null && src != null) {
                    System.arraycopy(src, s[i].pos, dstItem, s[i].pos, s[i].size);
                }
            }
        }

        /**
         * Copy
         *
         * @param srcItem Item data
         */
        public void copyFrom(byte[] srcItem) {
            for (int i = 0; i < 2; i++) {
                byte[] dst = s[i].data;
                if (dst != null && srcItem != null) {
                    System.arraycopy(srcItem, s[i].pos, dst, s[i].pos, s[i].size);
                }
            }
        }
    }
}
