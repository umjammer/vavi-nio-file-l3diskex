/*
 * Author: Sasaji (translated to Java)
 */

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Serdes;


/**
 * IMageDisk IMD format disk image parser.
 *
 * @see "http://dunfield.classiccmp.org//img42841/readme.txt"
 * @see "https://oldcomputers-ddns.org/public/pub/manuals/imd.pdf"
 */
public class DiskIMDParser extends DiskImageParser {

    /** IMD track header (packed 1 byte each) */
    private static class ImdTrackHeader {

        int mode;
        int track_num;
        int head_num_n_flg;
        int num_of_sectors;
        int sector_size_n;

        public static final int SIZE = 5;
    }

    @Override
    public boolean isSupported(String type) {
        return "imd".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /**
     * セクタデータの作成
     *
     * @param istream       ディスクイメージ
     * @param diskNumber    ディスク番号
     * @param trackNumber   トラック番号
     * @param sideNumber    サイド番号
     * @param numOfSectors  セクタ数
     * @param sectorNumber  セクタ番号
     * @param sectorSize    セクタサイズ
     * @param singleDensity 単密度か
     * @param track         [in,out] トラック
     * @return ヘッダ込みのセクタサイズ
     */
    private int parseSector(InputStream istream,
                            int diskNumber,
                            int trackNumber,
                            int sideNumber,
                            int numOfSectors,
                            int sectorNumber,
                            int sectorSize,
                            boolean singleDensity,
                            DiskImageTrack track) throws IOException {
        int r = istream.read();
        if (r == -1) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        int h_sector = r & 0xff;

        // Create sector
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, false, 0);
        track.add(sector);

        byte[] buffer = sector.getSectorBuffer();

        if (h_sector == 0) {
            // missing data
            sector.fill((byte) 0);
        } else {
            h_sector--;
            if ((h_sector & 1) != 0) {
                // compressed data
                int ch = istream.read();
                if (ch == -1) ch = 0;
                sector.fill((byte) (ch & 0xFF));
            } else {
                // plain data
                istream.readNBytes(buffer, 0, sectorSize);
            }
            if ((h_sector & 0x02) != 0) {
                // deleted data
                sector.setDeletedMark(true);
            }
            if ((h_sector & 0x04) != 0) {
                // TODO: bad sector
            }
        }

        sector.setSingleDensity(singleDensity);
        sector.clearModify();

        // Return sector size
        return sector.getSize();
    }

