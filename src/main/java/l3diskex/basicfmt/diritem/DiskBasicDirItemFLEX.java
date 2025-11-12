///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFLEX.DirectoryFlex;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_UNDELETE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;


/** ディレクトリ１アイテム FLEX */
public class DiskBasicDirItemFLEX extends DiskBasicDirItem<DirectoryFlex> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ FLEX (24bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryFlex implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte reserved;
        @Element(sequence = 5)
        public byte startTrack;
        @Element(sequence = 6)
        public byte startSector;
        @Element(sequence = 7)
        public byte lastTrack;
        @Element(sequence = 8)
        public byte lastSector;
        @Element(sequence = 9)
        public short totalSectors;
        @Element(sequence = 10)
        public byte randomAccess;
        @Element(sequence = 11)
        public byte reserved2;
        @Element(sequence = 12)
        public byte month;
        @Element(sequence = 13)
        public byte day;
        @Element(sequence = 14)
        public byte year;

        public static final int SIZE = 24;
    }

    /**
     * FLEX top of each sector
     */
    @Serdes(bigEndian = false)
    public static class FlexPointer {
        public static final int SIZE = 4;

        @Element(sequence = 1)
        public byte nextTrack;
        @Element(sequence = 2)
        public byte nextSector;
        @Element(sequence = 3)
        public short seqNum;

        public static FlexPointer serialize(byte[] b) throws IOException {
            FlexPointer p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            return p;
        }

        public byte[] deserialize() {
            return new byte[] {nextTrack, nextSector, (byte) (seqNum & 0xff), (byte) ((seqNum >> 8) & 0xff)};
        }
    }

    static final int TYPE_NAME_FLEX_READ_ONLY = 0;
    static final int TYPE_NAME_FLEX_UNDELETE = 1;
    static final int TYPE_NAME_FLEX_WRITE_ONLY = 2;
    static final int TYPE_NAME_FLEX_HIDDEN = 3;
    static final int TYPE_NAME_FLEX_RANDOM = 4;

    // FLEX属性
    static final int FILETYPE_MASK_FLEX_READ_ONLY = 0x80;
    static final int FILETYPE_MASK_FLEX_UNDELETE = 0x40;
    static final int FILETYPE_MASK_FLEX_WRITE_ONLY = 0x20;
    static final int FILETYPE_MASK_FLEX_HIDDEN = 0x10;

    public static final int FILETYPE_FLEX_RANDOM_MASK = 0xff0_0000;
    public static final int FILETYPE_FLEX_RANDOM_POS = 20;

    // FLEX属性名
    public static final Map<String, Object> gTypeNameFLEX = new LinkedHashMap<>() {{
        put("Read Only", FILE_TYPE_READONLY_MASK.getValue());
        put("Undeletable", FILE_TYPE_UNDELETE_MASK.getValue());
        put("Write Only", FILE_TYPE_WRITEONLY_MASK.getValue());
        put("Hidden", FILE_TYPE_HIDDEN_MASK.getValue());
        put("Random Access", FILE_TYPE_RANDOM_MASK.getValue());
    }};

    private static final int[][] gTypeValueFLEX = {
            {FILE_TYPE_READONLY_MASK.getValue(), FILETYPE_MASK_FLEX_READ_ONLY},
            {FILE_TYPE_UNDELETE_MASK.getValue(), FILETYPE_MASK_FLEX_UNDELETE},
            {FILE_TYPE_WRITEONLY_MASK.getValue(), FILETYPE_MASK_FLEX_WRITE_ONLY},
            {FILE_TYPE_HIDDEN_MASK.getValue(), FILETYPE_MASK_FLEX_HIDDEN},
    };

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryFlex> data = new DiskBasicDirData<>();

    /** ランダムアクセスファイルのインデックス(FSM)のグループ番号 */
    private final List<Integer> randomNumOfGroups = new ArrayList<>();

    public DiskBasicDirItemFLEX(DiskBasic basic) {
        super(basic);

        data.alloc(DirectoryFlex.class);
    }

    public DiskBasicDirItemFLEX(DiskBasic basic, DiskImageSector sector, int secPos, byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);

        this.data.attach(DirectoryFlex.class, data, dataP);
    }

    public DiskBasicDirItemFLEX(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int secPos,
                                byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);

        this.data.attach(DirectoryFlex.class, data, dataP);

        used(checkUsed(unuse[0]));

        calcFileSize();
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryFlex.class, data, dataPos);
    }

    private int phySecPos(int sectorNumber) {
        return (sectorNumber - 1) % basic.getGroupsPerSector();
    }

    private int secBufOfs(int sectorNumber) {
        return phySecPos(sectorNumber) * basic.getSectorSize() / basic.getGroupsPerSector();
    }

    private int logSectorSize(int sectorSize) {
        return sectorSize / basic.getGroupsPerSector();
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

    /** 属性１を返す */
    @Override
    protected int getFileType1() {
        return data.data().type & 0xff;
    }

    /** 属性１のセット */
    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /** 属性２を返す */
    @Override
    public int getFileType2() {
        return data.data().randomAccess & 0xff;
    }

    /** 属性２のセット */
    @Override
    protected void setFileType2(int val) {
        data.data().randomAccess = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return data.data().name[0] != 0 && (data.data().name[0] & 0x80) == 0;
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        if (data.data().name[0] == 0) {
            last[0] = true;
            return valid;
        }
        // 属性 0-3bitはゼロ
        if ((data.data().type & 0x0f) != 0) {
            valid = false;
        }
        return valid;
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除はエントリのMSBをセットするだけ
        data.data().name[0] |= (byte) 0x80;
        used(false);
        return true;
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
        if (ftype == -1) return;

        int val = 0;
        for (int i = 0; i <= TYPE_NAME_FLEX_HIDDEN; i++) {
            if ((ftype & gTypeValueFLEX[i][0]) != 0) {
                val |= gTypeValueFLEX[i][1];
            }
        }
        if ((ftype & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            int val2 = fileType.getOrigin();
            setFileType2(val2 != 0 ? val2 : 0x02);
        }

        setFileType1(val);
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int random = 0;
        int type1 = getFileType1();

        for (int i = 0; i <= TYPE_NAME_FLEX_HIDDEN; i++) {
            if ((type1 & gTypeValueFLEX[i][1]) != 0) {
                val |= gTypeValueFLEX[i][0];
            }
        }
        if (getFileType2() != 0) {
            val |= FILE_TYPE_RANDOM_MASK.getValue();
            random = getFileType2();
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, random);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        StringBuilder sb = new StringBuilder();
        int val = getFileAttr().getType();
        for (int i = 0; i <= TYPE_NAME_FLEX_RANDOM; i++) {
            if ((val & (int) Utils.valueAt(gTypeNameFLEX, i)) != 0) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(rb.getString(Utils.keyAt(gTypeNameFLEX, i)));
            }
        }

        return sb.toString();
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int sectorSize = logSectorSize(basic.getSectorSize()) - 4;
        val = (val + sectorSize - 1) / sectorSize;
        data.data().totalSectors = (short) val; // le
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
        // セクタ先頭4バイトを除く
        int secSize = logSectorSize(basic.getSectorSize()) - 4;

        int calcFileSize = 0;
        int calcGroups = 0;

        DirectoryFlex d = data.data();

        int trackNum = d.startTrack;
        int sectorNum = d.startSector;
        int nextTrackNum = 0;
        int nextSectorNum = 0;
        int[] divNum = new int[1];

        int randomFile = getFileType2();
        if (randomFile > 0) {
            // ランダムアクセスファイル
            for (int index = 0; index < randomFile; index++) {
                int groupNum = type.getSectorPosFromNumS(trackNum, sectorNum);

                DiskImageSector sector = basic.getSectorFromSectorPos(groupNum, divNum);
                if (sector == null) {
                    // error
                    break;
                }
                FlexPointer p = new FlexPointer();
                Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(secBufOfs(divNum[0] + 1))), p);
                nextTrackNum = p.nextTrack;
                nextSectorNum = p.nextSector;

                randomNumOfGroups.add(groupNum);

                trackNum = nextTrackNum;
                sectorNum = nextSectorNum;
            }
        } else {
            randomNumOfGroups.clear();
        }

        int limit = basic.getFatEndGroup() + 1;
        while ((trackNum != 0 || sectorNum != 0) && limit >= 0) {
            int gnum = type.getSectorPosFromNumS(trackNum, sectorNum);

            DiskImageSector sector = basic.getSectorFromSectorPos(gnum, divNum);
            if (sector == null) {
                // error
                break;
            }
            FlexPointer p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(secBufOfs(divNum[0] + 1))), p);
            nextTrackNum = p.nextTrack;
            nextSectorNum = p.nextSector;

            calcFileSize += secSize;
            calcGroups++;

            int next_gnum = type.getSectorPosFromNumS(nextTrackNum, nextSectorNum);

            int[] pTrackNum = {0}, pSideNum = {0}, pSectorNum = {0}, numOfDivs = {0};
            type.getNumFromSectorPos(gnum, pTrackNum, pSideNum, pSectorNum, divNum, numOfDivs);
            groupItems.add(gnum, next_gnum, pTrackNum[0], pSideNum[0], pSectorNum[0], pSectorNum[0], divNum[0], numOfDivs[0]);

            trackNum = nextTrackNum;
            sectorNum = nextSectorNum;

            limit--;

            if (trackNum == 0 || sectorNum == 0) {
                // 最終セクタは0パディング部分のサイズを減らす
                byte[] buf = sector.getSectorBuffer(secBufOfs(divNum[0] + 1));
                for (int pos = logSectorSize(sector.getSectorSize()) - 1; pos >= 4; pos--) {
                    if (buf[pos] != 0) break;
                    calcFileSize--;
                }
                break;
            }
        }

        groupItems.setNums(calcGroups);

        // ファイルサイズ
        //int interFileSize = data.data().totalSectors) * sectorSize;
        //if (interFileSize == 0) {
        //    interFileSize = calcFileSize;
        //}
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(logSectorSize(basic.getSectorSize()) * basic.getSectorsPerGroup());
    }

    @Override
    public boolean hasCreateDateTime() {
        return true;
    }

    @Override
    public boolean hasCreateDate() {
        return true;
    }

    @Override
    public boolean hasCreateTime() {
        return false;
    }

    /** 日付を返す */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        return LocalDate.of(
                (data.data().year % 100) + (tm.getYear() < 80 ? 100 : 0),
                data.data().month - 1,
                data.data().day);
    }

    /** 時間を返す */
    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        return LocalTime.of(0, 0, 0);
    }

    /** 日付を返す */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /** 時間を返す */
    @Override
    public String getFileCreateTimeStr() {
        return "";
    }

    /** 日付をセット */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() < 0 || tm.getMonth().ordinal() < -1) return;

        data.data().year = (byte) (tm.getYear() % 100);
        data.data().month = (byte) (tm.getMonth().ordinal() + 1);
        data.data().day = (byte) tm.getDayOfMonth();
    }

    /** 時間をセット */
    @Override
    public void setFileCreateTime(LocalDateTime tm) {
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryFlex getData() {
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
        data.fill(0);
    }

    /** アイテムを削除できるか */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        data.data().startTrack = (byte) trackNum[0];
        data.data().startSector = (byte) sectorNum[0];
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int val = type.getSectorPosFromNumS(data.data().startTrack, data.data().startSector);
        return val;
    }

    /** 最後のグループ番号をセット */
    @Override
    public void setLastGroup(int val) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        data.data().lastTrack = (byte) trackNum[0];
        data.data().lastSector = (byte) sectorNum[0];
    }

    /** 最後のグループ番号を返す */
    @Override
    public int getLastGroup() {
        int val = type.getSectorPosFromNumS(data.data().lastTrack, data.data().lastSector);
        return val;
    }

    /** 追加のグループ番号を得る(機種依存) */
    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.addAll(randomNumOfGroups);
    }

    /** 最初のトラック番号をセット */
    public void setStartTrack(int val) {
        data.data().startTrack = (byte) val;
    }

    /** 最初のセクタ番号をセット */
    public void setStartSector(int val) {
        data.data().startSector = (byte) val;
    }

    /** 最初のトラック番号を返す */
    public int getStartTrack() {
        return data.data().startTrack & 0xff;
    }

    /** 最初のセクタ番号を返す */
    public int getStartSector() {
        return data.data().startSector & 0xff;
    }

    /** 最後のトラック番号をセット */
    public void setLastTrack(int val) {
        data.data().lastTrack = (byte) val;
    }

    /** 最後のセクタ番号をセット */
    public void setLastSector(int val) {
        data.data().lastSector = (byte) val;
    }

    /** 最後のトラック番号を返す */
    public int getLastTrack() {
        return data.data().lastTrack & 0xff;
    }

    /** 最後のセクタ番号を返す */
    public int getLastSector() {
        return data.data().lastSector & 0xff;
    }

    //
    // ダイアログ用
    //

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("TYPE", data.data().type);
        vals.add("RESERVED", data.data().reserved);
        vals.add("START_TRACK", data.data().startTrack);
        vals.add("START_SECTOR", data.data().startSector);
        vals.add("LAST_TRACK", data.data().lastTrack);
        vals.add("LAST_SECTOR", data.data().lastSector);
        vals.add("TOTAL_SECTORS", (byte) data.data().totalSectors, true); // true for swap
        vals.add("RANDOM_ACCESS", data.data().randomAccess);
        vals.add("RESERVED2", data.data().reserved2);
        vals.add("MONTH", data.data().month);
        vals.add("DAY", data.data().day);
        vals.add("YEAR", data.data().year);
    }
}
