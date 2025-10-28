package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.DirectoryMzFdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/**
 * MZ Floppy DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 * @li IPLString : セクタ1のIPL
 */
public class DiskBasicTypeMZFDOS extends DiskBasicTypeMZBase<DirectoryMzFdos> {

    /** 使用状況セクタ */
    @Serdes(bigEndian = false)
    static class StFatMzFdos {

        @Element(sequence = 1)
        public byte[] reserved1 = new byte[32];
        // TODO: unknown
        @Element(sequence = 2)
        public byte sides;
        @Element(sequence = 3)
        public byte volume_num;
        @Element(sequence = 4)
        public byte[] sign = new byte[17];
        // sector
        @Element(sequence = 5)
        public short empty_start;
        @Element(sequence = 6)
        public byte[] map = new byte[203];
    }

    public DiskBasicTypeMZFDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMzFdos> dir) {
        super(basic, fat, dir);
    }

    /**
     * 使用しているグループの位置を得る
     */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /**
     * 次のグループ番号を得る
     * <p>
     * セクタ末尾に次のトラック＆セクタ番号がある
     */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        int[] trk_num = {0}, sid_num = {0}, sec_num = {0};
        basic.calcNumFromSectorPosForGroup(sector_pos, trk_num, sid_num, sec_num, null, null);
        DiskImageSector sector = basic.getSector(trk_num[0], sid_num[0], sec_num[0]);
        if (sector == null) return 0;

        byte[] b = sector.getSectorBuffer();
        int s = sector.getSectorSize();
        byte next_trk = basic.invertUint8(b[s - 2]);
        byte next_sec = basic.invertUint8(b[s - 1]);
        return basic.calcSectorPosFromNumTForGroup(next_trk, next_sec);
    }

    /**
     * FATエリアをチェック
     *
     * @param is_formatting フォーマット中か
     * @return 1.0       正常
     * 0.0 - 1.0 警告あり
     * <0.0      エラーあり
     */
    @Override
    public double checkFat(boolean is_formatting) throws IOException {
        double valid_ratio = 1.0;

        // FATエリア
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return -1.0;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return -1.0;
        }
        StFatMzFdos f = new StFatMzFdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);

        byte sides = basic.invertUint8(f.sides);
        if (sides == 0 || sides >= basic.getTracksPerSide()) {
            valid_ratio = -1.0;
        }
        //data_start_group = start_track * basic.getSectorsPerTrackOnBasic();
        // 最終グループ番号
        if (basic.getFatEndGroup() == 0) {
            basic.diskBasicParam.setFatEndGroup(basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        }
        return valid_ratio;
    }

    /**
     * ファイルをセーブする前の準備を行う
     *
     * @param istream   ストリームバッファ
     * @param file_size [in,out] 出力サイズ
     * @param pitem     [in,out] ファイル名、属性を持っているディレクトリアイテム
     * @param nitem     [in,out] 確保したディレクトリアイテム
     * @param errinfo   [in,out] エラー情報
     */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryMzFdos> pitem, DiskBasicDirItem<DirectoryMzFdos> nitem, DiskBasicError errinfo) throws IOException {
        // Chain用のセクタを確保する
        int gnum = getEmptyGroupNumber();
        if (gnum == INVALID_GROUP_NUMBER) {
            return false;
        }
        // セクタ
        DiskImageSector sector = basic.getSectorFromGroup(gnum);
        if (sector == null) {
            return false;
        }
        byte[] c = sector.getSectorBuffer();
        if (c == null) {
            return false;
        }
        sector.fill(basic.invertUint8((byte) 0));
        // チェイン情報にセクタをセット
        nitem.setChainSector(sector, c, null);

        // 開始グループを設定
        nitem.setStartGroup(0, gnum);

        // セクタを予約
        setGroupNumber(gnum, 1);
        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) nitem;
        ditem.setChainUsedSector(gnum, true);

        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileunit_num ファイル番号
     * @param item         [in,out]        ディレクトリアイテム
     * @param data_size    確保するデータサイズ（バイト）
     * @param flags        新規か追加か
     * @param group_items  [out] 確保したセクタリスト
     * @return >0:正常 -1:空きなし(開始グループ設定前) -2:空きなし(開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        int[] file_size = {0};
        int[] groups = {0};

        int rc = 0;
        //int group_num = 0;
        int remain = data_size;
        boolean is_chain = item.needChainInData();
        int sec_size = basic.getSectorSize();
        if (is_chain) {
            sec_size -= 2;
        }

        // 必要なグループ数
        int group_size = ((data_size - 1) / sec_size / basic.getSectorsPerGroup()) + 1;
        if (is_chain) {
            group_size = 1;
        }

        // 未使用が連続している位置をさがす
        int[] group_start = {0};
        int cnt = findContinuousArea(group_size, group_start);
        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // データの開始グループ決定
        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) item;
        ditem.setDataGroup(group_start[0]);
        // シーケンス番号
        ditem.assignSeqNumber();

        // 領域を確保する
        rc = allocateGroupsSub(item, group_start[0], remain, sec_size, group_items[0], file_size, groups);

        // 確保したグループ数をセット
        ditem.setGroupSize(groups[0] + 1);

        // FATの空き位置を更新
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            byte[] sectorBuffer = sector.getSectorBuffer();
            if (sectorBuffer != null) {
                StFatMzFdos f = new StFatMzFdos();
                Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f); // TODO write back
                int group_end = group_items[0].last().group + 1;
                int empty_start = basic.invertAndOrderUint16(f.empty_start);
                if (empty_start < group_end) {
                    f.empty_start = basic.invertAndOrderUint16((short) group_end);
                }
            }
        }

        return rc;
    }

    /**
     * グループを確保して使用中にする
     */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryMzFdos> item, int group_start, int remain, int sec_size, DiskBasicGroups group_items, int[] file_size, int[] groups) throws IOException {
        int rc = 0;
        int group_num = group_start;
        int prev_group = 0;

        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) item;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // 使用しているか
            boolean used_group = isUsedGroupNumber(group_num);
            if (!used_group) {
                if (prev_group > 0 && prev_group <= basic.getFatEndGroup()) {
                    // 使用済みにする
                    basic.getNumsFromGroup(prev_group, group_num, sec_size, remain, group_items);
                    setGroupNumber(prev_group, 1);
                    ditem.setChainUsedSector(prev_group, true);
                    file_size[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                    groups[0]++;
                }
                remain -= (sec_size * basic.getSectorsPerGroup());
                prev_group = group_num;
            }
            // 次のグループ
            group_num++;
            limit--;
        }
        if (prev_group > 0 && prev_group <= basic.getFatEndGroup()) {
            // 使用済みにする
            basic.getNumsFromGroup(prev_group, 0, sec_size, remain, group_items);
            setGroupNumber(prev_group, 1);
            ditem.setChainUsedSector(prev_group, true);
            file_size[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
            groups[0]++;
        }
        if (prev_group > basic.getFatEndGroup()) {
            // ファイルがオーバフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }
        return rc;
    }

    /**
     * データの読み込み/比較処理
     *
     * @param fileunit_num  ファイル番号
     * @param item          ディレクトリアイテム
     * @param istream       [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時はnull
     * @param ostream       [in,out] 出力先 データ読み出し時に使用 ベリファイ時はnull
     * @param sector_buffer セクタバッファ
     * @param sector_size   バッファサイズ
     * @param remain_size   残りサイズ
     * @param sector_num    セクタ番号
     * @param sector_end    最終セクタ番号
     * @return >=0 : 処理したサイズ  -1:比較不一致  -2:セクタがおかしい
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryMzFdos> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        boolean need_chain = item.needChainInData();

        if (need_chain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sector_size -= 2;
        }

        int size = remain_size < sector_size ? remain_size : sector_size;

        if (ostream != null) {
            temp.setData(sector_buffer, size, basic.isDataInverted());

            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            istream.read(temp.getData(), 0, temp.getSize());
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), 0, temp.getSize(), sector_buffer, 0, temp.getSize())) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * セクタデータを埋めた後の個別処理
     * フォーマット FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        //
        // FATエリア
        //
        sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return false;
        }
        sector.fill(basic.diskBasicParam.getFillCodeOnFAT());

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        int size = sector.getSectorBufferSize();

        StFatMzFdos fdat = new StFatMzFdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), fdat);

        for (int i = 0; i < 32; i++) {
            fdat.reserved1[i] = (short) 0;
        }

        fdat.sides = (byte) basic.getSidesPerDiskOnBasic();

        fdat.volume_num = (byte) data.getVolumeNumber();

        Arrays.fill(fdat.sign, 0, fdat.sign.length, basic.diskBasicParam.getDirSpaceCode());
        byte[] volname = data.getVolumeName().getBytes();
        if (volname.length > 0) {
            int len = volname.length;
            if (len >=  fdat.sign.length) len =  fdat.sign.length - 1;
            System.arraycopy(volname, 0, fdat.sign, 0, len);
        }

        // システムエリアは使用済みにする
        int gnum_start = 0;
        int gnum_end = basic.diskBasicParam.getDirEndSector();

        fdat.empty_start = basic.orderUint16((short) gnum_end);
        for (int gnum = gnum_start; gnum < gnum_end; gnum++) {
            int[] pos = {gnum};
            int[] mask = {0};
            calcUsedGroupPos(gnum, pos, mask);
            fdat.map[pos[0]] = (byte) (fdat.map[pos[0]] | mask[0]);
        }
        // TODO serialize

        // invert
        basic.invertMem(buf, size);

        //
        // MZ DISK BASICが使用するFATエリアは使用済みとして初期化する
        //
        sector = basic.getSectorFromSectorPos(15);
        if (sector != null) {
            sector.fill(basic.invertUint8((byte) 0xff));
            sector.fill(basic.invertUint8((byte) 0), 4, 2);
        }
        sector = basic.getSectorFromSectorPos(13);
        if (sector != null) {
            sector.fill(basic.invertUint8((byte) 0xff));
            sector.fill(basic.invertUint8((byte) 0), 1, 0);
        }

        //
        // DIRエリア
        //
        int[] trk_num = {0}, sid_num = {0}, sec_num = {0};
        //int index = 0;
        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            getNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            sector = basic.getSector(trk_num[0], sid_num[0], sec_num[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            }
        }

        return true;
    }

    /**
     * データの書き込み処理
     *
     * @param item       ディレクトリアイテム
     * @param istream    ストリームデータ
     * @param buffer     [out] セクタ内の書き込み先バッファ
     * @param size       書き込み先バッファサイズ
     * @param remain     残りのデータサイズ
     * @param sector_num セクタ番号
     * @param group_num  現在のグループ番号
     * @param next_group 次のグループ番号
     * @param sector_end 最終セクタ番号
     * @param seq_num    通し番号(0...)
     * @return 書き込んだバイト数
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryMzFdos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        boolean need_chain = item.needChainInData();

        int len = 0;
        if (need_chain) {
            size -= 2;
        }

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                istream.read(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            istream.read(buffer, 0, size);
            len = size;
        }

        // チェーン用のトラック＆セクタ番号を書く
        if (need_chain) {
            int next_sector = group_num * basic.getSectorsPerGroup();
            // 次のデータがあるセクタ番号を入れる
            if (sector_num < sector_end) {
                next_sector++;
            } else {
                next_sector = (remain > size ? next_group * basic.getSectorsPerGroup() : 0);
            }
            next_sector /= basic.getSectorsPerGroup();
            if (next_sector > 0) {
                int[] trk = {0};
                int[] sid = {0};
                int[] sec = {0};
                basic.calcNumFromSectorPosForGroup(next_sector, trk, sid, sec, null, null);
                trk[0] *= basic.getSidesPerDiskOnBasic();
                trk[0] += basic.getReversedSideNumber(sid[0]);
                buffer[size] = basic.invertUint8((byte) trk[0]);
                buffer[size + 1] = basic.invertUint8((byte) sec[0]);
            } else {
                buffer[size] = basic.invertUint8((byte) 0);
                buffer[size + 1] = basic.invertUint8((byte) 0);
            }
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /**
     * データの書き込み終了後の処理
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryMzFdos> item) {
        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) item;
        ditem.setUnknownData();
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        StFatMzFdos f = new StFatMzFdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);
        // ボリューム番号
        data.setVolumeNumber(basic.invertUint8(f.volume_num));
        // サイン
        byte[] sign = new byte[17];
        basic.invertMem(f.sign, 17, sign);
        data.setVolumeName(new String(sign));
        data.setVolumeNameMaxLength(17);
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        StFatMzFdos f = new StFatMzFdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);

        // ボリューム番号
        f.volume_num = basic.invertUint8((byte) data.getVolumeNumber());
        // サイン
        Arrays.fill(f.sign, basic.invertUint8(basic.diskBasicParam.getDirSpaceCode()));
        byte[] volname = data.getVolumeName().getBytes();
        if (volname.length > 0) {
            int len = volname.length;
            if (len >= f.sign.length) len = f.sign.length - 1;
            System.arraycopy(volname, 0, f.sign, 0, f.sign.length);
        }
        // TODO deserialize
    }
}