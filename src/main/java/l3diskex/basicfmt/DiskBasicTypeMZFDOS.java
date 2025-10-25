package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;


/**
 * @class DiskBasicTypeMZFDOS
 * <p>
 * MZ Floppy DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 * @li IPLString : セクタ1のIPL
 */
public class DiskBasicTypeMZFDOS extends DiskBasicTypeMZBase {

    // For C++ struct st_fat_mz_fdos
    static class StFatMzFdos {

        public static final int RESERVED1_SIZE = 32;
        public static final int SIGN_SIZE = 17;
        public static final int MAP_SIZE = 203;

        public byte[] reserved1 = new byte[RESERVED1_SIZE];
        public byte sides;          // TODO: unknown
        public byte volume_num;
        public byte[] sign = new byte[SIGN_SIZE];
        public short empty_start;   // sector
        public byte[] map = new byte[MAP_SIZE];

        public StFatMzFdos() {
            // Initialize fields (equivalent to C++ zero initialization or default construction)
            for (int i = 0; i < RESERVED1_SIZE; i++) {
                reserved1[i] = (short) 0;
            }
            sides = (short) 0;
            volume_num = (short) 0;
            for (int i = 0; i < SIGN_SIZE; i++) {
                sign[i] = (short) 0;
            }
            empty_start = 0;
            for (int i = 0; i < MAP_SIZE; i++) {
                map[i] = (short) 0;
            }
        }

        public static StFatMzFdos fromSectorBuffer(byte[] buffer, int offset) {
            StFatMzFdos fdos = new StFatMzFdos();
            int currentOffset = offset;

            // reserved1
            for (int i = 0; i < RESERVED1_SIZE; i++) {
                fdos.reserved1[i] = buffer[currentOffset++];
            }

            // sides
            fdos.sides = buffer[currentOffset++];

            // volume_num
            fdos.volume_num = buffer[currentOffset++];

            // sign
            for (int i = 0; i < SIGN_SIZE; i++) {
                fdos.sign[i] = buffer[currentOffset++];
            }

            // empty_start (short - 2 bytes, assuming little-endian as per typical embedded systems or library functions)
            fdos.empty_start = ByteUtil.readLeShort(buffer, currentOffset);
            currentOffset += 2;

            // map
            for (int i = 0; i < MAP_SIZE; i++) {
                fdos.map[i] = buffer[currentOffset++];
            }

            return fdos;
        }

        public void toSectorBuffer(byte[] buffer, int offset) {
            int currentOffset = offset;

            // reserved1
            for (int i = 0; i < RESERVED1_SIZE; i++) {
                buffer[currentOffset++] = reserved1[i];
            }

            // sides
            buffer[currentOffset++] = sides;

            // volume_num
            buffer[currentOffset++] = volume_num;

            // sign
            for (int i = 0; i < SIGN_SIZE; i++) {
                buffer[currentOffset++] = sign[i];
            }

            // empty_start (short - 2 bytes)
            ByteUtil.writeLeShort(empty_start, buffer, currentOffset);
            currentOffset += 2;

            // map
            for (int i = 0; i < MAP_SIZE; i++) {
                buffer[currentOffset++] = map[i];
            }
        }
    }

    // Temp buffer for data access operations (simulating C++ member)
    private final DiskBasicTempData temp = new DiskBasicTempData();

    public DiskBasicTypeMZFDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
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
    public double checkFat(boolean is_formatting) {
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

        StFatMzFdos f = StFatMzFdos.fromSectorBuffer(sectorBuffer, 0);

        byte sides = basic.invertUint8(f.sides);
        if (sides == 0 || sides >= basic.getTracksPerSide()) {
            valid_ratio = -1.0;
        }
        //	data_start_group = start_track * basic->GetSectorsPerTrackOnBasic();
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
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem pitem, DiskBasicDirItem nitem, DiskBasicError errinfo) throws IOException {
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
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) {
        int[] file_size = {0};
        int[] groups = {0};

        int rc = 0;
//	int group_num = 0;
        int remain = data_size;
        boolean is_chain = item.needChainInData();
        int sec_size = basic.getSectorSize();
        if (is_chain) {
            sec_size -= 2;
        }

        // 必要なグループ数
        // C++: int group_size = ((data_size - 1) / sec_size / basic->GetSectorsPerGroup()) + 1;
        int group_size = ((data_size - 1) / sec_size / basic.getSectorsPerGroup()) + 1;
        if (is_chain) {
            group_size = 1;
        }

        // 未使用が連続している位置をさがす
        // C++: int group_start;
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
        rc = AllocateGroupsSub(item, group_start[0], remain, sec_size, group_items, file_size, groups);

        // 確保したグループ数をセット
        ditem.setGroupSize(groups[0] + 1);

        // FATの空き位置を更新
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            byte[] sectorBuffer = sector.getSectorBuffer();
            if (sectorBuffer != null) {
                StFatMzFdos f = StFatMzFdos.fromSectorBuffer(sectorBuffer, 0);

                // Assuming group_items.Last().group is int or similar that fits into short
                int group_end = group_items.last().group + 1;
                int empty_start = basic.invertAndOrderUint16(f.empty_start);
                if (empty_start < group_end) {
                    f.empty_start = basic.invertAndOrderUint16((short) group_end);

                    // Write back to buffer
                    f.toSectorBuffer(sectorBuffer, 0);
                }
            }
        }

