/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryN88;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 N88-BASICの処理

 DiskBasicParam
 @li ReservedGroups : Group 予約済みにするグループ（クラスタ）番号
 */
public class DiskBasicTypeN88 extends DiskBasicTypeFAT8<DirectoryN88> {

    public DiskBasicTypeN88(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super(basic, fat, dir);
    }

    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;
        // 管理エリアに近い位置から検索

        // トラック当たりのグループ数
        int grps_per_trk = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // 最大グループ数
        int max_group = basic.getFatEndGroup() - managedStartGroup;
        if (max_group < managedStartGroup) max_group = managedStartGroup;
        max_group = max_group * 2 - 1;

        for (int i = 0; i <= max_group; i++) {
            int i2 = i / grps_per_trk;
            int i4 = i / grps_per_trk / 2;
            int num;
            if ((i2 & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * grps_per_trk) + (i % grps_per_trk);
            } else {
                num = managedStartGroup + ((i4 + 1) * grps_per_trk) + (i % grps_per_trk);
            }
            if (basic.getFatEndGroup() < num)
                continue;
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                new_num = num;
                break;
            }
        }
        return new_num;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int end_group = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            end_group /= basic.getSectorsPerGroup();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = super.checkFat(is_formatting);
        if (valid_ratio >= 0.0) {
            // FAT,ディレクトリエリアはシステム予約となっているか
            List<Integer> groups = basic.diskBasicParam.getReservedGroups();
            for (int idx = 0; idx < groups.size(); idx++) {
                int grp = getGroupNumber(groups.get(idx));
                if (grp != basic.diskBasicParam.getGroupSystemCode()) {
                    valid_ratio = -1.0;
                    break;
                }
            }
        }
        return valid_ratio;
    }

    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT,DIRエリア
        DiskImageTrack track = basic.getTrack(basic.getManagedTrackNumber(), basic.diskBasicParam.getFatSideNumber());
        if (track == null) return false;
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null) return false;
        int id_sec = (basic.diskBasicParam.getDirEndSector() + 1) % basic.getSectorsPerTrackOnBasic();
        for (int idx = 0; idx < sectors.size(); idx++) {
            DiskImageSector sector = sectors.get(idx);
            if (sector != null) {
                // ファイル管理エリアをクリア IDエリアは0でクリア
                sector.fill(sector.getSectorNumber() != id_sec ? basic.diskBasicParam.getFillCodeOnFAT() : 0);
            }
        }

        // システムで使用している部分のクラスタ位置を予約済みにする
        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int i = 0; i < grps.size(); i++) {
            setGroupNumber(grps.get(i), basic.diskBasicParam.getGroupSystemCode());
        }

        return true;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     *
     * @param item          ディレクトリアイテム
     * @param istream       [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時はnull
     * @param ostream       [in,out] 出力先 データ読み出し時に使用 ベリファイ時はnull
     * @param sector_buffer セクタバッファ
     * @param sector_size   バッファサイズ
     * @param remain_size   残りサイズ
     * @return 残りサイズ
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryN88> item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sectorOffset,
                                        int sector_size,
                                        int remain_size) throws IOException {
        // ファイルサイズはセクタサイズ境界なので要計算
        if (item.needCheckEofCode()) {
            // 終端コードの1つ前までを出力
            byte eof_code = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            byte null_code = basic.invertUint8((byte) 0);
            // ランダムアクセス時は除く
            int len = sector_size - 1;
            for (; len >= 0; len--) {
                if (sector_buffer[len] != eof_code && sector_buffer[len] != null_code)
                    break;
            }
            if (len >= 0)
                sector_size = len + 1;
        } else {
            // 計算手段がないので残りサイズをそのまま返す
            if (istream != null) {
                sector_size = istream.available() % sector_size; // TODO assume available as length
            } else {
                sector_size = remain_size;
            }
        }
        return sector_size;
    }

    @Override
    public int writeFile(DiskBasicDirItem<DirectoryN88> item,
                         InputStream istream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sector_num,
                         int group_num,
                         int next_group,
                         int sector_end,
                         int seq_num) throws IOException {
        boolean need_eof_code = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                if (need_eof_code) {
                    int bytesRead = istream.read(buffer, 0, remain);
                    // 最終は終端コードを入れる
                    // ただし、残りサイズが丁度セクタサイズなら入れない
                    if (bytesRead + 1 == remain) {
                        buffer[remain - 1] = basic.diskBasicParam.getTextTerminateCode();
                    }
                } else {
                    istream.read(buffer, 0, remain);
                }
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
        // 反転
        basic.invertMem(buffer, size);

        return len;
    }
}
