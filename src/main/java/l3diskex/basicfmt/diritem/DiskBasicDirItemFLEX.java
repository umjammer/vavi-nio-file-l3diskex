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
import l3diskex.basicfmt.BasicCommon.DirectoryFlex;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.FlexPtr;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_UNDELETE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;


/// ディレクトリ１アイテム FLEX
public class DiskBasicDirItemFLEX extends DiskBasicDirItem<DirectoryFlex> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    static final int TYPE_NAME_FLEX_READ_ONLY = 0;
    static final int TYPE_NAME_FLEX_UNDELETE = 1;
    static final int TYPE_NAME_FLEX_WRITE_ONLY = 2;
    static final int TYPE_NAME_FLEX_HIDDEN = 3;
    static final int TYPE_NAME_FLEX_RANDOM = 4;

    /// FLEX属性
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
    private final DiskBasicDirData<DirectoryFlex> m_data = new DiskBasicDirData<>();

    /** ランダムアクセスファイルのインデックス(FSM)のグループ番号 */
    private final List<Integer> m_random_group_nums = new ArrayList<>();

    public DiskBasicDirItemFLEX(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryFlex.class);
    }

    public DiskBasicDirItemFLEX(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryFlex.class, n_data, dataP);
    }

    public DiskBasicDirItemFLEX(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryFlex.class, n_data, dataP);

        used(checkUsed(n_unuse[0]));

        calcFileSize();
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryFlex.class, n_data, dataP);
    }

    private int phySecPos(int sector_number) {
        return (sector_number - 1) % basic.diskBasicParam.getGroupsPerSector();
    }

    private int secBufOfs(int sector_number) {
        return phySecPos(sector_number) * basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
    }

    private int logSecSiz(int sector_size) {
        return sector_size / basic.diskBasicParam.getGroupsPerSector();
    }

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = m_data.data().name.length;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** 拡張子を格納する位置を返す */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    /** 属性１を返す */
    @Override
    protected int getFileType1() {
        return m_data.data().type & 0xff;
    }

    /** 属性１のセット */
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    /** 属性２を返す */
    @Override
    public int getFileType2() {
        return m_data.data().randomAccess & 0xff;
    }

    /** 属性２のセット */
    @Override
    protected void setFileType2(int val) {
        m_data.data().randomAccess = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return m_data.data().name[0] != 0 && (m_data.data().name[0] & 0x80) == 0;
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;

        if (m_data.data().name[0] == 0) {
            last[0] = true;
            return valid;
        }
        // 属性 0-3bitはゼロ
        if ((m_data.data().type & 0x0f) != 0) {
            valid = false;
        }
        return valid;
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除はエントリのMSBをセットするだけ
        m_data.data().name[0] |= (byte) 0x80;
        used(false);
        return true;
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int val = 0;
        for (int i = 0; i <= TYPE_NAME_FLEX_HIDDEN; i++) {
            if ((ftype & gTypeValueFLEX[i][0]) != 0) {
                val |= gTypeValueFLEX[i][1];
            }
        }
        if ((ftype & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            int val2 = file_type.getOrigin();
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
        StringBuilder str = new StringBuilder();
        int val = getFileAttr().getType();
        for (int i = 0; i <= TYPE_NAME_FLEX_RANDOM; i++) {
            if ((val & (int) Utils.valueAt(gTypeNameFLEX, i)) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(Utils.keyAt(gTypeNameFLEX, i)));
            }
        }

        return str.toString();
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int sec_size = logSecSiz(basic.getSectorSize()) - 4;
        val = (val + sec_size - 1) / sec_size;
        m_data.data().totalSectors = (short) val; // le
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /** 指定ディレクトリのすべてのグループを取得 */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException {
        // セクタ先頭4バイトを除く
        int sec_size = logSecSiz(basic.getSectorSize()) - 4;

        int calc_file_size = 0;
        int calc_groups = 0;

        DirectoryFlex d = m_data.data();

        int track_num = d.startTrack;
        int sector_num = d.startSector;
        int next_track_num = 0;
        int next_sector_num = 0;
        int[] div_num = new int[1];

        int random_file = getFileType2();
        if (random_file > 0) {
            // ランダムアクセスファイル
            for (int idx = 0; idx < random_file; idx++) {
                int gnum = type.getSectorPosFromNumS(track_num, sector_num);

                DiskImageSector sector = basic.getSectorFromSectorPos(gnum, div_num);
                if (sector == null) {
                    // error
                    break;
                }
                FlexPtr p = new FlexPtr();
                Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(secBufOfs(div_num[0] + 1))), p);
                next_track_num = p.nextTrack;
                next_sector_num = p.nextSector;

                m_random_group_nums.add(gnum);

                track_num = next_track_num;
                sector_num = next_sector_num;
            }
        } else {
            m_random_group_nums.clear();
        }

        int limit = basic.getFatEndGroup() + 1;
        while ((track_num != 0 || sector_num != 0) && limit >= 0) {
            int gnum = type.getSectorPosFromNumS(track_num, sector_num);

            DiskImageSector sector = basic.getSectorFromSectorPos(gnum, div_num);
            if (sector == null) {
                // error
                break;
            }
            FlexPtr p = new FlexPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(secBufOfs(div_num[0] + 1))), p);
            next_track_num = p.nextTrack;
            next_sector_num = p.nextSector;

            calc_file_size += sec_size;
            calc_groups++;

            int next_gnum = type.getSectorPosFromNumS(next_track_num, next_sector_num);

            int[] ptrack_num = {0}, pside_num = {0}, psector_num = {0}, div_nums = {0};
            type.getNumFromSectorPos(gnum, ptrack_num, pside_num, psector_num, div_num, div_nums);
            group_items.add(gnum, next_gnum, ptrack_num[0], pside_num[0], psector_num[0], psector_num[0], div_num[0], div_nums[0]);

            track_num = next_track_num;
            sector_num = next_sector_num;

            limit--;

            if (track_num == 0 || sector_num == 0) {
                // 最終セクタは0パディング部分のサイズを減らす
                byte[] buf = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
                for (int pos = logSecSiz(sector.getSectorSize()) - 1; pos >= 4; pos--) {
                    if (buf[pos] != 0) break;
                    calc_file_size--;
                }
                break;
            }
        }

        group_items.setNums(calc_groups);

        // ファイルサイズ
        //int inter_file_size = wxUINT16_SWAP_ON_LE(m_data.Data()->total_sectors) * sec_size;
        //if (inter_file_size == 0) {
        //    inter_file_size = calc_file_size;
        //}
        group_items.setSize(calc_file_size);
        group_items.setSizePerGroup(logSecSiz(basic.getSectorSize()) * basic.getSectorsPerGroup());
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
                (m_data.data().year % 100) + (tm.getYear() < 80 ? 100 : 0),
                m_data.data().month - 1,
                m_data.data().day);
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

        m_data.data().year = (byte) (tm.getYear() % 100);
        m_data.data().month = (byte) (tm.getMonth().ordinal() + 1);
        m_data.data().day = (byte) tm.getDayOfMonth();
    }

    /** 時間をセット */
    @Override
    public void setFileCreateTime(LocalDateTime tm) {
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryFlex getData() {
        return m_data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        m_data.fill(0);
    }

    /** アイテムを削除できるか */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int[] trk_num = {0};
        int[] sec_num = {0};
        type.getNumFromSectorPosS(val, trk_num, sec_num);
        m_data.data().startTrack = (byte) trk_num[0];
        m_data.data().startSector = (byte) sec_num[0];
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileunit_num) {
        int val = type.getSectorPosFromNumS(m_data.data().startTrack, m_data.data().startSector);
        return val;
    }

    /** 最後のグループ番号をセット */
    @Override
    public void setLastGroup(int val) {
        int[] trk_num = {0};
        int[] sec_num = {0};
        type.getNumFromSectorPosS(val, trk_num, sec_num);
        m_data.data().lastTrack = (byte) trk_num[0];
        m_data.data().lastSector = (byte) sec_num[0];
    }

    /** 最後のグループ番号を返す */
    @Override
    public int getLastGroup() {
        int val = type.getSectorPosFromNumS(m_data.data().lastTrack, m_data.data().lastSector);
        return val;
    }

    /** 追加のグループ番号を得る(機種依存) */
    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.addAll(m_random_group_nums);
    }

    /** 最初のトラック番号をセット */
    public void setStartTrack(int val) {
        m_data.data().startTrack = (byte) val;
    }

    /** 最初のセクタ番号をセット */
    public void setStartSector(int val) {
        m_data.data().startSector = (byte) val;
    }

    /** 最初のトラック番号を返す */
    public int getStartTrack() {
        return m_data.data().startTrack & 0xff;
    }

    /** 最初のセクタ番号を返す */
    public int getStartSector() {
        return m_data.data().startSector & 0xff;
    }

    /** 最後のトラック番号をセット */
    public void setLastTrack(int val) {
        m_data.data().lastTrack = (byte) val;
    }

    /** 最後のセクタ番号をセット */
    public void setLastSector(int val) {
        m_data.data().lastSector = (byte) val;
    }

    /** 最後のトラック番号を返す */
    public int getLastTrack() {
        return m_data.data().lastTrack & 0xff;
    }

    /** 最後のセクタ番号を返す */
    public int getLastSector() {
        return m_data.data().lastSector & 0xff;
    }

    //
    // ダイアログ用
    //

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("TYPE", m_data.data().type);
        vals.add("RESERVED", m_data.data().reserved);
        vals.add("START_TRACK", m_data.data().startTrack);
        vals.add("START_SECTOR", m_data.data().startSector);
        vals.add("LAST_TRACK", m_data.data().lastTrack);
        vals.add("LAST_SECTOR", m_data.data().lastSector);
        vals.add("TOTAL_SECTORS", (byte) m_data.data().totalSectors, true); // true for swap
        vals.add("RANDOM_ACCESS", m_data.data().randomAccess);
        vals.add("RESERVED2", m_data.data().reserved2);
        vals.add("MONTH", m_data.data().month);
        vals.add("DAY", m_data.data().day);
        vals.add("YEAR", m_data.data().year);
    }
}
