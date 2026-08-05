///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.StringJoiner;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88.DirectoryN88;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ENCRYPTED_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeN88.FORMAT_TYPE_N88;
import static l3diskex.basicfmt.type.DiskBasicTypePA.FORMAT_TYPE_PA;


/**
 Directory 1 item N88-BASIC

 {@link #externalAttr} 1 when it is a random access file
 */
public class DiskBasicDirItemN88 extends DiskBasicDirItemFAT8<DirectoryN88> {

    /**
     * Directory entry n88 BASIC
     */
    @Serdes(bigEndian = false)
    public static class DirectoryN88 implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type; // byte
        @Element(sequence = 4)
        public byte startGroup; // byte
        @Element(sequence = 5)
        public byte[] reserved = new byte[5];

        public static final int SIZE = 16;

        @Override
        public String toString() {
            return new StringJoiner(", ", DirectoryN88.class.getSimpleName() + "[", "]")
                    .add("name=" + new String(name) + "." + new String(ext))
                    .add("type=" + type)
                    .add("startGroup=" + startGroup)
                    .add("reserved=" + Arrays.toString(reserved))
                    .toString();
        }
    }

    /** N88-BASIC attribute names */
    public static final String[] TYPE_NAME_N88_1 = {
            "Ascii",
            "Binary",
            "Machine",
            "Ascii(Random Access)",
    };

    // N88-BASIC

    public static final int TYPE_NAME_N88_ASCII = 0;
    public static final int TYPE_NAME_N88_BINARY = 1;
    public static final int TYPE_NAME_N88_MACHINE = 2;
    public static final int TYPE_NAME_N88_RANDOM = 3;

    public static final int FILETYPE_N88_ASCII = 0x00;
    public static final int FILETYPE_N88_BINARY = 0x80;
    public static final int FILETYPE_N88_MACHINE = 0x01;

    /** N88-BASIC attribute names 2 */
    public static final String[] TYPE_NAME_N88_2 = {
            "Write Protected",
            "Read After Write",
            "Encrypted",
    };

    public static final int TYPE_NAME_N88_READ_ONLY = 0;
    public static final int TYPE_NAME_N88_READ_WRITE = 1;
    public static final int TYPE_NAME_N88_ENCRYPTED = 2;

    public static final int DATATYPE_MASK_N88_READ_ONLY = 0x10;
    public static final int DATATYPE_MASK_N88_READ_WRITE = 0x40;
    public static final int DATATYPE_MASK_N88_ENCRYPTED = 0x20;

    /** Directory data */
    protected DiskBasicDirData<DirectoryN88> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_N88 ||
                formatType == FORMAT_TYPE_PA;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryN88.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        this.data.attach(DirectoryN88.class, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        // n88
        this.data.attach(DirectoryN88.class, data, dataPos);
//Debug.printStackTrace(new Exception());

        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || (this.data.data().name[0] == (byte) 0xff));