    /**
     * トラックデータの作成
     *
     * @param istream    ディスクイメージ
     * @param diskNumber ディスク番号
     * @param offsetPos  オフセット番号
     * @param offset     オフセット位置
     * @param disk       [in,out] ディスク
     * @return -1:エラー or 終り >0:トラックサイズ
     */
    private int parseTrack(InputStream istream,
                           int diskNumber,
                           int offsetPos,
                           int offset,
                           DiskImageDisk disk) throws IOException {
        int len = istream.available();
        if (len < ImdTrackHeader.SIZE) {
            // end of file
            return -1;
        }
        ImdTrackHeader hTrack = new ImdTrackHeader();
        Serdes.Util.deserialize(istream, hTrack);
        if (hTrack.mode > 5) {
            result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
            return -1;
        }
        if ((hTrack.head_num_n_flg & 0x0F) > 1) {
            result.setError(DiskResult.ERRV_ID_SIDE, diskNumber,
                    hTrack.track_num, hTrack.track_num, hTrack.head_num_n_flg & 0x0f, 1);
            return -1;
        }
        if (hTrack.sector_size_n > 6) {
            result.setError(DiskResult.ERRV_SECTOR_SIZE_HEADER, diskNumber, hTrack.sector_size_n);
            return -1;
        }
        int sectorSize = 128 << hTrack.sector_size_n;

        // Sector, track and head maps
        byte[] sectorMap = new byte[hTrack.num_of_sectors];
        byte[] trackMap = new byte[hTrack.num_of_sectors];
        byte[] headMap = new byte[hTrack.num_of_sectors];
        if (hTrack.num_of_sectors > 0) {
            // read sector map
            len = istream.readNBytes(sectorMap, 0, hTrack.num_of_sectors);
            if (len != hTrack.num_of_sectors) {
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
                return -1;
            }
            if ((hTrack.head_num_n_flg & 0x80) != 0) {
                // cylinder map (track map)
                len = istream.readNBytes(trackMap, 0, hTrack.num_of_sectors);
                if (len != hTrack.num_of_sectors) {
                    result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
                    return -1;
                }
            } else {
                Arrays.fill(trackMap, (byte) hTrack.track_num);
            }
            if ((hTrack.head_num_n_flg & 0x40) != 0) {
                // read head map
                len = istream.readNBytes(headMap, 0, hTrack.num_of_sectors);
                if (len != hTrack.num_of_sectors) {
                    result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
                    return -1;
                }
            } else {
                Arrays.fill(headMap, (byte) (hTrack.head_num_n_flg & 0x0F));
            }
        }
        if (sectorSize * hTrack.num_of_sectors > 32768) {
            result.setError(DiskResult.ERRV_DISK_TOO_LARGE, diskNumber);
            return -1;
        }

        // Create track
        DiskImageTrack track = disk.newImageTrack(hTrack.track_num, hTrack.head_num_n_flg & 0x0f, offsetPos, 1);
        disk.setMaxTrackNumber(hTrack.track_num);

        int d88TrackSize = 0;
        for (int pos = 0; pos < hTrack.num_of_sectors && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(istream, diskNumber, trackMap[pos] & 0xff, headMap[pos] & 0xff,
                    hTrack.num_of_sectors, sectorMap[pos] & 0xff, sectorSize, hTrack.mode <= 2, track);
        }

        if (result.getValid() >= 0) {
            // インターリーブの計算
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // トラックサイズ設定
            track.setSize(d88TrackSize);
            // サイド番号は各セクタのID Hに合わせる
            track.setSideNumber(track.getMajorIDH());

            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offsetPos, offset);
        }

        return d88TrackSize;
    }

    /**
     * IMDファイルを解析
     *
     * @param iStream    解析対象データ
     * @param diskNumber ディスク番号
     * @retval -1: finish parsing
     * @retval 0: parse next disk
     */
    private int parseDisk(InputStream iStream, int diskNumber) throws IOException {
        // skip comment line at the start of the stream
        int ch = 0;
        while (ch != 0x1a && ch != -1) {
            ch = iStream.read();
        }
        if (ch == -1) return -1;

        // ディスク作成
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // トラック解析
        int d88Offset = disk.getOffsetStart();   // header size
        int d88OffsetPos = 0;
        int limitOffsetPos = disk.getCreatableTracks();
        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(iStream, diskNumber, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                break;
            }
            d88Offset += offset;

            d88OffsetPos++;
            if (d88OffsetPos >= limitOffsetPos) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, diskNumber, d88Offset);
            }
        }

        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            DiskParam diskParam = disk.calcMajorNumber();
            // ディスクを追加
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return 0;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> hints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * チェック
     *
     * @param iStream 解析対象データ
     * @return 1: 選択ダイアログ表示, 0: 正常（候補が複数ある時はダイアログ表示）
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        byte[] header = new byte[31];
        int len = iStream.readNBytes(header, 0, 31);
        if (len < header.length) {
            // too short
            return -1;
        }
        if (!Arrays.equals(Arrays.copyOfRange(header, 0, 4), "IMD ".getBytes())) {
            // not IMD image
            return -1;
        }
        if (header[4] != '1' || header[5] != '.') {
            // support only version 1.xx
            return -1;
        }

        // check comment line on head of stream
        int ch = 0;
        while (ch != 0x1a && ch != -1) {
            ch = iStream.read();
        }
        if (ch == -1) return -1;

        return 0;
    }

    /**
     * IMDファイルを解析
     *
     * @param iStream   解析対象データ
     * @param diskParam パラメータ通常不要
     * @return 0: 正常m -1: エラーあり, 1: 警告あり
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        for (int diskNumber = 0; diskNumber < 1; diskNumber++) {
            if (parseDisk(iStream, diskNumber) < 0) {
                break;
            }
        }

        return result.getValid();
    }
}
