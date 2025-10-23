/*
 * Sasaji (translated automatically)
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * Frost-DOSの処理
 * <p>
 * DiskBasicParam
 *
 * @li ReservedGroups : Group 予約済みにするグループ（クラスタ）番号
 */
public class DiskBasicTypeFROST extends DiskBasicTypeFAT8 {

    // Assuming FROST_GROUP_SIZE is a defined constant (e.g., in Common.java or BasicCommon.java)
    private static final int FROST_GROUP_SIZE = 512; // Example value, assuming 512 bytes per group/sector size

    public DiskBasicTypeFROST(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /**
     * FAT位置をセット
     *
     * @param num グループ番号(0...)
     * @param val 値
     */
    @Override
    public void setGroupNumber(int num, int val) {
        // 16bit
        fat.getDiskBasicFatArea().setData16BE(num, convTrackSectorFromSectorPos(val));
    }

    /**
     * FAT位置を返す
     *
     * @param num グループ番号(0...)
     */
    @Override
    public int getGroupNumber(int num) {
        // 16bit
        return convSectorPosFromTrackSector(fat.getDiskBasicFatArea().getData16BE(0, num));
    }

    /**
     * 次の空きFAT位置を返す
     *
     * @param curr_group グループ番号(0...)
     * @return INVALID_GROUP_NUMBER 空きなし
     */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        int new_num = INVALID_GROUP_NUMBER;
        // 現在の番号と連続するように検索
        boolean found = false;
        for (int i = 0; i < 2 && !found; i++) {
            int sgnum = (i == 0 ? curr_group + 1 : 0);
            for (int gnum = sgnum; gnum <= basic.getFatEndGroup(); gnum++) {
                int next_gnum = getGroupNumber(gnum);
                if (next_gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                    new_num = gnum;
                    found = true;
                    break;
                }
            }
        }
        return new_num;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param is_formatting フォーマット中か
     * @return 1.0       正常
     * 0.0 - 1.0 警告あり
     * <0.0      エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        // １トラック当たりのグループ数を計算する
        if (basic.diskBasicParam.getGroupsPerTrack() == 0) {
            // 512バイトを１グループとして計算する
            int cnt = 0;
            DiskImageTrack track = basic.getTrack(1, 0);
            if (track != null) {
                List<DiskImageSector> secs = track.getSectors();
                if (secs != null) {
                    for (int sec = 0; sec < secs.size(); sec++) {
                        int siz = secs.get(sec).getSectorSize();
                        cnt += (siz / FROST_GROUP_SIZE);
                    }
                }
            }
            if (cnt == 0) {
                cnt = 11;
            }
            basic.diskBasicParam.setGroupsPerTrack(cnt);
        }
        // １セクタ当たりのグループ数
        int grps_per_sec = (basic.diskBasicParam.getGroupsPerTrack() + basic.getSectorsPerTrackOnBasic() - 1) / basic.getSectorsPerTrackOnBasic();
        basic.diskBasicParam.setGroupsPerSector(grps_per_sec);

        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int end_group = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerTrack();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }

