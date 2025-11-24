///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.ResourceBundle;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.type.DiskBasicTypeMSDOS.FORMAT_TYPE_LOSA;


/**
 * ディレクトリ１アイテム L-os Angeles (MS-DOS compatible)
 *
 * @see "https://github.com/tablacus/LosAngeles"
 * @see "https://github.com/tablacus/LSX-Dodgers"
 * @see "https://note.com/medamap/n/n4146227b2f9e"
 */
public class DiskBasicDirItemLOSA extends DiskBasicDirItemMSDOS {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ L-os Angeles (MS-DOS compatible) (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryLosa implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 1)
        public byte[] ext = new byte[3];
        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 1)
        public byte[] startAddress = new byte[4];
        @Element(sequence = 1)
        public byte binaryType;
        @Element(sequence = 1)
        public byte[] execAddress = new byte[4];
        @Element(sequence = 1)
        public byte reserved;
        @Element(sequence = 1)
        public short wTime;
        @Element(sequence = 1)
        public short wDate;
        @Element(sequence = 1)
        public short startGroup;
        @Element(sequence = 1)
        public int fileSize;

        public static final int SIZE = 32;
    }

    public static final int FILE_TYPE_LOSA_BINARY = 0xa0;

    public static final String TYPE_NAME_LOSA_BINARY = "LA binary";

    //
    //
    //

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_LOSA;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);
    }

    @Override
    public void init(DiskBasic basic,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic,
                     int num,
                     DiskBasicGroupItem groupItem,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next,
                     boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);
    }

    /** File name / extension handling */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        return switch (num) {
            case 0 -> {
                size[0] = len[0] = data.data().losa().name.length;
                yield data.data().losa().name;
            }
            default -> {
                size[0] = len[0] = 0;
                yield null;
            }
        };
    }

    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().losa().ext.length;
        return data.data().losa().ext;
    }

    /** File type (attribute 2) */
    @Override
    public int getFileType2() {
        return data.data().losa().binaryType & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        data.data().losa().binaryType = (byte) (val & 0xff);
    }

    /** Generic attribute handling */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) {
            return;
        }

        // MS‑DOS
        setFileType1((fType & 0xFF00) >> 8);

        int t2 = fileType.getOrigin() >> 16;
        setFileType2(t2);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int t2 = getFileType2();
        return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int fType = getFileAttr().getType();
        int t2 = getFileAttr().getOrigin() >> 8;
        if (t2 == FILE_TYPE_LOSA_BINARY) {
            attr.append(rb.getString(TYPE_NAME_LOSA_BINARY));
        }
        getFileAttrStrSub(fType, attr);
        if (attr.isEmpty()) {
            attr.append("---");
        }
        return attr.toString();
    }

    // Address handling

    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public int getStartAddress() {
        String address = new String(data.data().losa().startAddress);
        int lVal = Integer.parseInt(address, 16);
        return lVal;
    }

    @Override
    public int getExecuteAddress() {
        String address = new String(data.data().losa().execAddress);
        int lVal = Integer.parseInt(address, 16);
        return lVal;
    }

    @Override
    public void setStartAddress(int val) {
        String address = String.format("%04X", val);
        byte[] b = address.getBytes();
        System.arraycopy(b, 0, data.data().losa().startAddress, 0, 4);
    }

    @Override
    public void setExecuteAddress(int val) {
        String address = String.format("%04X", val);
        byte[] b = address.getBytes();
        System.arraycopy(b, 0, data.data().losa().execAddress, 0, 4);
    }

    @Override
    public int getDataSize() {
        return DirectoryLosa.SIZE;
    }

    //
    // ダイアログ用
    //

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().losa().name, data.data().losa().name.length);
        vals.add("EXT", data.data().losa().ext, data.data().losa().ext.length);
        vals.add("TYPE", data.data().losa().type);
        vals.add("START_ADDR", data.data().losa().startAddress, data.data().losa().startAddress.length);
        vals.add("BINARY_TYPE", data.data().losa().binaryType);
        vals.add("EXEC_ADDR", data.data().losa().execAddress, data.data().losa().execAddress.length);
        vals.add("RESERVED", data.data().losa().reserved);
        vals.add("WTIME", data.data().losa().wTime);
        vals.add("WDATE", data.data().losa().wDate);
        vals.add("START_GROUP", data.data().losa().startGroup);
        vals.add("FILE_SIZE", data.data().losa().fileSize);
    }
}
