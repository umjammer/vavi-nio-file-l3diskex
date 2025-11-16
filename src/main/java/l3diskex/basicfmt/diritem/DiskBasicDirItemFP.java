///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ResourceBundle;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFP.DirectoryFp;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_FP;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;


/** ディレクトリ１アイテム Casio FP-1100 C82-BASIC */
public class DiskBasicDirItemFP extends DiskBasicDirItemFAT8<DirectoryFp> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ C82-BASIC (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryFp implements Directory {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[8];
        @Element(sequence = 3)
        public byte[] ext = new byte[3];
        @Element(sequence = 4)
        public byte term;
        @Element(sequence = 5)
        public byte[] unknown = new byte[7];
        @Element(sequence = 6)
        public short loadAddress;
        @Element(sequence = 7)
        public short endAddress;
        @Element(sequence = 8)
        public short execAddress;
        @Element(sequence = 9)
        public short fileSize;
        @Element(sequence = 10)
        public byte startGroup;
        @Element(sequence = 11)
        public byte attr;
        @Element(sequence = 12)
        public byte[] reserved = new byte[2];

        public static final int SIZE = 32;
    }

    // Casio FP-1100 C82-BASIC file types
    public static final int FILETYPE_FP_BASIC = 0x10;	// BASIC
    public static final int FILETYPE_FP_DATA = 0x04;	// DATA
    public static final int FILETYPE_FP_MACHINE = 0x0d;	// MACHINE
    public static final int FILETYPE_FP_ASCII = 0x20;	// ascii
    public static final int FILETYPE_FP_RANDOM = 0x80;	// random

    // C82-BASIC attributes
    public static final String[] TYPE_NAME_FP_2 = {
            "Write Protected",
            "Read After Write",
    };
    public static final int TYPE_NAME_FP_READ_ONLY = 0;
    public static final int TYPE_NAME_FP_READ_WRITE = 1;
    public static final int TYPE_NAME_FP_UNKNOWN = 2;

    public static final int DATATYPE_MASK_FP_READ_ONLY = 0xf0;
    public static final int DATATYPE_MASK_FP_READ_WRITE = 0x0f;

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryFp> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(DiskBasicFormatType formatType) {
        return formatType == FORMAT_TYPE_FP;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryFp.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryFp.class, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryFp.class, data, dataP);

        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || this.data.data().name[0] == (byte) 0xff);

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param num       通し番号
     * @param groupItem トラック番号などのデータ
     * @param sector    セクタ
     * @param sectorPos    セクタ内のディレクトリエントリの位置
     * @param data      ディレクトリアイテム
     * @param next      [out] 次のセクタ
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryFp.class, data, dataPos);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = data.data().name.length;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

    @Override
    protected int getFileType1() {
        return data.data().type & 0xff;
    }

    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    @Override
    public int getFileType2() {
        return data.data().attr & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        data.data().attr = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.data.data().type != 0;
    }

    /** エントリデータの未使用部分を設定 */
    private void setTerminate(int val) {
        if (!data.isValid()) return;

        int c;
        c = (val != 0 ? 0xff : 0);
        data.data().term = (byte) (c & 0xff);

        c = ((val & FILETYPE_FP_BASIC) != 0 ? 0xff : 0);
        Arrays.fill(data.data().unknown, 0, data.data().unknown.length, (byte) c);
    }

    @Override
    public int getFileType1Pos() {
        int t1 = getFileType1();
        int val = TYPE_NAME_1_UNKNOWN;
        if ((t1 & FILETYPE_FP_MACHINE) == FILETYPE_FP_MACHINE) {
            val = TYPE_NAME_1_MACHINE;
        } else if ((t1 & FILETYPE_FP_BASIC) == FILETYPE_FP_BASIC) {
            val = TYPE_NAME_1_BASIC;
        } else if ((t1 & FILETYPE_FP_DATA) == FILETYPE_FP_DATA) {
            val = TYPE_NAME_1_DATA;
        }
        return val;
    }

    @Override
    public int getFileType2Pos() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_FP_RANDOM) != 0) {
            val = TYPE_NAME_2_RANDOM;
        } else if ((t1 & FILETYPE_FP_ASCII) != 0) {
            val = TYPE_NAME_2_ASCII;
        } else {
            val = TYPE_NAME_2_BINARY;
        }
        return val;
    }

    @Override
    public void takeAddressesInFile() {
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        if (((data.data().type & 0x02) != 0) || (data.data().term != 0 && data.data().term != 0xff)) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        clearData();
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int t2 = 0;
        if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            t1 = FILETYPE_FP_MACHINE;
        } else if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
            t1 = FILETYPE_FP_BASIC;
        } else if ((fType & FILE_TYPE_DATA_MASK.getValue()) != 0) {
            t1 = FILETYPE_FP_DATA;
        }
        if ((fType & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            t1 |= FILETYPE_FP_RANDOM;
        } else if ((fType & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            t1 |= FILETYPE_FP_ASCII;
        }

        if ((fType & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            t2 |= DATATYPE_MASK_FP_READ_ONLY;
        }
        if ((fType & FILE_TYPE_READWRITE_MASK.getValue()) != 0) {
            t2 |= DATATYPE_MASK_FP_READ_WRITE;
        }

        setFileType1(t1);
        setFileType2(t2);

        setTerminate(t1);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int t2 = getFileType2();
        int val = 0;
        if ((t1 & FILETYPE_FP_MACHINE) == FILETYPE_FP_MACHINE) {
            val = FILE_TYPE_MACHINE_MASK.getValue();
        } else if ((t1 & FILETYPE_FP_BASIC) == FILETYPE_FP_BASIC) {
            val = FILE_TYPE_BASIC_MASK.getValue();
        } else if ((t1 & FILETYPE_FP_DATA) == FILETYPE_FP_DATA) {
            val = FILE_TYPE_DATA_MASK.getValue();
        }

        if ((t1 & FILETYPE_FP_RANDOM) != 0) {
            val |= FILE_TYPE_RANDOM_MASK.getValue();
        } else if ((t1 & FILETYPE_FP_ASCII) != 0) {
            val |= FILE_TYPE_ASCII_MASK.getValue();
        } else {
            val |= FILE_TYPE_BINARY_MASK.getValue();
        }

        if ((t2 & DATATYPE_MASK_FP_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t2 & DATATYPE_MASK_FP_READ_WRITE) != 0) {
            val |= FILE_TYPE_READWRITE_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t2 << 8 | t1);
    }

    @Override
    public String getFileAttrStr() {
        int p1 = getFileType1Pos();
        String attr = rb.getString(TYPE_NAME_1[p1]);
        //
        int p2 = getFileType2Pos();
        attr += " - ";
        attr += TYPE_NAME_2[p2];
        //
        int t2 = getFileType2();
        if ((t2 & DATATYPE_MASK_FP_READ_ONLY) != 0) {
            attr += ", ";
            attr += TYPE_NAME_FP_2[TYPE_NAME_FP_READ_ONLY];
        }
        if ((t2 & DATATYPE_MASK_FP_READ_WRITE) != 0) {
            attr += ", ";
            attr += TYPE_NAME_FP_2[TYPE_NAME_FP_READ_WRITE];
        }
        return attr;
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int t1 = getFileType1();
        if (t1 == FILETYPE_FP_BASIC) {
            // BASIC - binary
            data.data().fileSize = (short) val;
            data.data().endAddress = (short) val;
        } else if (t1 == FILETYPE_FP_MACHINE) {
            // Machine
            data.data().fileSize = (short) val;
            int end_addr = data.data().loadAddress + val - 1;
            data.data().endAddress = (short) end_addr;
        }
        // アスキーファイルはファイルサイズをセットしない
    }

    @Override
    public int getFileSize() {
        int val = data.data().fileSize & 0xffff;
        if (val == 0) {
            val = groups.getSize();
        }
        return val;
    }

    @Override
    public int getStartAddress() {
        return data.data().loadAddress & 0xffff;
    }

    @Override
    public int getEndAddress() {
        return data.data().endAddress & 0xffff;
    }

    @Override
    public int getExecuteAddress() {
        return data.data().execAddress & 0xffff;
    }

    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = (short) val;
    }

    @Override
    public void setEndAddress(int val) {
        data.data().endAddress = (short) (val & 0xffff);
    }

    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = (short) (val & 0xffff);
    }

    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public boolean hasExecuteAddress() {
        return hasAddress();
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        // fp
        data.data().startGroup = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        // fp
        return data.data().startGroup & 0xff;
    }

    @Override
    public boolean needCheckEofCode() {
        // アスキー形式のときはEOFコードでサイズを計算
        return (getFileType1() & (FILETYPE_FP_RANDOM | FILETYPE_FP_ASCII)) == FILETYPE_FP_ASCII;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        // ファイルの最終が終端記号で終わっているかを調べる
        // ただし、ファイルサイズがクラスタサイズと合うなら終端記号は不要
        if ((fileSize % (basic.getSectorSize() * basic.getSectorsPerGroup())) != 0) {
            fileSize = checkEofCode(iStream, fileSize) - 1;
        }
        return fileSize;
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryFp getData() {
        return data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir());
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    //
    // ダイアログ用
    //

    @Override
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errInfo) {
        int ftype = attr.getFileType();
        if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            // バイナリ
            if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                // BASICならアドレスは固定
                attr.setStartAddress(0);
                attr.setEndAddress(getFileSize());
                attr.setExecuteAddress(0);
            }
        } else {
            // アスキー、ランダムアクセス
            // アドレスは固定
            attr.setStartAddress(0);
            attr.setEndAddress(0);
            attr.setExecuteAddress(0);
        }
        return true;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TYPE", data.data().type);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("TERM", data.data().term);
        vals.add("UNKNOWN", data.data().unknown, data.data().unknown.length);
        vals.add("LOAD_ADDR", data.data().loadAddress);
        vals.add("END_ADDR", data.data().endAddress);
        vals.add("EXEC_ADDR", data.data().execAddress);
        vals.add("FILE_SIZE", data.data().fileSize);
        vals.add("START_GROUP", data.data().startGroup);
        vals.add("ATTR", data.data().attr);
        vals.add("RESERVED", data.data().reserved, data.data().reserved.length);
    }
}