        return 1.0;
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
        double valid_ratio = super.checkFat(is_formatting);
        if (valid_ratio >= 0.0) {
            // FAT,ディレクトリエリアはシステム予約となっているか
            List<Integer> groups = basic.diskBasicParam.getReservedGroups(); // Assuming List<Integer> is the Java equivalent of wxArrayInt
            for (int grp_val : groups) {
                int grp = getGroupNumber(grp_val);
                if (grp != basic.diskBasicParam.getGroupSystemCode()) {
                    valid_ratio = -1.0;
                    break;
                }
            }
        }
        return valid_ratio;
    }

    /**
     * 使用可能なディスクサイズを得る
     *
     * @param disk_size  ディスクサイズ (Output parameter via an array or wrapper)
     * @param group_size グループ数 (Output parameter via an array or wrapper)
     */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        if (group_size.length > 0) {
            group_size[0] = 0;
        }
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum != basic.diskBasicParam.getGroupSystemCode()) group_size[0]++;
        }
        if (disk_size.length > 0) {
            disk_size[0] = group_size[0] * basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
        }
    }

    /**
     * 残りディスクサイズを計算
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.clear();

        // 使用済みかチェック
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int fsize = 0;
            int grps = 0;
            int gnum = getGroupNumber(pos);
            int fsts = FAT_AVAIL_USED.getValue();
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                fsize = (basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector());
                grps = 1;
                fsts = FAT_AVAIL_FREE.getValue();
            } else if (gnum == basic.diskBasicParam.getGroupSystemCode()) {
                fsts = FAT_AVAIL_SYSTEM.getValue();
            } else if (gnum >= basic.diskBasicParam.getGroupFinalCode()) {
                fsts = FAT_AVAIL_USED_LAST.getValue();
            }
            // Assuming FatAvailability is a simple class/struct to hold the values
            fatAvailability.add(fsts, fsize, grps);
        }

//	free_disk_size = (int)fsize;
//	free_groups = (int)grps;
    }

    /**
     * 未使用が連続している位置をさがす
     */
    public int findContinuousArea(int group_size) {
        // 未使用が連続している位置をさがす
        int group = INVALID_GROUP_NUMBER;
        int group_start = INVALID_GROUP_NUMBER;
        int cnt = 0;
        for (int gnum = 0; gnum <= basic.getFatEndGroup() && cnt < group_size; gnum++) {
            if (getGroupNumber(gnum) == basic.diskBasicParam.getGroupUnusedCode()) {
                if (cnt == 0) {
                    group_start = gnum;
                }
                cnt++;
            } else {
                cnt = 0;
            }
        }
        if (cnt == group_size) {
            group = group_start;
        }
        return group;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileunit_num ファイル番号
     * @param item         ディレクトリアイテム
     * @param data_size    確保するデータサイズ（バイト）
     * @param flags        新規か追加か (Assuming AllocateGroupFlags is an enum/constant class)
     * @param group_items  確保したセクタリスト
     * @return >0:正常 -1:空きなし(開始グループ設定前) -2:空きなし(開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) throws IOException {
        int groups = 0;

        // FAT
        int rc = 0;
        boolean first_group = (flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW);
        int sizeremain = data_size;

        int bytes_per_group = basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
        int group_size = (data_size + bytes_per_group - 1) / bytes_per_group;
        // 連続して確保できる領域
        int group_num = findContinuousArea(group_size);
        if (group_num == INVALID_GROUP_NUMBER) {
            group_num = getEmptyGroupNumber();
        }
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeremain > 0) {
            if (group_num == INVALID_GROUP_NUMBER) {
                // 空きなし
                rc = first_group ? -1 : -2;
                break;
            }
            // 位置を予約
            setGroupNumber(group_num, basic.diskBasicParam.getGroupFinalCode());

            // グループ番号の書き込み
            if (first_group) {
                item.setStartGroup(fileunit_num, group_num);
                first_group = false;
            }

            // 次の空きグループをさがす
            int next_group_num = getNextEmptyGroupNumber(group_num);

            // 次の空きがない場合 or 残りサイズがこのグループで収まる場合
            if (next_group_num == INVALID_GROUP_NUMBER || sizeremain <= bytes_per_group) {
                // 最後のグループ番号
                next_group_num = calcLastGroupNumber(next_group_num, new int[] {sizeremain}); // sizeremain is passed as a mutable array
            }

            basic.getNumsFromGroup(group_num, next_group_num, basic.getSectorSize(), sizeremain, group_items);

            // グループ番号設定
            setGroupNumber(group_num, next_group_num);

            group_num = next_group_num;

            sizeremain -= bytes_per_group;
            groups++;

            limit--;
        }
        if (limit < 0) {
            // too large or infinit loop
            rc = first_group ? -1 : -2;
        }

        if (rc >= 0) {
            if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
                // 追加のときはチェインをつなぐ
                if (group_items.count() > 0) {
                    rc = chainGroups(item.getStartGroup(0), group_items.item(0).group);
                }
            }
        } else {
            // グループを削除
            deleteGroups(group_items);
            rc = -1;
        }

        return rc;
    }

    /**
     * グループ番号から開始セクタ番号を得る
     *
     * @param group_num グループ番号
     * @return 開始セクタ番号
     */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    /**
     * トラック＋セクタ番号から論理セクタ番号を得る
     */
    public int convSectorPosFromTrackSector(int trk_sec) {
        if (trk_sec == basic.diskBasicParam.getGroupUnusedCode() || trk_sec == basic.diskBasicParam.getGroupFinalCode() || trk_sec == basic.diskBasicParam.getGroupSystemCode()) {
            return trk_sec;
        }

        return (trk_sec >> 8) * basic.diskBasicParam.getGroupsPerTrack() + (trk_sec & 0xff) - 1;
    }

    /**
     * 論理セクタ番号からトラック＋セクタ番号を得る
     */
    public int convTrackSectorFromSectorPos(int pos) {
        if (pos == basic.diskBasicParam.getGroupUnusedCode() || pos == basic.diskBasicParam.getGroupFinalCode() || pos == basic.diskBasicParam.getGroupSystemCode()) {
            return pos;
        }

        return ((pos / basic.diskBasicParam.getGroupsPerTrack()) << 8) + (pos % basic.diskBasicParam.getGroupsPerTrack()) + 1;
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る
     * セクタ位置は、機種によらずトラック0,サイド0,セクタ1を0とした通し番号
     *
     * @param sector_pos セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)
     * @param track_num  トラック番号 (Output parameter)
     * @param side_num   サイド番号 (Output parameter)
     * @param sector_num セクタ番号 (Output parameter)
     * @param div_num    分割番号 (Output parameter, can be null)
     * @param div_nums   分割数 (Output parameter, can be null)
     */
    @Override
    public void getNumFromSectorPos(int sector_pos, int[] track_num, int[] side_num, int[] sector_num, int[] div_num, int[] div_nums) {
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();

        int grps_per_sec = basic.diskBasicParam.getGroupsPerSector();

        // 2D, 2HD
        int trksid_num = sector_pos / groups_per_track;
        track_num[0] = trksid_num / sides_per_disk;
        side_num[0] = trksid_num % sides_per_disk;
        sector_num[0] = ((sector_pos % groups_per_track) / grps_per_sec);
        if (div_num != null) div_num[0] = ((sector_pos % groups_per_track) % grps_per_sec);

        if (sector_num[0] * grps_per_sec > groups_per_track) {
            grps_per_sec = grps_per_sec - (sector_num[0] * grps_per_sec - groups_per_track);
        }

        track_num[0] += basic.getTrackNumberBaseOnDisk();
        side_num[0] += basic.getSideNumberBaseOnDisk();
        sector_num[0] += basic.getSectorNumberBaseOnDisk();

        if (div_nums != null) div_nums[0] = grps_per_sec;
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     * セクタ位置は、機種によらずトラック0,サイド0,セクタ1を0とした通し番号
     *
     * @param track_num  トラック番号
     * @param side_num   サイド番号
     * @param sector_num セクタ番号
     * @param div_num    分割番号
     * @param div_nums   分割数
     * @return セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)
     */
    @Override
    public int getSectorPosFromNum(int track_num, int side_num, int sector_num, int div_num, int div_nums) {
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int sector_pos;

        track_num -= basic.getTrackNumberBaseOnDisk();
        side_num -= basic.getSideNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBaseOnDisk();

        // 2D, 2HD
        sector_pos = (track_num * sides_per_disk + side_num) * groups_per_track;
        // The original C++ uses `sector_num * div_nums + div_num`
        // which seems to calculate the offset within the track in terms of groups,
        // considering sector-group division.
        sector_pos += (sector_num * div_nums + div_num);

        return sector_pos;
    }

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    /**
     * セクタデータを埋めた後の個別処理
     * フォーマット FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT トラック０はシステム
        int egnum = basic.diskBasicParam.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic() - 1;
        for (int gnum = 0; gnum < egnum; gnum++) {
            setGroupNumber(gnum, basic.diskBasicParam.getGroupSystemCode());
        }
        // FAT FAT, DIRエリアはシステム
        List<Integer> arr = basic.diskBasicParam.getReservedGroups();
        for (int gnum : arr) {
            setGroupNumber(gnum, basic.diskBasicParam.getGroupSystemCode());
        }
        return true;
    }

    /**
     * グループ確保時に最後のグループ番号を計算する
     *
     * @param group_num   現在のグループ番号
     * @param size_remain 残りのデータサイズ (mutable via int array)
     * @return 最後のグループ番号
     */
    @Override
    public int calcLastGroupNumber(int group_num, int[] size_remain) {
        return basic.diskBasicParam.getGroupFinalCode();
    }

    /**
     * データの書き込み処理
     *
     * @param item       ディレクトリアイテム
     * @param istream    ストリームデータ
     * @param buffer     セクタ内の書き込み先バッファ
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
    public int writeFile(DiskBasicDirItem item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws java.io.IOException {
        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                // Read up to 'remain' bytes
                len = istream.read(buffer, 0, remain);
                if (len < 0) len = 0; // Handle end of stream unexpectedly
            }
            if (size > len) {
                // バッファの余りは0サプレス (Fill remaining with 0s)
                java.util.Arrays.fill(buffer, len, size, (byte) 0);
            }
        } else {
            // 継続
            // Read 'size' bytes
            len = istream.read(buffer, 0, size);
            if (len < 0) len = 0;
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // タイトル名 FATエリア
        int[] div_num = new int[1];
        int[] div_nums = new int[1];
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1 + 3, null, null, null, div_num, div_nums);
        if (sector != null) {
            byte[] buf = sector.getSectorBuffer();
            int offset = sector.getSectorSize() * div_num[0] / div_nums[0] + 0x140;

            if (offset < buf.length && buf[offset] >= 0x20 && (buf[offset] & 0xFF) < 0xff) {
                // Java equivalent of wxString dst;
                String dst = new String(buf, offset, 64, basic.getCharCodes().charset());
                data.setVolumeName(dst);
            }
        }
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        // Implementation for setting identified data, if needed.
    }
}