        return rc;
    }

    /**
     * グループを確保して使用中にする
     */
    public int AllocateGroupsSub(DiskBasicDirItem item, int group_start, int remain, int sec_size, DiskBasicGroups group_items, int[] file_size, int[] groups) {
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
     * @param istream       [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時はNULL
     * @param ostream       [in,out] 出力先 データ読み出し時に使用 ベリファイ時はNULL
     * @param sector_buffer セクタバッファ
     * @param sector_size   バッファサイズ
     * @param remain_size   残りサイズ
     * @param sector_num    セクタ番号
     * @param sector_end    最終セクタ番号
     * @return >=0 : 処理したサイズ  -1:比較不一致  -2:セクタがおかしい
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        boolean need_chain = item.needChainInData();

        if (need_chain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sector_size -= 2;
        }

        int size = (remain_size < sector_size ? remain_size : sector_size);

        if (ostream != null) {
            // 書き出し (Read from disk to stream)
            // C++: temp.SetData(sector_buffer, size, basic->IsDataInverted());
            temp.setData(sector_buffer, size, basic.isDataInverted());

            // C++: ostream->Write((const void *)temp.GetData(), temp.GetSize());
            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            // 読み込んで比較 (Read from stream and compare with disk buffer)
            // C++: temp.SetSize(size);
            temp.setSize(size);
            // C++: istream->Read((void *)temp.GetData(), temp.GetSize());
            istream.read(temp.getData(), 0, temp.getSize());
            // C++: temp.InvertData(basic->IsDataInverted());
            temp.invertData(basic.isDataInverted());

            // C++: if (memcmp(temp.GetData(), sector_buffer, temp.GetSize()) != 0) {
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
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
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

        StFatMzFdos fdat = StFatMzFdos.fromSectorBuffer(buf, 0);

        // Array fields in StFatMzFdos are byte[], need to set their values
        for (int i = 0; i < StFatMzFdos.RESERVED1_SIZE; i++) {
            fdat.reserved1[i] = (short) 0;
        }

        fdat.sides = (byte) basic.getSidesPerDiskOnBasic();

        fdat.volume_num = (byte) data.getVolumeNumber();

        // Array fields in StFatMzFdos are byte[], need to set their values
        short dirSpaceCode = basic.diskBasicParam.getDirSpaceCode();
        for (int i = 0; i < StFatMzFdos.SIGN_SIZE; i++) {
            fdat.sign[i] = (byte) dirSpaceCode;
        }

        byte[] volname = data.getVolumeName().getBytes();
        if (volname.length > 0) {
            int len = volname.length;
            if (len >= StFatMzFdos.SIGN_SIZE) len = StFatMzFdos.SIGN_SIZE - 1;
            for (int i = 0; i < len; i++) {
                fdat.sign[i] = volname[i];
            }
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

        // Write back fdat to buf before inversion
        fdat.toSectorBuffer(buf, 0);

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
//	int index = 0;
        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            // C++: GetNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            getNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            // C++: sector = basic->GetSector(trk_num, sid_num, sec_num);
            sector = basic.getSector(trk_num[0], sid_num[0], sec_num[0]);
            if (sector != null) {
                // C++: sector->Fill(basic->InvertUint8(basic->GetFillCodeOnFAT()));
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
    public int writeFile(DiskBasicDirItem item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
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
    public void additionalProcessOnSavedFile(DiskBasicDirItem item) {
        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) item;
        ditem.setUnknownData();
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        StFatMzFdos f = StFatMzFdos.fromSectorBuffer(sectorBuffer, 0);

        // ボリューム番号
        data.setVolumeNumber(basic.invertUint8(f.volume_num));
        // サイン
        byte[] sign = new byte[StFatMzFdos.SIGN_SIZE];
        basic.invertMem(f.sign, StFatMzFdos.SIGN_SIZE, sign);
        char[] signChars = new char[StFatMzFdos.SIGN_SIZE];
        for (int i = 0; i < StFatMzFdos.SIGN_SIZE; i++) {
            signChars[i] = (char) sign[i];
        }
        data.setVolumeName(new String(signChars));
        data.setVolumeNameMaxLength(StFatMzFdos.SIGN_SIZE);
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        // C++: struct st_fat_mz_fdos *f = (struct st_fat_mz_fdos *)sector->GetSectorBuffer();
        StFatMzFdos f = StFatMzFdos.fromSectorBuffer(sectorBuffer, 0);

        // ボリューム番号
        f.volume_num = basic.invertUint8((byte) data.getVolumeNumber());
        // サイン
        short invertedSpaceCode = basic.invertUint8(basic.diskBasicParam.getDirSpaceCode());
        for (int i = 0; i < StFatMzFdos.SIGN_SIZE; i++) {
            f.sign[i] = (byte) invertedSpaceCode;
        }

        byte[] volname = data.getVolumeName().getBytes();
        if (volname.length > 0) {
            int len = volname.length;
            if (len >= StFatMzFdos.SIGN_SIZE) len = StFatMzFdos.SIGN_SIZE - 1;
            for (int i = 0; i < len; i++) {
                // Need to convert char to byte and then invert
                byte val = volname[i];
                f.sign[i] = val; // Inversion will happen when writing back if basic->InvertMem is called after
            }
        }

        // Write back fdat to buf
        f.toSectorBuffer(sectorBuffer, 0);

        // C++ has no explicit InvertMem here, relying on other operations if needed, but based on the C++ code, SetIdentifiedData doesn't call InvertMem.
        // If the entire FAT sector needs inversion after modification, it should be done here.
        // Comparing with AdditionalProcessOnFormatted, it seems the data in the buffer should be inverted before writing back.
        // Assuming the SetIdentifiedData is called when the sector is already loaded (and potentially inverted), and the data *written* to the struct fields needs to be inverted.

        // Re-read fdat to reflect the changes in the buffer, and then invert the sign data
        // This is complex due to the inversion logic. Assuming the `f` struct fields are meant to hold the *inverted* data if `basic->InvertUint8` was used,
        // and the buffer is then inverted again before being written to disk if needed by the disk implementation.

        // Based on `AdditionalProcessOnFormatted`:
        // 1. Fill struct fields (volume_num, sign) with *already inverted* values (f->volume_num = basic->InvertUint8(data.GetVolumeNumber());)
        // 2. Write struct back to buffer (f.toSectorBuffer)
        // 3. Invert the whole buffer (basic->InvertMem(buf, size);) -> this step is missing in the C++ SetIdentifiedData but present in AdditionalProcessOnFormatted.
        //    However, without the full context of how `GetManagedSector` interacts with disk storage (i.e., whether it automatically loads inverted or raw),
        //    I will stick to the literal C++ implementation for `SetIdentifiedData` which omits a final `basic->InvertMem(buf, size);`.

        // Let's re-examine `f->volume_num = basic->InvertUint8(data.GetVolumeNumber());`
        // In the sign block:
        // `memset(f->sign, basic->InvertUint8(basic->GetDirSpaceCode()), sizeof(f->sign));` (inverted space code)
        // `memcpy(f->sign, volname.data(), len);` (non-inverted volume name characters, which is inconsistent if the whole block should be inverted)

        // Given the inconsistency, and the lack of a final `basic->InvertMem(buf, size)` in the C++ `SetIdentifiedData`,
        // I'll proceed with the direct translation, accepting the potential logical flaw (if present in the original C++).
    }
}