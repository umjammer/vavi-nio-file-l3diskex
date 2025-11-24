///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMDOS.DirectoryMdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeMDOS.FORMAT_TYPE_MDOS;


/**
 * ディレクトリ１アイテム MDOS
 */
public class DiskBasicDirItemMDOS extends DiskBasicDirItem<DirectoryMdos> {

    /**
     * ディレクトリエントリ MDOS (16bytes)
     */
    @Serdes
    public static class DirectoryMdos implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte unknown; // byte
        @Element(sequence = 4, bigEndian = "false")
        public short startGroup; // little endian
        @Element(sequence = 5)
        public short fileSize; // big endian

        public static final int SIZE = 16;
    }

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryMdos> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_MDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMdos.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryMdos.class, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryMdos.class, data, dataP);
        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || (this.data.data() != null && this.data.data().name != null && this.data.data().name[0] == 0));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMdos.class, data, dataPos);
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        byte[] data = this.data.getRawData();
        return DiskBasicDirItem.checkData((data != null) ? data : null, getDataSize(), last);
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** ファイル名を格納する位置を返す */
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

    /** 拡張子を格納する位置を返す */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

//    /** 属性１を返す */
//    public int getFileType1();

    /** 属性１のセット */
    @Override
    protected void setFileType1(int val) {
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.data.data().name[0] != 0;
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = FILE_TYPE_BINARY_MASK.getValue();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, 0);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        return "";
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        data.data().fileSize = (short) val; // le
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        short val = data.data().fileSize;
        return val /* le */ & 0xffff;
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** 指定ディレクトリのすべてのグループを取得 */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcFileSize = 0;
        int calcGroups = 0;

        // 16bit FAT
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = basic.getType().getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // 同じポジションならエラー
                rc = false;
            } else if (nextGroup == basic.getGroupFinalCode()) {
                // 最終グループ(0xffff)
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else if (nextGroup >= basic.getGroupSystemCode()) {
                // システム領域はエラー(0xeeee)
                rc = false;
            }
            if (rc) {
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), 0, groupItems);
                calcFileSize += (basic.getSectorSize() * basic.getSectorsPerGroup());
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        groupItems.addNums(calcGroups);
        groupItems.addSize(calcFileSize >= getFileSize() ? getFileSize() : calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().startGroup = (short) (val & 0xffff); // be
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return data.data().startGroup & 0xffff; // be
    }

    /** ファイルの終端コードをチェックする必要があるか */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /** セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合など */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) {
        return fileSize;
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryMdos getData() {
        return data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir());
    }

    /** アイテムが実行アドレスを持っているか */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convFileTypeFromFileName(String filename) {
        int fType = FILE_TYPE_BINARY_MASK.getValue();
        return fType;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        return t1;
    }

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("UNKNOWN", data.data().unknown);
        vals.add("START_GROUP", data.data().startGroup);
        vals.add("FILE_SIZE", data.data().fileSize);
    }
}
