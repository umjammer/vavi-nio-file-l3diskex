/*
 * @author Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ResourceBundle;

import l3diskex.Common;
import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.DirectoryCpm;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeCPM.FORMAT_TYPE_CPM;
import static l3diskex.basicfmt.type.DiskBasicTypeSMC.FORMAT_TYPE_SMC;


/**
 * ディレクトリ１アイテム CP/M
 *
 * {@link #externalAttr} バイナリ属性の時: FILE_TYPE_BINARY_MASK, アスキー属性の時: 0
 */
public class DiskBasicDirItemCPM extends DiskBasicDirItem<DirectoryCpm> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ CP/M (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryCpm implements Directory, Cloneable {

        @Element(sequence = 1)
        public byte type; // byte user id
        @Element(sequence = 2)
        public byte[] name = new byte[8];
        @Element(sequence = 3)
        public byte[] ext = new byte[3];
        @Element(sequence = 4)
        public byte extentNum; // byte
        @Element(sequence = 5)
        public byte[] reserved = new byte[2];
        @Element(sequence = 6)
        public byte recordNum; // byte

        // union
        @Element(sequence = 7)
        public byte[] mapBytes = new byte[16]; // byte b[16]
        //public short[] mapWords = new short[8]; //

        @Override
        public DirectoryCpm clone() {
            return new DirectoryCpm();
        }

        public static final int SIZE = 32;
    }

    /** CP/M属性名 */
    public static final String[] typeNameCPM = {
            /*rb.getString(*/"Read Only"/*)*/,
            /*rb.getString(*/"System"/*)*/,
            /*rb.getString(*/"Archive"/*)*/,
    };

    // CP/M属性名
    static final int TYPE_NAME_CPM_READ_ONLY = 0;
    static final int TYPE_NAME_CPM_SYSTEM = 1;
    static final int TYPE_NAME_CPM_ARCHIVE = 2;

    public static final String[] typeNameCPM_2 = {
            /*rb.getString(*/"Binary"/*)*/,
            /*rb.getString(*/"Ascii"/*)*/,
    };

    static final int TYPE_NAME_CPM_BINARY = 0;
    static final int TYPE_NAME_CPM_ASCII = 1;

    public static final int SECTOR_UNIT_CPM = 128;

    //
    //
    //

    /** ディレクトリデータ */
    protected DiskBasicDirData<DirectoryCpm> data = new DiskBasicDirData<>();

    /** グループ番号の幅(1 = 8ビット, 2 = 16ビット) */
    protected int groupWidth;
    /** グループ番号のエントリ数(8 or 16) */
    protected int groupEntries;

    /** 次のエクステントがある場合 */
    protected DiskBasicDirItemCPM nextItem;

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_CPM ||
                formatType == FORMAT_TYPE_SMC;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryCpm.class);
        // グループ番号の幅
        groupWidth = basic.getGroupWidth();
        groupEntries = basic.getGroupsPerDirEntry() >= 8 ? basic.getGroupsPerDirEntry() : (16 / groupWidth);
        externalAttr = getFileTypeByExt(0, getFileExtPlainStr());

        nextItem = null;
    }

    @Override
    public void init(DiskBasic basic,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryCpm.class, data, dataP);
        // グループ番号の幅
        groupWidth = basic.getGroupWidth();
        groupEntries = basic.getGroupsPerDirEntry() >= 8 ? basic.getGroupsPerDirEntry() : (16 / groupWidth);
        externalAttr = getFileTypeByExt(0, getFileExtPlainStr());

        nextItem = null;
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        this.data.attach(DirectoryCpm.class, data, dataPos);
        // グループ番号の幅
        groupWidth = basic.getGroupWidth();
        groupEntries = basic.getGroupsPerDirEntry() >= 8 ? basic.getGroupsPerDirEntry() : (16 / groupWidth);
        externalAttr = getFileTypeByExt(0, getFileExtPlainStr());

        nextItem = null;

        used(checkUsed(unuse[0]));
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param num       通し番号
     * @param groupItem トラック番号などのデータ
     * @param sector    セクタ
     * @param sectorPos    セクタ内のディレクトリエントリの位置
     * @param data      ディレクトリアイテム
     * @param dataPos     ディレクトリアイテムのポインタ
     * @param next      次のセクタ
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryCpm.class, data, dataPos);
    }

    /**
     * ファイル名を格納する位置を返す
     */
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

    /**
     * 拡張子を格納する位置を返す
     */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

    /**
     * 属性１を返す (User ID)
     */
    @Override
    public int getFileType1() {
        return basic.invertUint8((byte) (data.data().type & 0xff));
    }

    /**
     * 属性２を返す (R/S/A + Binary/ASCII)
     */
    @Override
    public int getFileType2() {
        int val = 0;
        byte[] ext = new byte[data.data().ext.length + 1];
        basic.invertMemory(data.data().ext, data.data().ext.length, ext);

        val |= (ext[0] & 0x80) != 0 ? FILE_TYPE_READONLY_MASK.getValue() : 0; // read only
        val |= (ext[1] & 0x80) != 0 ? FILE_TYPE_SYSTEM_MASK.getValue() : 0;   // system
        val |= (ext[2] & 0x80) != 0 ? FILE_TYPE_ARCHIVE_MASK.getValue() : 0;  // archive

        String extstr = getFileExtPlainStr();

        val = getFileTypeByExt(val, extstr);

        val |= externalAttr;

        return val;
    }

    /**
     * 拡張子からアスキーorバイナリ属性を判断する
     */
    public int getFileTypeByExt(int val, String ext) {
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext, FILE_TYPE_BINARY_MASK.getValue(), 0x3f);
        if (sa != null) {
            val |= FILE_TYPE_BINARY_MASK.getValue();
        }
        return val;
    }

    /**
     * 属性１を設定 (User ID)
     */
    @Override
    protected void setFileType1(int val) {
        data.data().type = basic.invertUint8((byte) val);
    }

    /**
     * 属性２を設定 (R/S/A + Binary/ASCII)
     */
    @Override
    protected void setFileType2(int val) {
        if (basic.isDataInverted()) Common.invertMemory(data.getRawData(), data.data().ext.length); // invert

        data.data().ext[0] = (byte) ((data.data().ext[0] & 0x7f) | ((val & FILE_TYPE_READONLY_MASK.getValue()) != 0 ? 0x80 : 0));
        data.data().ext[1] = (byte) ((data.data().ext[1] & 0x7f) | ((val & FILE_TYPE_SYSTEM_MASK.getValue()) != 0 ? 0x80 : 0));
        data.data().ext[2] = (byte) ((data.data().ext[2] & 0x7f) | ((val & FILE_TYPE_ARCHIVE_MASK.getValue()) != 0 ? 0x80 : 0));
        externalAttr = val & FILE_TYPE_BINARY_MASK.getValue();

        if (basic.isDataInverted()) Common.invertMemory(data.getRawData(), data.data().ext.length); // invert
    }

    /**
     * ファイル名を得る
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        super.getNativeFileName(name, nLen, ext, eLen);

        // 拡張子部分のMSBは属性ビットなので除く
        for (int en = 0; en < eLen[0]; en++) {
            ext[en] &= 0x7f;
        }
    }

    /**
     * 拡張子を返す
     *
     * @return 拡張子
     */
    @Override
    public String getFileExtPlainStr() {
        if (!data.isValid()) return "";

        byte[] ext = new byte[data.data().ext.length];
        basic.invertMemory(data.data().ext, data.data().ext.length, ext);
        for (int i = 0; i < data.data().ext.length; i++) {
            ext[i] &= 0x7f;
        }
        return new String(ext);
    }

    /**
     * ファイル名を設定
     *
     * filename はデータビットが反転している場合あり
     * @param filename ファイル名
     * @param size     バッファサイズ
     * @param length   長さ
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        super.setNativeName(filename, size, length);

        // 複数ある時
        if (nextItem != null) {
            nextItem.setNativeName(filename, size, length);
        }
    }

    /**
     * 拡張子を設定
     *
     * fileext はデータビットが反転している場合あり
     * @param fileExt 拡張子
     * @param size    バッファサイズ
     * @param length  長さ
     */
    @Override
    protected void setNativeExt(byte[] fileExt, int size, int length) {
        byte[] e;
        int[] el = {0};
        e = getFileExtPos(el);

        if (el[0] > size) el[0] = size;

        for (int i = 0; i < el[0]; i++) {
            // MSBは属性ビットなのでのこす
            e[i] = (byte) ((e[i] & 0x80) | (fileExt[i] & 0x7f));
        }

        // 複数ある時
        if (nextItem != null) {
            nextItem.setNativeExt(fileExt, size, length);
        }
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return getFileType1() != 0xe5;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        setFileType1(basic.getDeleteCode());
        used(false);

        // 複数ある時
        if (nextItem != null) {
            nextItem.delete();
        }
        return true;
    }

    /**
     * ディレクトリアイテムのチェック
     *
     * @param last チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = false;
        // ユーザIDが0～15でファイル名がオール0ならダメ
        if (getFileType1() < 0x10) {
            byte[] name = new byte[data.data().name.length];
            basic.invertMemory(data.data().name, data.data().name.length, name);
            for (int n = 0; n < data.data().name.length; n++) {
                if (name[n] != 0) {
                    valid = true;
                    break;
                }
            }
            if (valid && !last[0]) {
                valid = checkData(data.getRawData(), getDataSize(), last);
            }
            // グループ番号が超えていたらダメ
            if (valid) {
                for (int i = 0; i < groupEntries; i++) {
                    if (getGroupNumber(i) > basic.getFatEndGroup()) {
                        valid = false;
                        break;
                    }
                }
            }
        } else {
            valid = !checkUsed(false);
        }
        return valid;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        setFileType1(fileType.getFormat() == basic.getFormatTypeNumber() ? fileType.getOrigin() : 0);
        setFileType2(fileType.getType());

        // 複数ある場合
        if (nextItem != null) {
            nextItem.setFileAttr(fileType);
        }
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        return new DiskBasicFileType(basic.getFormatTypeNumber(), getFileType2(), getFileType1());
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        int val = getFileType2();
        StringBuilder str = new StringBuilder();

        if ((val & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            str.append(rb.getString(typeNameCPM_2[TYPE_NAME_CPM_BINARY]));
        } else {
            str.append(rb.getString(typeNameCPM_2[TYPE_NAME_CPM_ASCII]));
        }
        if ((val & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            if (!str.isEmpty()) str.append(", ");
            str.append(rb.getString(typeNameCPM[TYPE_NAME_CPM_READ_ONLY]));
        }
        if ((val & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            if (!str.isEmpty()) str.append(", ");
            str.append(rb.getString(typeNameCPM[TYPE_NAME_CPM_SYSTEM]));
        }
        if ((val & FILE_TYPE_ARCHIVE_MASK.getValue()) != 0) {
            if (!str.isEmpty()) str.append(", ");
            str.append(rb.getString(typeNameCPM[TYPE_NAME_CPM_ARCHIVE]));
        }
        return str.toString();
    }

    /**
     * ファイルサイズをセット
     */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
    }

    /**
     * ファイルサイズとグループ数を計算する
     */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcFileSize = 0;
        int calcGroups = 0;

        int bytesPerGroup = basic.getSectorSize() * basic.getSectorsPerGroup();

        // グループ数を計算
        int mapSize = getGroupEntries();
        int groupSize = (bytesPerGroup * mapSize);
        int remainSize = (getExtentNumber() * SECTOR_UNIT_CPM + getRecordNumber()) * SECTOR_UNIT_CPM;
        // ファイルサイズは1エントリ分にする
        remainSize = ((remainSize + groupSize - 1) % groupSize) + 1;

        for (int mapPos = 0; mapPos < mapSize; mapPos++) {
            int groupNum = getGroupNumber(mapPos);
            if (groupNum == 0) break;
            basic.getNumsFromGroup(groupNum, 0, basic.getSectorSize(), remainSize, groupItems);

            calcFileSize += (remainSize < bytesPerGroup ? remainSize : bytesPerGroup);
            calcGroups++;

            remainSize -= bytesPerGroup;
        }
        groupItems.addSize(calcFileSize);
        groupItems.addNums(calcGroups);

        if (nextItem != null) {
            // ファイルには続きがある
            nextItem.getUnitGroups(fileUnitNum, groupItems);
        } else {
            // ファイル終り

            // グループサイズ
            groupItems.setSizePerGroup(bytesPerGroup);
            // 最終セクタのサイズを計算
            groupItems.setSize(recalcFileSize(groupItems, (int) groupItems.getSize()));
        }
    }

    /**
     * 最終セクタのサイズを計算してファイルサイズを返す
     *
     * @param groupItems   グループリスト
     * @param occupiedSize 占有サイズ
     * @return 計算後のファイルサイズ
     */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.size() == 0) return occupiedSize;

        DiskBasicGroupItem lItem = groupItems.last();
        DiskImageSector sector = basic.getSector(lItem.track, lItem.side, lItem.sectorEnd);
        if (sector == null) return occupiedSize;

        int sectorSize = sector.getSectorSize();
        int remainSize = ((occupiedSize + SECTOR_UNIT_CPM - 1) % SECTOR_UNIT_CPM) + 1;
        int unitPos = (((occupiedSize + sectorSize - 1) % sectorSize) / SECTOR_UNIT_CPM);
        byte[] buf = sector.getSectorBuffer();
        int bufOffset = unitPos * SECTOR_UNIT_CPM;
        remainSize = type.calcDataSizeOnLastSector(this, null, null, buf, bufOffset, SECTOR_UNIT_CPM, remainSize);

        occupiedSize = occupiedSize - SECTOR_UNIT_CPM + remainSize;

        return occupiedSize;
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return DirectoryCpm.SIZE;
    }

    /**
     * アイテムを返す
     */
    @Override
    public DirectoryCpm getData() {
        return data.data();
    }

    /**
     * アイテムをコピー
     */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    /**
     * ディレクトリをクリア ファイル新規作成時
     */
    @Override
    public void clearData() {
        data.fill(0, getDataSize(), basic.isDataInverted(), 0);
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     */
    @Override
    public boolean needCheckEofCode() {
        return externalAttr == 0;
    }

    /**
     * セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合
     */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // ファイルの最終が終端記号で終わっているかを調べる
            // ただし、ファイルサイズが128バイトと合うなら終端記号は不要
            if ((fileSize % SECTOR_UNIT_CPM) != 0) {
                fileSize = checkEofCode(iStream, fileSize);
                fileSize--;
            }
        }
        return fileSize;
    }

    /**
     * 最初のグループ番号を設定
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int val = 0;
        val = groupWidth > 1 ? ByteUtil.readLeShort(data.data().mapBytes, 0) & 0xffff : data.data().mapBytes[0] & 0xff;
        if (basic.isDataInverted()) val ^= (groupWidth > 1 ? 0xffff : 0xff); // invert
        return val;
    }

    /**
     * アイテムを削除できるか
     */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /**
     * グループ番号をセット (aka setGroup)
     */
    public void setGroupNumber(int pos, int val) {
        if (pos < 0 || pos >= groupEntries) return;

        if (basic.isDataInverted()) val = ~val; // invert

        if (groupWidth > 1) {
            ByteUtil.writeLeShort((short) val, data.data().mapBytes, pos * 2);
        } else {
            data.data().mapBytes[pos] = (byte) val;
        }
    }

    /**
     * グループ番号を返す (aka getGroup)
     */
    public int getGroupNumber(int pos) {
        int val = 0;
        if (pos < 0 || pos >= groupEntries) return val;

        val = groupWidth > 1 ? ByteUtil.readLeShort(data.data().mapBytes, pos * 2) & 0xffff : data.data().mapBytes[pos] & 0xff;
        if (basic.isDataInverted()) val ^= (groupWidth > 1 ? 0xffff : 0xff); // invert
        return val;
    }

    /**
     * エクステント番号を返す
     */
    public int getExtentNumber() {
        int num = data.data().extentNum & 0xff;
        if (basic.isDataInverted()) num ^= 0xff; // invert
        return num;
    }

    /**
     * レコード番号を返す
     */
    public int getRecordNumber() {
        int num = data.data().recordNum & 0xff;
        if (basic.isDataInverted()) num ^= 0xff; // invert
        return num;
    }

    /**
     * ファイルサイズからエクステント番号とレコード番号をセット
     */
    public void calcExtentAndRecordNumber(int val) {
        //int limitSize = basic.getSectorSize() * basic.getSectorsPerGroup() * groupEntries;

        if (val == 0) {
            data.data().extentNum = 0;
            data.data().recordNum = 0;
        } else {
            val = (val + SECTOR_UNIT_CPM - 1) / SECTOR_UNIT_CPM;
            if ((val % SECTOR_UNIT_CPM) == 0) {
                data.data().extentNum = (byte) ((val / SECTOR_UNIT_CPM) - 1);
                data.data().recordNum = (byte) SECTOR_UNIT_CPM;
            } else {
                data.data().extentNum = (byte) (val / SECTOR_UNIT_CPM);
                data.data().recordNum = (byte) (((val - 1) % SECTOR_UNIT_CPM) + 1);
            }
        }
        if (basic.isDataInverted()) {
            data.data().extentNum ^= (byte) 0xff; // invert
            data.data().recordNum ^= (byte) 0xff; // invert
        }
    }

    /**
     * 次のアイテムをセット
     */
    public void setNextItem(DiskBasicDirItem<DirectoryCpm> val) {
        nextItem = (DiskBasicDirItemCPM) val;
    }

    /**
     * 次のアイテムを返す
     */
    public DiskBasicDirItemCPM getNextItem() {
        return nextItem;
    }

    /**
     * アイテムソート用
     */
    public static int compare(DiskBasicDirItem<DirectoryCpm> item1, DiskBasicDirItem<DirectoryCpm> item2) {
        byte[] d1 = Arrays.copyOf(item1.getRawData(), DirectoryCpm.SIZE);
        byte[] d2 = Arrays.copyOf(item2.getRawData(), DirectoryCpm.SIZE);
        if (item1.getBasic().isDataInverted()) {
            Common.invertMemory(d1, DirectoryCpm.SIZE);
            Common.invertMemory(d2, DirectoryCpm.SIZE);
        }

        int cmp = 0;
        // ユーザID＋ファイル名＋拡張子＋エクステント番号
        cmp = Arrays.compare(d1, 0, 13, d2, 0, 13);
        // ＋レコード番号(逆)
        if (cmp == 0) cmp = Arrays.compare(d1, 15, 16, d2, 15, 16);
        // ＋マップ
        if (cmp == 0) cmp = Arrays.compare(d1, 16, 17, d2, 16, 17);
        return cmp;
    }

    /**
     * 名前比較
     */
    public static int compareName(DiskBasicDirItem<DirectoryCpm> item1, DiskBasicDirItem<DirectoryCpm> item2) {
        byte[] d1 = Arrays.copyOf(item1.getRawData(), DirectoryCpm.SIZE);
        byte[] d2 = Arrays.copyOf(item2.getRawData(), DirectoryCpm.SIZE);
        if (item1.getBasic().isDataInverted()) {
            Common.invertMemory(d1, DirectoryCpm.SIZE);
            Common.invertMemory(d2, DirectoryCpm.SIZE);
        }

        int cmp = 0;
        // ファイル名＋拡張子
        cmp = Arrays.compare(d1, 1, 12, d2, 1, 12);
        return cmp;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // 拡張子で属性を設定する
        ftype = getFileTypeByExt(externalAttr, Utils.getExt(filename));
        return ftype;
    }

    /** グループ番号のエントリ数を返す */
    public int getGroupEntries() {
        return groupEntries;
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TYPE", data.data().type & 0xff);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("EXTENT_NUM", data.data().extentNum & 0xff);
        vals.add("RESERVED", data.data().reserved, data.data().reserved.length);
        vals.add("RECORD_NUM", data.data().recordNum & 0xff);
        vals.add("MAP", data.data().mapBytes, data.data().mapBytes.length);
    }
}