//Debug.println("1): " + isUsed() + "\n" + StringUtil.getDump(data.getRawData(), DirectoryN88.SIZE));

        // Calculate file size and number of groups
        calcFileSize();
    }

    /**
     * Set pointer to item
     *
     * @param num       Serial number
     * @param groupItem Data such as track number
     * @param sector    Sector
     * @param sectorPos Position of directory entry within sector
     * @param data      Directory item
     * @param next      [out] Next sector
     * @see DiskBasicType#checkDirectory
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryN88.class, data, dataPos);
//Debug.println("2)\n" + StringUtil.getDump(data.getRawData(), DirectoryN88.SIZE));
    }

    /** Returns position where file name is stored */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // N88
        if (num == 0) {
            size[0] = len[0] = data.data().name.length;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** Returns position where extension is stored */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

    @Override
    public int getFileType1() {
        return data.data().type;
    }

    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && data.data().name[0] != 0x00 && data.data().name[0] != (byte) 0xff);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        // There is an invalid value in the attributes (0x0c = 00001100b, i.e., bits 2 and 3)
        if ((getFileType1() & 0x0c) != 0) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry
        data.fill(basic.getDeleteCode(), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        // n88
        int t1 = convFileType1(fType);

        externalAttr = ((fType & FILE_TYPE_RANDOM_MASK.getValue()) != 0 ? 1 : 0);

        if ((fType & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            t1 |= DATATYPE_MASK_N88_READ_ONLY;
        }
        if ((fType & FILE_TYPE_ENCRYPTED_MASK.getValue()) != 0) {
            t1 |= DATATYPE_MASK_N88_ENCRYPTED;
        }
        if ((fType & FILE_TYPE_READWRITE_MASK.getValue()) != 0) {
            t1 |= DATATYPE_MASK_N88_READ_WRITE;
        }
        setFileType1(t1);
    }

    public int convFileType1(int fileType) {
        int t1 = 0;
        if ((fileType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            t1 = FILETYPE_N88_MACHINE;
        } else if ((fileType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            t1 = FILETYPE_N88_BINARY;
        } else {
            t1 = FILETYPE_N88_ASCII;
        }
        return t1;
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int type = 0;
        if ((t1 & FILETYPE_N88_MACHINE) != 0) {
            type = FILE_TYPE_MACHINE_MASK.getValue();     // machine
            type |= FILE_TYPE_BINARY_MASK.getValue();     // binary
        } else {
            type = FILE_TYPE_BASIC_MASK.getValue();       // basic
            if ((t1 & FILETYPE_N88_BINARY) != 0) {
                type |= FILE_TYPE_BINARY_MASK.getValue(); // binary
            } else {
                type |= FILE_TYPE_ASCII_MASK.getValue();  // ascii
            }
        }
        if ((t1 & DATATYPE_MASK_N88_READ_ONLY) != 0) {
            type |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t1 & DATATYPE_MASK_N88_ENCRYPTED) != 0) {
            type |= FILE_TYPE_ENCRYPTED_MASK.getValue();
        }
        if ((t1 & DATATYPE_MASK_N88_READ_WRITE) != 0) {
            type |= FILE_TYPE_READWRITE_MASK.getValue();
        }

        if (isValidDirectory()) { // TODO ad-hoc if this is a root directory set directory type bit
            type |= FILE_TYPE_DIRECTORY_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), type, t1);
    }

    @Override
    public String getFileAttrStr() {
        // n88
        String attr = TYPE_NAME_N88_1[convFileType1Pos(getFileType1())];
        //
        int t = getFileType1();
        if ((t & DATATYPE_MASK_N88_READ_ONLY) != 0) {
            attr += ", ";
            attr += TYPE_NAME_N88_2[TYPE_NAME_N88_READ_ONLY];
        }
        if ((t & DATATYPE_MASK_N88_READ_WRITE) != 0) {
            attr += ", ";
            attr += TYPE_NAME_N88_2[TYPE_NAME_N88_READ_WRITE];
        }
        if ((t & DATATYPE_MASK_N88_ENCRYPTED) != 0) {
            attr += ", ";
            attr += TYPE_NAME_N88_2[TYPE_NAME_N88_ENCRYPTED];
        }

        return attr;
    }

    @Override
    public void setFileSize(int val) {
        // Round file size to sector size boundary
        int sectorSize = basic.getSectorSize();
        groups.setSize((((val - 1) / sectorSize) + 1) * sectorSize);
    }

    // C++ method: TakeAddressesInFile
    @Override
    protected void takeAddressesInFile() {
        if (groups.size() == 0 || (getFileType1() & FILETYPE_N88_MACHINE) == 0) {
            startAddress = -1;
            endAddress = -1;
            execAddress = -1;
            return;
        }

        DiskBasicGroupItem item = groups.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        boolean isBigEndian = basic.isBigEndian();

        // Start address
        startAddress = sector.get16(0, isBigEndian);
        // End address
        endAddress = sector.get16(2, isBigEndian);
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        // n88
        data.data().startGroup = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        // n88
        return data.data().startGroup & 0xff;
    }

    @Override
    public boolean hasEndMark() {
        boolean val;
        val = ((data.data().name[0] & 0xff) == basic.getGroupUnusedCode());
        return val;
    }

    @Override
    public void setEndMark(DiskBasicDirItem nextItem) {
        if (nextItem == null) return;

        if (hasEndMark())
            ((DirectoryN88) nextItem.getData()).name[0] = (byte) basic.getGroupUnusedCode();
    }

    @Override
    public boolean needCheckEofCode() {
        // EOF code is needed when Asc format
        return (((getFileType1() & (FILETYPE_N88_MACHINE | FILETYPE_N88_BINARY)) == 0) && (externalAttr == 0));
    }

    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // Check if the end of the file ends with a termination symbol
            fileSize = checkEofCode(iStream, fileSize);
            // However, if the file size matches the sector size, the termination symbol is not required
            if ((fileSize % basic.getSectorSize()) == 1) {
                // The remaining 1 byte is only for the termination code, so it is unnecessary
                fileSize--;
            }
        }
        return fileSize;
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryN88 getData() {
        return data.data();
    }

    @Override
    public byte[] getRawData() {
        return data.getRawData();
    }

    @Override
    protected void flushData() throws IOException {
        data.flush();
    }

    @Override
    public boolean copyData(byte[] val) { // directory_t replaced by Object
        return data.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir(), getDataSize());

        data.data().type = 0;
    }

    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // Set attribute by extension
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            ftype = sa.getType();
        } else {
            ftype = FILE_TYPE_ASCII_MASK.getValue();
        }
        return ftype;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        // Set attribute by extension
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            t1 = convFileType1(sa.getType());
        } else {
            t1 = TYPE_NAME_N88_ASCII;
        }
        return t1;
    }

    // Returns position in list from attribute (for property dialog)
    public int convFileType1Pos(int t1) {
        int val = 0;
        if ((t1 & FILETYPE_N88_MACHINE) != 0) {
            val = TYPE_NAME_N88_MACHINE;
        } else {
            if ((t1 & FILETYPE_N88_BINARY) != 0) {
                val = TYPE_NAME_N88_BINARY;
            } else if (externalAttr != 0) {
                val = TYPE_NAME_N88_RANDOM;
            } else {
                val = TYPE_NAME_N88_ASCII;
            }
        }
        return val;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("TYPE", data.data().type);
        vals.add("START_GROUP", data.data().startGroup);
        vals.add("RESERVED", data.data().reserved, data.data().reserved.length);
    }
}
