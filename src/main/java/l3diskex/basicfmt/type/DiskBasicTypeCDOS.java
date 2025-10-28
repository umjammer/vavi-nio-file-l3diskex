///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Arrays;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryCdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Common.mem_copy;
import static l3diskex.Utils.TEMP_DATA_SIZE;


/**
 * C-DOSの処理
 * <p>
 * DiskBasicParam
 * <li>IDString     FATエリアにあるID</li>
 * <li>IPLString    セクタ1のIPL</li>
 * <li>VolumeString ボリューム名</li>
 * <li>Endian       16ビット値のバイトオーダ</li>
 */
public class DiskBasicTypeCDOS extends DiskBasicTypeMZBase<DirectoryCdos> {

    /// CDOS 使用状況セクタ
    @Serdes
    static class st_fat_cdos {
        // 使用状況
        @Element(sequence = 1)
        public byte[] bits = new byte[0xae];
        // 拡張ディレクトリ
        @Element(sequence = 2)
        public short exdir;
        // ボリューム番号
        @Element(sequence = 3)
        public short volume_num;
        // 年
        @Element(sequence = 4)
        public byte yy;
        // 月
        @Element(sequence = 5)
        public byte mm;
        // 日
        @Element(sequence = 6)
        public byte dd;
        // ボリューム名
        @Element(sequence = 7)
        public byte[] volume_name = new byte[27];
        // ID
        @Element(sequence = 8)
        public byte[] id = new byte[16];
        @Element(sequence = 9)
        public byte[] reserved1 = new byte[0x20];
    }

