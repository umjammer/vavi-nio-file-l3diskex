package l3diskex.basicfmt;

import java.io.ByteArrayOutputStream;
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

import l3diskex.Parambase.MyAttribute;
import l3diskex.Parambase.MyAttributes;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_EXTENSION_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


public abstract class DiskBasicDirItem<T extends DirectoryT> {

    private static final Logger logger = System.getLogger(DiskBasicDirItem.class.getName());

    protected static class DiskBasicDirData<TYPE extends DirectoryT> {
        private TYPE data;
        private boolean self;
        byte[] raw;

        public DiskBasicDirData() {
            data = null;
            self = false;
        }

        public void delete() {
            if (self) data = null;
            data = null;
            self = false;
        }

        public void alloc(Class<TYPE> clazz) {
            delete();
            try {
                data = clazz.getDeclaredConstructor().newInstance();
                self = true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Deprecated
        public void attach(TYPE data) {
            delete();
            this.data = data;
        }

        public void attach(byte[] data) {
            attach(data, data.length);
        }

        public void attach(byte[] data, int offset) {
            delete();
            this.raw = Arrays.copyOfRange(data, offset, data.length - offset);
        }

        public boolean copy(TYPE srcData) {
            return copy(srcData, 0xff_fffff, false, 0);
        }

        public boolean copy(TYPE srcData, int len) {
            return copy(srcData, len, false, 0);
        }

        public boolean copy(TYPE srcData, int len, boolean invert, int start) {
            if (data == null) return false;
            try {
                this.data = srcData;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Serdes.Util.serialize(srcData, baos);
                byte[] dst = baos.toByteArray();
                if (len > dst.length) len = dst.length;
                System.arraycopy(dst, 0, raw, start, len);
                if (invert) invert(len, start);
            } catch (IOException e) {
logger.log(Level.ERROR, e.toString(), e);
                return false;
            }
            return true;
        }

        public boolean fill(int ch) {
            return fill(ch, 0xff_fffff, false, 0);
        }

        public boolean fill(int ch, int len) {
            return fill(ch, len, false, 0);
        }

        public boolean fill(int ch, int len, boolean invert, int start /* = 0 */) {
            if (data == null) return false;
            byte[] dst = raw;
            if (len > dst.length) len = dst.length;
            Arrays.fill(dst, start, len, (byte)ch);
            if (invert) invert(len, start);
            return true;
        }

        public void invert(int len, int start) {
            byte[] arr = raw;
            if (len > arr.length) len = arr.length;
            for (int i = start; i < len; i++) {
                arr[i] = (byte)(~arr[i]);
            }
        }

        public TYPE data() {
            return data;
        }

        public boolean isValid() {
            return data != null;
        }

        public boolean isSelf() {
            return self;
        }

        // TODO sizeof
        public int getDataSize() {
            return raw.length;
        }

        public byte[] getRawData() {
            return raw;
        }
    }

    public static final int USED_ITEM = 0x0001;
    public static final int VISIBLE_LIST = 0x0002;
    public static final int VISIBLE_TREE = 0x0004;

    protected DiskBasic basic;
    protected DiskBasicType type;
    protected DiskBasicDirItem<T> parent;
    protected List<DiskBasicDirItem<T>> children;
    protected boolean validDir;
    protected int num;
    protected int position;
    protected int flags;
    protected DiskBasicGroups groups;
    protected DiskImageSector sector;
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
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    public DiskBasicDirItem(DiskBasicDirItem<T> src) {
        dup(src);
    }

    public DiskBasicDirItem(DiskBasic basic) {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = 0;
        sector = null;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    public DiskBasicDirItem(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData) {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = nSecpos;
        sector = nSector;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    public DiskBasicDirItem(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext, boolean[] nUnuse) {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = nNum;
        position = nSecpos;
        sector = nSector;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    protected void dup(DiskBasicDirItem<T> src) {
        this.basic = src.basic;
        this.type = src.type;
        this.parent = src.parent;
        if (src.children != null) {
            this.children = new ArrayList<>();
            this.children = src.children;
        } else {
            this.children = null;
        }
        this.validDir = src.validDir;
        this.num = src.num;
        this.position = src.position;
        this.groups = src.groups;
        this.sector = src.sector;
        this.externalAttr = src.externalAttr;
        this.flags = src.flags;
    }

    public void setDataPtr(int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext /* = null */) throws IOException {
        num = nNum;
        position = nSecpos;
        sector = nSector;
    }

    public void createChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
    }

    public void addChild(DiskBasicDirItem<T> newitem) {
        createChildren();
        children.add(newitem);
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

    public static boolean checkData(byte[] buf, int len, boolean[] last) {
        byte prev = 0;
        boolean valid = false;
        for (int i = 0; i < len; i++) {
            if (buf[i] == 0 || buf[i] == (byte)0xff) {
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

    public boolean isDeletable() {
        return getFileAttr().unmatchType(FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue());
    }

    public abstract boolean delete() throws IOException;

    public boolean hasEndMark() {
        return false;
    }

    public void setEndMark(DiskBasicDirItem<?> nextItem) throws IOException {
    }

    public void refresh() throws IOException {
        used(checkUsed(false));
        calcFileSize();
    }

    public boolean isLoadable() {
        return isDeletable();
    }

    public boolean isCopyable() {
        return getFileAttr().matchType(FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue(), 0);
    }

    public boolean isOverWritable() {
        return isCopyable();
    }

    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    protected byte[] getFileNamePos(int num, int[] len) {
        int[] size = new int[1];
        return getFileNamePos(num, size, len);
    }

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

    protected void setNativeName(byte[] filename, int size, int[] length) {
        int[] nl = new int[1];
        int[] ns = new int[1];
        byte[] n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            if (ns[0] > size) ns[0] = size;
            System.arraycopy(filename, 0, n, 0, ns[0]);
        }
    }

    protected void setNativeExt(byte[] fileext, int size, int length) {
        int[] el = new int[1];
        byte[] e = getFileExtPos(el);
        if (e != null && el[0] > 0) {
            if (el[0] > size) el[0] = size;
            System.arraycopy(fileext, 0, e, 0, el[0]);
        }
    }

    protected String getFileNamePlainStr() {
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

    protected boolean toNativeFileName(String filename, byte[] name, int[] nlen, byte[] ext, int[] elen) {
        byte[] tmp = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        String namestr = filename;
        if (basic.diskBasicParam.toUpperAfterRenamed()) {
            namestr = namestr.toUpperCase();
        }
        int tmplen = convStringToChars(namestr, tmp, tmp.length);
        if (tmplen < 0) return false;
        if (ext != null && elen[0] > 0) {
            int[] extLen = new int[1];
            getFileExtPos(extLen);
            elen[0] = extLen[0];
        }
        splitFileName(tmp, tmp.length, tmplen, name, nlen[0], nl, ext, elen[0], el, basic.diskBasicParam.getExtensionPreCode());
        nlen[0] = nl[0];
        elen[0] = el[0];
        return true;
    }

    protected String remakeFileNameAndExtStr(String filepath) {
        String newname = "";
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
            newname = filename.substring(0, Math.min(filename.length(), nl[0]));
        } else {
            String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
            String ext = lastDot >= 0 ? filename.substring(lastDot + 1) : "";
            newname = name.substring(0, Math.min(name.length(), nl[0]));
            if (!ext.isEmpty()) {
                newname += ".";
                newname += ext.substring(0, Math.min(ext.length(), el[0]));
            }
        }
        if (basic.diskBasicParam.toUpperBeforeDialog()) {
            newname = newname.toUpperCase();
        }
        convertFileNameBeforeImportDialog(newname);
        return newname;
    }

    protected String remakeFileNameOnlyStr(String filepath) {
        String newname = "";
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
        newname = name.substring(0, Math.min(name.length(), nl[0]));
        if (el[0] > 0) {
            if (!ext.isEmpty()) {
                newname += (char)basic.diskBasicParam.getExtensionPreCode();
                newname += ext.substring(0, Math.min(ext.length(), el[0]));
            }
        }
        if (basic.diskBasicParam.toUpperBeforeDialog()) {
            newname = newname.toUpperCase();
        }
        convertFileNameBeforeImportDialog(newname);
        return newname;
    }

    protected void addExtensionByFileAttr(int fileType, int mask, String[] filename, boolean dupli /* = false */) {
        Path path = Paths.get(filename[0]);
        String ext = getFileExtension(path.getFileName().toString());
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext, fileType, mask);
        if (sa == null) {
            sa = basic.diskBasicParam.getAttributesByExtension().findType(fileType, mask);
        }
        if (sa != null) {
            if (dupli || !ext.equalsIgnoreCase(sa.getName())) {
                filename[0] += ".";
                if (isUpperString(filename[0])) {
                    filename[0] += sa.getName().toUpperCase();
                } else {
                    filename[0] += sa.getName().toLowerCase();
                }
            }
        }
    }

    protected void addExtensionByFileAttr(int fileType, int mask, String filename, int external, boolean dupli) {
        Path path = Paths.get(filename);
        String ext = getFileExtension(path.getFileName().toString());
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext, fileType, mask);
        if (sa == null) {
            sa = basic.diskBasicParam.getAttributesByExtension().find(fileType, mask, external);
        }
        if (sa == null) {
            sa = basic.diskBasicParam.getAttributesByExtension().findType(fileType, mask);
        }
        if (sa != null) {
            if (dupli || !ext.equalsIgnoreCase(sa.getName())) {
                filename += ".";
                if (isUpperString(filename)) {
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

    protected boolean getFileAttrName(int pos, Map<String, Object> list, String[] attr, int defaultPos) {
        boolean match = true;
        if (pos >= 0) {
            attr[0] = Utils.keyAt(list, pos);
        } else {
            MyAttribute sa = basic.diskBasicParam.getSpecialAttributes().findValue(-pos);
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

    protected boolean trimExtensionByExtensionAttr(String[] filename) {
        String ext = getFileExtension(filename[0]);
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext);
        if (sa != null) {
            filename[0] = removeExtension(filename[0]);
        }
        return sa != null;
    }

    protected boolean trimLastExtensionByExtensionAttr(String filename) {
        String ext = getFileExtension(filename);
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext);
        if (sa != null) {
            String name = removeExtension(filename);
            String subext = getFileExtension(name);
            if (!subext.isEmpty()) {
                filename = name;
            }
        }
        return sa != null;
    }

    protected boolean trimLastExtensionByExtensionAttr(String filename, Map<String, Object> list, int listFirst, int listLast, String[] outfile, int[] attr, int[] pos) {
        boolean match = false;
        int t1 = 0;
        int p1 = -1;
        String ext = getFileExtension(filename).toUpperCase();
        int idx = indexOf(list, ext);
        if (idx >= listFirst && idx <= listLast) {
            match = true;
            t1 = (int) list.get(idx);
            p1 = idx;
        } else {
            MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext);
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

    protected boolean isContainAttrByExtension(String filename, Map<String, Object> list, int listFirst, int listLast, String[] outfile, int[] attr, int[] pos) {
        boolean match = false;
        int t1 = 0;
        int p1 = -1;
        String ext = getFileExtension(filename).toUpperCase();
        int idx = indexOf(list, ext);
        if (idx >= listFirst && idx <= listLast) {
            match = true;
            t1 = (int) Utils.valueAt(list, idx);
            p1 = idx;
        } else {
            MyAttribute sa = basic.diskBasicParam.getSpecialAttributes().findUpperCase(ext);
            if (sa != null) {
                match = true;
                t1 = sa.getValue();
            } else {
                sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext);
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

    public static void createChoiceForAttrDialog(DiskBasic basic, Map<String, Object> list, int endPos, List<String> types, int fileType) {
        boolean matchValue = false;
        for (Map.Entry<String, Object> e : list.entrySet()) {
            types.add(e.getKey());
            if (fileType >= 0 && (int) e.getValue() == fileType) {
                matchValue = true;
            }
        }
        MyAttributes attrs = basic.diskBasicParam.getSpecialAttributes();
        for (int i = 0; i < attrs.size(); i++) {
            MyAttribute attr = attrs.get(i);
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

    public static int selectChoiceForAttrDialog(DiskBasic basic, int selPos, int endPos, int unknownPos) {
        if (selPos < 0) {
            MyAttributes attrs = basic.diskBasicParam.getSpecialAttributes();
            int nType = attrs.getIndexByValue(-selPos);
            if (nType >= 0) {
                selPos = nType + endPos;
            }
        }
        if (selPos < 0) {
            selPos = unknownPos;
        }
        return selPos;
    }

    public static int calcSpecialOriginalTypeFromPos(DiskBasic basic, int pos, int endPos) {
        int val = -1;
        if (pos >= endPos) {
            pos -= endPos;
            MyAttributes attrs = basic.diskBasicParam.getSpecialAttributes();
            int count = attrs.size();
            if (pos < count) {
                val = attrs.getValueByIndex(pos);
            }
        }
        return val;
    }

    public static int calcSpecialFileTypeFromPos(DiskBasic basic, int pos, int endPos) {
        int t = 0;
        if (pos >= endPos) {
            pos -= endPos;
            MyAttributes attrs = basic.diskBasicParam.getSpecialAttributes();
            int count = attrs.size();
            if (pos < count) {
                t = attrs.getTypeByIndex(pos);
            }
        }
        return t;
    }

    public double normalCodesInFileName() {
        int count = 0;
        boolean invert = basic.diskBasicParam.isDataInverted();
        int[] l = new int[1];
        byte[] n = getFileNamePos(0, l);
        for (int i = 0; i < l[0]; i++) {
            byte c = n[i];
            if (invert) {
                c = (byte)(~c);
            }
            if (c >= 0x20) {
                count++;
            }
        }
        return l[0] > 0 ? (double)count / (double)l[0] : 0.0;
    }

    public boolean isFileNameEditable() {
        return true;
    }

    public void setFileNameStr(String filename) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nlen = new int[]{name.length};
        int[] elen = new int[]{ext.length};
        toNativeFileName(filename, name, nlen, ext, elen);
        setNativeFileName(name, name.length, nlen[0], ext, ext.length, elen[0]);
    }

    public void setFileNamePlain(String filename) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nlen = new int[]{name.length};
        int[] elen = new int[]{0};
        toNativeFileName(filename, name, nlen, null, elen);
        setNativeFileName(name, name.length, nlen[0], ext, ext.length, 0);
    }

    public void setFileExtPlain(String fileext) {
        byte[] ext = new byte[256];
        int[] nlen = new int[]{0};
        int[] elen = new int[]{ext.length};
        toNativeFileName(fileext, ext, elen, null, nlen);
        setNativeFileName(null, 0, 0, ext, ext.length, elen[0]);
    }

    public void setNativeFileName(byte[] name, int nsize, int nlen, byte[] ext, int esize, int elen) {
        boolean invert = basic.diskBasicParam.isDataInverted();
        char space = (char)basic.diskBasicParam.getDirSpaceCode();
        char term = (char)basic.diskBasicParam.getDirTerminateCode();
        if (name != null) {
            if (nsize > nlen) {
                name[nlen] = (byte)term;
                nlen++;
            }
            if (nsize > nlen) {
                Arrays.fill(name, nlen, nsize, (byte)space);
            }
            if (invert) {
                invertBytes(name, nsize);
            }
            setNativeName(name, nsize, new int[] {nlen});
        }
        if (ext != null) {
            if (esize > elen) {
                ext[elen] = (byte)term;
                elen++;
            }
            if (esize > elen) {
                Arrays.fill(ext, elen, esize, (byte)space);
            }
            if (invert) {
                invertBytes(ext, esize);
            }
            setNativeExt(ext, esize, elen);
        }
    }

    public void copyFileName(DiskBasicDirItem<?> src) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nlen = new int[]{name.length};
        int[] elen = new int[1];
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        elen[0] = extLen[0];
        src.getNativeFileName(name, nlen, ext, elen);
        setNativeFileName(name, name.length, nlen[0], ext, ext.length, elen[0]);
    }

    public String getFileNameStr() {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nl = new int[]{name.length};
        int[] el = new int[]{ext.length};
        getNativeFileName(name, nl, ext, el);
        String dst = "";
        convCharsToString(name, nl[0]);
        if (el[0] > 0) {
            dst += (char)basic.diskBasicParam.getExtensionPreCode();
            convCharsToString(ext, el[0]);
        }
        return dst;
    }

    public String getFileNameStrForExport() {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nl = new int[]{name.length};
        int[] el = new int[]{ext.length};
        getNativeFileName(name, nl, ext, el);
        String dst = "";
        convCharsToString(name, nl[0]);
        if (el[0] > 0) {
            dst += ".";
            convCharsToString(ext, el[0]);
        }
        return dst;
    }

    public void getFileName(byte[] filename, int length) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nl = new int[]{name.length};
        int[] el = new int[]{ext.length};
        getNativeFileName(name, nl, ext, el);
        System.arraycopy(name, 0, filename, 0, Math.min(length, nl[0]));
        if (el[0] > 0 && (nl[0] + el[0] + 1) < length) {
            filename[nl[0]] = basic.diskBasicParam.getExtensionPreCode();
            nl[0]++;
            System.arraycopy(ext, 0, filename, nl[0], el[0]);
        }
    }

    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = new int[1];
        int[] l = new int[1];
        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            System.arraycopy(n, 0, filename, 0, s[0]);
        }
        length[0] = l[0];
    }

    protected void getNativeExt(byte[] fileext, int size, int[] length) {
        byte[] e = null;
        int[] l = new int[1];
        e = getFileExtPos(l);
        if (e != null && l[0] > 0) {
            System.arraycopy(e, 0, fileext, 0, l[0]);
        }
        length[0] = l[0];
    }

    public void getNativeFileName(byte[] name, int[] nlen, byte[] ext, int[] elen) {
        int[] nl = new int[1];
        int[] el = new int[1];
        boolean invert = basic.diskBasicParam.isDataInverted();
        char trimm = (char)basic.diskBasicParam.getDirTrimmingCode();
        char space = (char)basic.diskBasicParam.getDirSpaceCode();
        char term = (char)basic.diskBasicParam.getDirTerminateCode();
        if (name != null && nlen[0] > 0) {
            Arrays.fill(name, 0, nlen[0], (byte)0);
            getNativeName(name, nlen[0], nl);
            if (invert) {
                invertBytes(name, nl[0]);
            }
            nl[0] = rtrim(name, nl[0], trimm);
            nl[0] = rtrim(name, nl[0], space);
            nl[0] = rtrim(name, nl[0], term);
            if (isUsed()) nlen[0] = strShrink(name, nl[0]);
            else nlen[0] = nl[0];
        }
        if (ext != null && elen[0] > 0) {
            Arrays.fill(ext, 0, elen[0], (byte)0);
            getNativeExt(ext, elen[0], el);
            if (invert) {
                invertBytes(ext, el[0]);
            }
            el[0] = rtrim(ext, el[0], trimm);
            el[0] = rtrim(ext, el[0], space);
            el[0] = rtrim(ext, el[0], term);
            if (isUsed()) elen[0] = strShrink(ext, el[0]);
            else elen[0] = el[0];
        }
    }

    public boolean isSameName(String name, boolean icase) {
        if (!isUsedAndVisible()) return false;
        byte[] sname = new byte[256];
        byte[] dname = new byte[256];
        int[] snlen =new int[]{sname.length};
        int[] dnlen = new int[]{dname.length};
        int[] selen = new int[]{0};
        int[] delen = new int[]{0};
        toNativeFileName(name, dname, dnlen, null, delen);
        getNativeFileName(sname, snlen, null, selen);
        if (icase) {
            toUpper(sname, snlen[0]);
            toUpper(dname, dnlen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sname, Math.max(snlen[0], dnlen[0])), Arrays.copyOf(dname, Math.max(snlen[0], dnlen[0])));
    }

    public boolean isSameFileName(DiskBasicFileName filename, boolean icase) {
        if (!isUsedAndVisible()) return false;
        byte[] sname = new byte[256];
        byte[] sext = new byte[256];
        byte[] dname = new byte[256];
        byte[] dext = new byte[256];
        int[] snlen = new int[]{sname.length};
        int[] selen = new int[]{sext.length};
        int[] dnlen = new int[]{dname.length};
        int[] delen = new int[]{dext.length};
        toNativeFileName(filename.getName(), dname, dnlen, dext, delen);
        getNativeFileName(sname, snlen, sext, selen);
        if (icase) {
            toUpper(sname, snlen[0]);
            toUpper(sext, selen[0]);
            toUpper(dname, dnlen[0]);
            toUpper(dext, delen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sname, Math.max(snlen[0], dnlen[0])), Arrays.copyOf(dname, Math.max(snlen[0], dnlen[0])))
                && ((delen[0] == 0 && selen[0] == 0) || Arrays.equals(Arrays.copyOf(sext, Math.max(selen[0], delen[0])), Arrays.copyOf(dext, Math.max(selen[0], delen[0]))))
                && (getOptionalName() == filename.getOptional());
    }

    public boolean isSameFileName(DiskBasicDirItem<T> src, boolean icase) {
        if (!isUsedAndVisible()) return false;
        byte[] sname = new byte[256];
        byte[] sext = new byte[256];
        byte[] dname = new byte[256];
        byte[] dext = new byte[256];
        int[] snlen = new int[]{sname.length};
        int[] selen = new int[]{sext.length};
        int[] dnlen = new int[]{dname.length};
        int[] delen = new int[]{dext.length};
        src.getNativeFileName(dname, dnlen, dext, delen);
        getNativeFileName(sname, snlen, sext, selen);
        if (icase) {
            toUpper(sname, snlen[0]);
            toUpper(sext, selen[0]);
            toUpper(dname, dnlen[0]);
            toUpper(dext, delen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sname, Math.max(snlen[0], dnlen[0])), Arrays.copyOf(dname, Math.max(snlen[0], dnlen[0])))
                && ((delen[0] == 0 && selen[0] == 0) || Arrays.equals(Arrays.copyOf(sext, Math.max(selen[0], delen[0])), Arrays.copyOf(dext, Math.max(selen[0], delen[0]))))
                && (src.getOptionalName() == getOptionalName());
    }

    public void toUpper(byte[] str, int size) {
        for (int i = 0; i < size; i++) {
            if (str[i] >= 'a' && str[i] <= 'z') {
                str[i] = (byte)(str[i] - 32);
            }
        }
    }

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

    public static void memoryCopy(byte[] src, int ssize, int slen, byte[] dst, int dsize, int[] dlen) {
        if (dst == null || dsize == 0) {
            dlen[0] = 0;
            return;
        }
        if (dsize > slen) Arrays.fill(dst, slen, dsize, (byte)0);
        if (slen > 0) System.arraycopy(src, 0, dst, 0, slen);
        dlen[0] = slen;
    }

    public static void splitFileName(byte[] src, int ssize, int slen, byte[] dname, int dnsize, int[] dnlen, byte[] dext, int desize, int[] delen, byte spchr) {
        int pos = -1;
        for (int i = slen - 1; i >= 0; i--) {
            if (src[i] == spchr) {
                pos = i;
                break;
            }
        }
        if (desize > 0 && pos >= 0) {
            memoryCopy(src, ssize, pos, dname, dnsize, dnlen);
            pos++;
            memoryCopy(Arrays.copyOfRange(src, pos, src.length), ssize - pos, slen - pos, dext, desize, delen);
        } else {
            memoryCopy(src, ssize, slen, dname, dnsize, dnlen);
            memoryCopy(null, 0, 0, dext, desize, delen);
        }
    }

    public void setFileAttr(DiskBasicFileType fileType) {
    }

    public void setFileAttr(DiskBasicFormatType formatType, int fileType, int originalType0) {
        setFileAttr(formatType, fileType, originalType0, 0, 0);
    }

    public void setFileAttr(DiskBasicFormatType formatType, int fileType, int originalType0, int originalType1) {
        setFileAttr(formatType, fileType, originalType0, originalType1, 0);
    }

    public void setFileAttr(DiskBasicFormatType formatType, int fileType, int originalType0, int originalType1, int originalType2) {
        setFileAttr(new DiskBasicFileType(formatType, fileType, originalType0, originalType1, originalType2));
    }

    public DiskBasicFileType getFileAttr() {
        return new DiskBasicFileType();
    }

    public String getFileAttrStr() {
        return "";
    }

    public void setExternalAttr(int val) {
        externalAttr = val;
    }

    public int getExternalAttr() {
        return externalAttr;
    }

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

    public boolean isDirectory() {
        return getFileAttr().isDirectory();
    }

    public void setFileSize(int val) {
        groups.setSize(val);
    }

    public int getFileSize() {
        return groups.getSize();
    }

    public void setDirectorySize(int val) {
        setFileSize(val);
    }

    public void getUnitGroups(int fileunitNum, DiskBasicGroups groupItems) throws IOException {
    }

    public void getAllGroups(DiskBasicGroups groupItems) throws IOException {
        groupItems.empty();
        getUnitGroups(0, groupItems);
    }

    public void setGroupSize(int val) {
        groups.setNums(val);
    }

    public int getGroupSize() {
        return groups.getNums();
    }

    public void setStartGroup(int fileunitNum, int val) {
        setStartGroup(fileunitNum, val, 0);
    }

    public void setStartGroup(int fileunitNum, int val, int size) {
    }

    public int getStartGroup(int fileunitNum) {
        return 0;
    }

    public void setExtraGroup(int val) {
    }

    public int getExtraGroup() {
        return 0xffff_ffff;
    }

    public void setExtraGroups(DiskBasicGroups grps) {
    }

    public void getExtraGroups(List<Integer> arr) {
    }

    public void getExtraGroups(DiskBasicGroups grps) {
    }

    public void setNextGroup(int val) {
    }

    public int getNextGroup() {
        return 0xffff_ffff;
    }

    public void setLastGroup(int val) {
    }

    public int getLastGroup() {
        return 0xffff_ffff;
    }

    public void setParentGroup(int val) {
    }

    public int getParentGroup() {
        return 0xffff_ffff;
    }

    public int getGroupCount() {
        return groups.count();
    }

    public DiskBasicGroups getGroups() {
        return groups;
    }

    public void setGroups(DiskBasicGroups vals) {
        groups = vals;
    }

    public DiskBasicGroupItem getGroup(int idx) {
        return groups.itemPtr(idx);
    }

    public void clearChainSector(DiskBasicDirItem<T> pitem /* = null */) {
    }

    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<T> pitem /* = null */) throws IOException {
    }

    public void setChainSector(DiskImageSector sector, int num, byte[] data, DiskBasicDirItem<T> pitem /* = null */) throws IOException {
    }

    public void setChainSector(int num, int pos, byte[] data, DiskBasicDirItem<T> pitem /* = null */) {
    }

    public void addChainGroupNumber(int idx, int val) {
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

    public LocalDate getFileCreateDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    public LocalTime getFileCreateTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    public void getFileCreateDateTime(LocalDateTime tm) {
        getFileCreateDate(tm);
        getFileCreateTime(tm);
    }

    public LocalDateTime getFileCreateDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDateTime(tm);
        return tm;
    }

    public String getFileCreateDateStr() {
        return "";
    }

    public String getFileCreateTimeStr() {
        return "";
    }

    public String getFileCreateDateTimeStr() {
        String str = getFileCreateDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileCreateTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    public LocalDate getFileModifyDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    public LocalTime getFileModifyTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    public void getFileModifyDateTime(LocalDateTime tm) {
        getFileModifyDate(tm);
        getFileModifyTime(tm);
    }

    public LocalDateTime getFileModifyDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileModifyDateTime(tm);
        return tm;
    }

    public String getFileModifyDateStr() {
        return "";
    }

    public String getFileModifyTimeStr() {
        return "";
    }

    public String getFileModifyDateTimeStr() {
        String str = getFileModifyDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileModifyTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    public LocalDate getFileAccessDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    public LocalTime getFileAccessTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    public void getFileAccessDateTime(LocalDateTime tm) {
        getFileAccessDate(tm);
        getFileAccessTime(tm);
    }

    public LocalDateTime getFileAccessDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileAccessDateTime(tm);
        return tm;
    }

    public String getFileAccessDateStr() {
        return "";
    }

    public String getFileAccessTimeStr() {
        return "";
    }

    public String getFileAccessDateTimeStr() {
        String str = getFileAccessDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileAccessTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    public void setFileCreateDate(LocalDateTime tm) {
    }

    public void setFileCreateTime(LocalDateTime tm) {
    }

    public void setFileCreateDateTime(LocalDateTime tm) {
        setFileCreateDate(tm);
        setFileCreateTime(tm);
    }

    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

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

    public void setFileAccessDateTime(LocalDateTime tm) {
        setFileAccessDate(tm);
        setFileAccessTime(tm);
    }

    public String getFileAccessDateTimeTitle() {
        return "Accessed Date";
    }

    public int getFileDateTimeOrder(int idx) {
        return idx;
    }

    public String getFileDateTimeStr() {
        return getFileCreateDateTimeStr();
    }

    public void writeFileDateTime(String path) {
        if (!(hasCreateDateTime() || hasModifyDateTime() || hasAccessDateTime())) {
            return;
        }
        if (!Files.exists(Paths.get(path))) {
            return;
        }
        // Implementation depends on file system capabilities
    }

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

    public boolean isUsed() {
        return (flags & USED_ITEM) != 0;
    }

    public void used(boolean val) {
        flags = val ? (flags | USED_ITEM) : (flags & ~USED_ITEM);
    }

    public boolean isVisible() {
        return (flags & VISIBLE_LIST) != 0;
    }

    public void visible(boolean val) {
        flags = val ? (flags | VISIBLE_LIST) : (flags & ~VISIBLE_LIST);
    }

    public boolean isUsedAndVisible() {
        return (flags & (USED_ITEM | VISIBLE_LIST)) == (USED_ITEM | VISIBLE_LIST);
    }

    public boolean isVisibleOnTree() {
        return (flags & VISIBLE_TREE) != 0;
    }

    public void visibleOnTree(boolean val) {
        flags = val ? (flags | VISIBLE_TREE) : (flags & ~VISIBLE_TREE);
    }

    public void copyItem(DiskBasicDirItem<T> src) {
        copyData(src.getData());
        groups = src.groups;
        flags = src.flags;
        externalAttr = src.externalAttr;
    }

    public abstract int getDataSize();

    public abstract T getData();

    public abstract boolean copyData(T val);

    public abstract void clearData();

    public void initialData() {
        clearData();
    }

    public boolean needCheckEofCode() {
        return false;
    }

    public byte getEofCode() {
        return basic.diskBasicParam.getTextTerminateCode();
    }

    public boolean needChainInData() {
        return false;
    }

    public boolean preExportDataFile(String[] filename) {
        addExtensionByFileAttr(getFileAttr().getType(), FILE_TYPE_EXTENSION_MASK, filename, false);
        return true;
    }

    public boolean preImportDataFile(String[] filename) {
        trimLastExtensionByExtensionAttr(filename[0]);
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    public int getFileUnitSize(int fileunitNum, InputStream istream, int fileOffset) throws IOException {
        if (fileunitNum == 0) {
            try {
                return istream.available();
            } catch (IOException e) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public boolean isValidFileUnit(int fileunitNum) {
        return fileunitNum == 0;
    }

    public int convFileTypeFromFileName(String filename) {
        return 0;
    }

    public int convOriginalTypeFromFileName(String filename) {
        return 0;
    }

    public int convOptionalNameFromFileName(String filename) {
        return 0;
    }

    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        return true;
    }

    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
    }

    public int convStringToChars(String src, byte[] dst, int len) {
        System.arraycopy(src.getBytes(), 0, dst, 0, len);
        return len;
    }

    public String convCharsToString(byte[] src, int len) {
        return new String(src, 0, len);
    }

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
            time1.setTextContent(Utils.formatHMSStr(ctm));
            root.appendChild(time1);
            Element date1 = doc.createElement("CreateDate");
            date1.setTextContent(Utils.formatYMDStr(ctm));
            root.appendChild(date1);

            LocalDateTime mtm = LocalDateTime.now();
            getFileModifyDateTime(mtm);
            time1 = doc.createElement("ModifyTime");
            time1.setTextContent(Utils.formatHMSStr(mtm));
            root.appendChild(time1);
            date1 = doc.createElement("ModifyDate");
            date1.setTextContent(Utils.formatYMDStr(mtm));
            root.appendChild(date1);

            LocalDateTime atm = LocalDateTime.now();
            getFileAccessDateTime(atm);
            time1 = doc.createElement("AccessTime");
            time1.setTextContent(Utils.formatHMSStr(atm));
            root.appendChild(time1);
            date1 = doc.createElement("AccessDate");
            date1.setTextContent(Utils.formatYMDStr(atm));
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
            int[] fnl = new int[]{fname.length};
            int[] fel = new int[]{fext.length};
            getNativeFileName(fname, fnl, fext, fel);

            Element ext1 = doc.createElement("Ext");
            ext1.setTextContent(encodeEscape(fext, fel[0]));
            root.appendChild(ext1);

            Element name1 = doc.createElement("Name");
            name1.setTextContent(encodeEscape(fname, fnl[0]));
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
            return false;
        }
    }

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
            LocalDateTime tmCtmp = LocalDateTime.now();
            LocalDateTime tmMtmp = LocalDateTime.now();
            LocalDateTime tmAtmp = LocalDateTime.now();

            byte[] fname = new byte[256];
            byte[] fext = new byte[256];
            int fnl = fname.length;
            int fel = fext.length;

            org.w3c.dom.NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Node node = nodes.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String name = node.getNodeName();
                    String content = node.getTextContent();

                    if (name.equals("Format")) {
                        formatType = Integer.parseInt(content);
                    } else if (name.equals("Name")) {
                        decodeEscape(content, fname, fnl);
                    } else if (name.equals("Ext")) {
                        decodeEscape(content, fext, fel);
                    } else if (name.equals("Type")) {
                        fileType = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("OriginalType0")) {
                        originalType0 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("OriginalType1")) {
                        originalType1 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("OriginalType2")) {
                        originalType2 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("Size")) {
                        fileSize = Integer.parseInt(content);
                    } else if (name.equals("StartAddress")) {
                        startAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("EndAddress")) {
                        endAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("ExecuteAddress")) {
                        execAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    } else if (name.equals("ExternalAttribute")) {
                        externalAttr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    }
                }
            }

            fnl = strLength(fname, fnl, 0);
            fel = strLength(fext, fel, 0);
            setNativeFileName(fname, fname.length, fnl, fext, fext.length, fel);
            setFileAttr(DiskBasicFormatType.valueOf(formatType), fileType, originalType0, originalType1, originalType2);
            setFileSize(fileSize);
            setStartAddress(startAddr);
            setEndAddress(endAddr);
            setExecuteAddress(execAddr);
            setExternalAttr(externalAttr);
            if (attr != null) {
                attr.setCreateDateTime(tmCtmp);
                attr.setModifyDateTime(tmMtmp);
                attr.setAccessDateTime(tmAtmp);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void setSector(DiskImageSector val) {
        sector = val;
    }

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

    public void calcFileUnitSize(int fileunitNum) throws IOException {
    }

    public void calcFileSize() throws IOException {
        groups.empty();
        calcFileUnitSize(0);
    }

    public int checkEofCode(InputStream istream, int fileSize) {
        try {
            istream.skip(fileSize - 1);
            byte[] c = new byte[1];
            istream.read(c);
            if (c[0] != getEofCode()) {
                fileSize++;
            }
            istream.reset();
            return fileSize;
        } catch (IOException e) {
            return fileSize;
        }
    }

    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        return occupiedSize;
    }

    public int recalcFileSizeOnSave(InputStream istream, int fileSize) {
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

    private boolean isUpperString(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isLetter(c) && Character.isLowerCase(c)) {
                return false;
            }
        }
        return true;
    }

    private int indexOf(Map<String, Object> list, String ext) {
        int i = 0;
        for (Map.Entry<String, Object> e : list.entrySet()) {
            if (e.getKey().equalsIgnoreCase(ext)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private int rtrim(byte[] arr, int len, char ch) {
        while (len > 0 && arr[len - 1] == (byte)ch) {
            len--;
        }
        return len;
    }

    private int strShrink(byte[] arr, int len) {
        int newLen = 0;
        for (int i = 0; i < len; i++) {
            if (arr[i] != 0) {
                newLen = i + 1;
            }
        }
        return newLen;
    }

    private int strLength(byte[] arr, int maxLen, int val) {
        int len = 0;
        for (int i = 0; i < maxLen; i++) {
            if (arr[i] == (byte)val) {
                break;
            }
            len++;
        }
        return len;
    }

    void invertBytes(byte[] arr, int len) {
        for (int i = 0; i < len; i++) {
            arr[i] = (byte)(~arr[i]);
        }
    }

    private String encodeEscape(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xFF;
            if (b < 32 || b > 126) {
                sb.append(String.format("\\x%02X", b));
            } else {
                sb.append((char)b);
            }
        }
        return sb.toString();
    }

    private void decodeEscape(String str, byte[] dst, int maxLen) {
        int j = 0;
        for (int i = 0; i < str.length() && j < maxLen; i++) {
            if (str.charAt(i) == '\\' && i + 3 < str.length() && str.charAt(i + 1) == 'x') {
                String hex = str.substring(i + 2, i + 4);
                dst[j++] = (byte)Integer.parseInt(hex, 16);
                i += 3;
            } else {
                dst[j++] = (byte)str.charAt(i);
            }
        }
    }

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

       public void setFileAttr(DiskBasicFormatType nFormat, int nType, int nOrigin0 /* = 0 */) {
           setFileAttr(nFormat, nType, nOrigin0, 0, 0);
       }

       public void setFileAttr(DiskBasicFormatType nFormat, int nType, int nOrigin0, int nOrigin1, int nOrigin2 /* = 0 */) {
            type.setFormat(nFormat);
            type.setType(nType);
            type.setOrigin(0, nOrigin0);
            type.setOrigin(1, nOrigin1);
            type.setOrigin(2, nOrigin2);
        }

        public void setFileType(int nType) {
            type.setType(nType);
        }

        public void setFileOriginAttr(int idx, int nOrigin) {
            type.setOrigin(idx, nOrigin);
        }

        public void setFileOriginAttr(int nOrigin) {
            type.setOrigin(nOrigin);
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

        public int getFileType() {
            return type.getType();
        }

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

    public static class DirItemSectorBoundary {
        private static class SectorInfo {
            byte[] data;
            int size;
            int pos;

            SectorInfo() {
                data = null;
                size = 0;
                pos = 0xFFFF;
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

        public void clear() {
            for (int i = 0; i < 2; i++) {
                s[i].data = null;
                s[i].size = 0;
                s[i].pos = 0xFFFF;
            }
        }

        public boolean set(DiskBasic basic, DiskImageSector sector, int position, Object itemData, int itemSize, SectorParam next) {
            if (sector == null) return false;

            int spos = position;
            int ssize = sector.getSectorSize();
            byte[] sptr = (byte[])itemData;
            if (itemData != null) {
                s[0].data = sptr;
                s[0].size = itemSize;
                s[0].pos = 0;
            }
            if (spos + itemSize >= ssize) {
                s[1].pos = ssize - spos;
                s[1].size = s[0].size - s[1].pos;
                s[0].size = s[1].pos;

                if (next != null) {
                    DiskBasicType type = basic.getType();
                    int nsectorPos = type.getSectorPosFromNum(next.getTrackNumber(), next.getSideNumber(), next.getSectorNumber());
                    int[] side = {next.getSideNumber()}, sec = {next.getSectorNumber()};
                    DiskImageSector nsector = basic.getManagedSector(next.getTrackNumber(), side, sec);
                    if (nsector != null) {
                        sptr = nsector.getSectorBuffer();
                        ssize = nsector.getSectorSize();
                        spos = basic.diskBasicParam.getDirStartPosOnSector();
                        spos += (nsectorPos % basic.diskBasicParam.getSectorsPerGroup()) == 0 ? basic.diskBasicParam.getDirStartPosOnGroup() : 0;
                        s[1].data = Arrays.copyOfRange(sptr, spos - s[1].pos, sptr.length);
                    }
                }
                return true;
            }
            return false;
        }

        public void copyTo(Object dstItem) {
            byte[] dst = (byte[])dstItem;
            for (int i = 0; i < 2; i++) {
                byte[] src = s[i].data;
                if (dst != null && src != null) {
                    System.arraycopy(src, s[i].pos, dst, s[i].pos, s[i].size);
                }
            }
        }

        public void copyFrom(Object srcItem) {
            byte[] src = (byte[])srcItem;
            for (int i = 0; i < 2; i++) {
                byte[] dst = s[i].data;
                if (dst != null && src != null) {
                    System.arraycopy(src, s[i].pos, dst, s[i].pos, s[i].size);
                }
            }
        }
    }
}