    /** */
    public DiskBasicTypeCDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCdos> dir) {
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
     * FATエリアをチェック
     */
    @Override
    public double checkFat(boolean is_formatting) throws IOException {
        double valid_ratio = 1.0;

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return -1.0;
        }
        st_fat_cdos f = new st_fat_cdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), f);
        if (basic.invertUint8(f.bits[0]) != (byte) 0xff) {
            valid_ratio = 0.1;
        }
        byte[] d_id = basic.diskBasicParam.getVariousStringParam("IDString").getBytes(); // To8BitData
        if (d_id.length > 0) {
            // FM用はID部分に"FM"とある
            byte[] s_id = new byte[f.id.length];
            basic.invertMem(f.id, f.id.length, s_id);
            if (!Arrays.equals(s_id, d_id)) {
                valid_ratio = 0.1;
            }
        }

        // 最終グループ番号
        basic.diskBasicParam.setFatEndGroup(basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);

        return valid_ratio;
    }

    /**
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryCdos> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        int[] file_size = {0};
        int[] groups = {0};

        int rc = 0;
        // int group_num = 0;
        int remain = data_size;
        int sec_size = basic.getSectorSize();

        // 必要なグループ数
        int group_size = ((data_size - 1) / sec_size / basic.getSectorsPerGroup()) + 1;

        // 未使用が連続している位置をさがす
        int[] group_start = new int[1];
        int cnt = findContinuousArea(group_size, group_start);

        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // 開始グループ決定
        item.setStartGroup(fileunit_num, group_start[0]);

        // 領域を確保する
        rc = allocateGroupsSub(item, group_start[0], remain, sec_size, group_items[0], file_size, groups);

        return rc;
    }

    /**
     * サブディレクトリを作成できるか
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        //
        // IPL
        //
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));    // invert
            byte[] buf = sector.getSectorBuffer();
            if (buf != null) {
                byte[] ipl = basic.diskBasicParam.getVariousStringParam("IPLString").getBytes(); // To8BitData
                int len = ipl.length;
                if (len > 0) {
                    if (len > 32) len = 32;
                    basic.invertMem(ipl, len, buf);
                }
            }
        }

        //
        // FATエリア
        //
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return false;
        }
        fatbuf.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
        //memset(fatbuf.getBuffer(), basic.getFillCodeOnFAT(), fatbuf.getSize());
        //basic.InvertMem(fatbuf.getBuffer(), fatbuf.getSize());

        byte[] b = fatbuf.getBuffer();
        if (b == null) {
            return false;
        }
        st_fat_cdos f = new st_fat_cdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);

        // システムエリアは使用済みにする
        basic.invertMem(f.bits, f.bits.length);

        int gnum_start = 0;
        int gnum_end = (basic.getManagedTrackNumber() + 1) * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        for (int gnum = gnum_start; gnum < gnum_end; gnum++) {
            int[] pos = {gnum};
            int[] mask = {0};
            calcUsedGroupPos(gnum, pos, mask);
            f.bits[pos[0]] |= (byte) mask[0];
        }
        // オーバートラック部分は使用済みにする
        gnum_start = basic.getFatEndGroup() + 1;
        gnum_end = (0xb0 << 3);
        for (int gnum = gnum_start; gnum < gnum_end; gnum++) {
            int[] pos = {gnum};
            int[] mask = {0};
            calcUsedGroupPos(gnum, pos, mask);
            f.bits[pos[0]] |= (byte) mask[0];
        }

        basic.invertMem(f.bits, f.bits.length);

        // 拡張ディレクトリ（未対応）
        f.exdir = basic.invertUint16((short) 0xffff);

        // ID
        byte[] id = basic.diskBasicParam.getVariousStringParam("IDString").getBytes(); // To8BitData
        if (id.length > 0) {
            basic.invertMem(id, id.length, f.id);
        }

        // ボリューム番号を設定
        int vol_num = data.getVolumeNumber();
        f.volume_num = basic.invertAndOrderUint16((short) vol_num);
        // ボリューム名を設定
        byte[] vol_name;
        if (!data.getVolumeName().isEmpty()) {
            vol_name = data.getVolumeName().getBytes();
        } else {
            vol_name = basic.diskBasicParam.getVariousStringParam("VolumeString").getBytes(); // To8BitData
        }
        Common.mem_copy(vol_name, vol_name.length, (byte) 0, f.volume_name, f.volume_name.length);
        basic.invertMem(f.volume_name, f.volume_name.length);
        // ボリューム日付
        LocalDate tm = Utils.convDateStrToTm(data.getVolumeDate());
        if (tm == null) {
            tm = LocalDate.now();
        }
        byte[] yy = new byte[1], mm = new byte[1], dd = new byte[1];
        Utils.convTmToYYMMDD(tm.atStartOfDay(), yy, mm, dd);
        f.yy = basic.invertUint8(yy[0]);
        f.mm = basic.invertUint8(mm[0]);
        f.dd = basic.invertUint8(dd[0]);

        //
        // Auto Start
        //
        sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector());
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
        }

        //
        // DIRエリア
        //
        int[] trk_num = new int[1], sid_num = new int[1], sec_num = new int[1];
        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            getNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            sector = basic.getSector(trk_num[0], sid_num[0], sec_num[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnDir()));
            }
        }

        return true;
    }

    /**
     * データの読み込み/比較処理
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryCdos> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        int size = remain_size < sector_size ? remain_size : sector_size;

        if (ostream != null) {
            // 書き出し
            temp.setData(sector_buffer, size, basic.isDataInverted());

            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            istream.readNBytes(temp.getData(), 0, temp.getSize());
            temp.invertData(basic.isDataInverted());

            if (Arrays.compare(temp.getData(), sector_buffer) != 0) {
                // データが異なる
                return -1;
            }
        }

        return size;
    }

    /**
     * 内部ファイルをエクスポートする際に内容を変換
     */
    @Override
    public boolean convertDataForLoad(DiskBasicDirItem<DirectoryCdos> item, InputStream istream, OutputStream ostream) throws IOException {
        int osize = istream.available(); // TODO assume available as GetLength()

        if (item.getFileAttr().isAscii()) {
            // 最終バイトが0かどうかチェック
            ((SeekableDataInputStream) istream).position(osize - 1);
            if (istream.read() == 0) {
                osize--;
            }
            ((SeekableDataInputStream) istream).position(0);
        }

        temp.setSize(TEMP_DATA_SIZE);
        while (osize > 0) {
            int len = istream.readNBytes(temp.getData(), 0, temp.getSize());
            ostream.write(temp.getData(), 0, len > osize ? osize : len);
            osize -= len;
        }

        return true;
    }

    /**
     * エクスポートしたファイルをベリファイする際に内容を変換
     */
    @Override
    public boolean convertDataForVerify(DiskBasicDirItem<DirectoryCdos> item, InputStream istream, OutputStream ostream) throws IOException {
        int osize = istream.available(); // TODO assume available as GetLength()

        boolean need_null_code = false;
        if (item.getFileAttr().isAscii()) {
            // 最終バイトが0かどうかチェック
            ((SeekableDataInputStream) istream).position(osize - 1);
            int c = istream.read();
            if (c > 0 && c != 0xff) {
                need_null_code = true;
            }
            ((SeekableDataInputStream) istream).position(0);
        }

        temp.setSize(TEMP_DATA_SIZE);
        int len;
        while ((len = istream.read(temp.getData(), 0, temp.getSize())) > 0) {
            ostream.write(temp.getData(), 0, len);
        }

        if (need_null_code) {
            // 最後に$00をつけて出力
            ostream.write(0);
        }
        return true;
    }

    /**
     * ファイルをセーブする前にデータを変換
     */
    @Override
    public boolean convertDataForSave(DiskBasicDirItem<DirectoryCdos> item, InputStream istream, OutputStream ostream) throws IOException {
        // 処理はベリファイと同じ
        return convertDataForVerify(item, istream, ostream);
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryCdos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        int len = 0;

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                temp.setSize(remain);
                istream.readNBytes(temp.getData(), 0, temp.getSize());

                System.arraycopy(temp.getData(), 0, buffer, 0, temp.getSize());
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            temp.setSize(size);
            istream.readNBytes(temp.getData(), 0, temp.getSize());

            System.arraycopy(temp.getData(), 0, buffer, 0, temp.getSize());

            len = size;
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        st_fat_cdos f = new st_fat_cdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), f);

        // volume number
        data.setVolumeNumber(basic.invertAndOrderUint16(f.volume_num));
        // volume label
        byte[] volume_name = new byte[f.volume_name.length];
        basic.invertMem(f.volume_name, f.volume_name.length, volume_name);
        data.setVolumeName(new String(volume_name));
        data.setVolumeNameMaxLength(f.volume_name.length);
        // volume date
        LocalDate tm = Utils.convYYMMDDToTm(
                basic.invertUint8(f.yy),
                basic.invertUint8(f.mm),
                basic.invertUint8(f.dd)
        );
        data.setVolumeDate(l3diskex.Utils.formatYMDStr(tm));
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        st_fat_cdos f = new st_fat_cdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), f);

        DiskBasicFormat fmt = basic.getFormatType();

        // volume number
        if (fmt.hasVolumeNumber()) {
            f.volume_num = basic.invertAndOrderUint16((short) data.getVolumeNumber());
        }
        // volume label
        if (fmt.hasVolumeName()) {
            byte[] vol_name = data.getVolumeName().getBytes();
            mem_copy(vol_name, vol_name.length, (byte) 0, f.volume_name, f.volume_name.length);
            basic.invertMem(f.volume_name, f.volume_name.length);
        }
        // volume date
        if (fmt.hasVolumeDate()) {
            LocalDate tm = Utils.convDateStrToTm(data.getVolumeDate());
            if (tm != null) {
                byte[] yy = new byte[1], mm = new byte[1], dd = new byte[1];
                Utils.convTmToYYMMDD(tm.atStartOfDay(), yy, mm, dd);
                f.yy = basic.invertUint8(yy[0]);
                f.mm = basic.invertUint8(mm[0]);
                f.dd = basic.invertUint8(dd[0]);
            }
        }
    }
}
