///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
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
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/**
 * TRS-80 JV3 ディスクパーサー
 *
 * TRS-80 is the PC by one of 1977 Trinity（Apple, Commodore, Tandy)
 *
 * @see "https://www.tim-mann.org/trs80/dskspec.html"
 */
public class DiskJV3Parser extends DiskImageParser {

    /** 1=dden, 0=sden */
    private static final byte JV3_DENSITY = (byte) 0x80;
    /** 0=side 0, 1=side 1 */
    private static final byte JV3_SIDE = (byte) 0x10;
    /** in used sectors: 0=256,1=128,2=1024,3=512 */
    private static final byte JV3_SIZE = (byte) 0x03;

    /** in track and sector fields of free sectors */
    private static final byte JV3_FREE = (byte) 0xff;

    private static final byte JV3_WRITABLE = (byte) 0xff;
    private static final byte JV3_WPROTECT = (byte) 0x00;

    private static final short[] jv3SizeMap = {1, 0, 3, 2};

    /**
     * セクタ番号情報
     */
    @Serdes
    public static class Jv3SectorId {

        @Element(sequence = 1)
        public byte trackNumber;
        @Element(sequence = 2)
        public byte sectorNumber;
        @Element(sequence = 3)
        public byte flags;

        public static final int SIZE = 3;
    }

    /**
     * ヘッダ情報
     */
    @Serdes
    public static class Jv3Header {

        @Element(sequence = 1)
        public Jv3SectorId[] ids = new Jv3SectorId[2901];
        @Element(sequence = 2)
        public byte writeProtected;

        // Total size: 2901 * 3 + 1
        public static final int SIZE = 2901 * Jv3SectorId.SIZE + 1;

        public Jv3Header() {
            for (int i = 0; i < 2901; i++) {
                ids[i] = new Jv3SectorId();
            }
        }
    }

    //
    //
    //

    @Override
    public boolean isSupported(String type) {
        return "jv3".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /**
     * セクタデータの作成
     */
    private int parseSector(InputStream iStream, int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numOfSectors, boolean singleDensity, DiskImageTrack track) throws IOException {
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, singleDensity, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int size = sector.getSectorBufferSize();

        int len = iStream.read(buf, 0, size);
        if (len < size) {
            // ファイルデータが足りない
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }

        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /**
     * ディスクの解析
     *
     * @param iStream 解析対象データ
     * @return サイズ
     */
    private int parseDisk(InputStream iStream) throws IOException {
        Jv3Header header = new Jv3Header();

        DiskImageDisk disk = file.newImageDisk(0);
        int start = disk.getOffsetStart(); // header size
        int offsetPos = 0;
        int maxTrackNumber = -1;
        int limitOffsetPos = disk.getCreatableTracks();
        for (int diskPart = 0; diskPart < 2; diskPart++) {
            int len = iStream.available();
            if (len < Jv3Header.SIZE) {
                break;
            }
            Serdes.Util.deserialize(iStream, header);

            for (int i = 0; i < 2901; i++) {
                if (header.ids[i].trackNumber == JV3_FREE || header.ids[i].sectorNumber == JV3_FREE) {
                    continue;
                }
                int trackNumber = Byte.toUnsignedInt(header.ids[i].trackNumber);
                int sideNumber = (header.ids[i].flags & JV3_SIDE) != 0 ? 1 : 0;
                int sectorNumber = Byte.toUnsignedInt(header.ids[i].sectorNumber);
                int sectorSize = (128 << jv3SizeMap[header.ids[i].flags & JV3_SIZE]);
                boolean single_density = (header.ids[i].flags & JV3_DENSITY) == 0;

                // トラックが存在するか
                DiskImageTrack track = disk.getTrack(trackNumber, sideNumber);
                if (track == null) {
                    // 新規トラック
                    track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, 1);
                    disk.add(track);
                    offsetPos++;

                    if (offsetPos >= limitOffsetPos) {
                        result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, start);
                    }
                }
                int trackSize = track.getSize();
                // セクタ作成
                int newSectorSize = parseSector(iStream, trackNumber, sideNumber, sectorNumber, sectorSize, 1, single_density, track);
                // トラックサイズ更新
                track.setSize(trackSize + newSectorSize);
                start += newSectorSize;

                maxTrackNumber = Math.max(maxTrackNumber, trackNumber);
            }
        }
        // ディスクサイズ設定
        disk.setSize(start);
        // 最大トラック番号設定
        disk.setMaxTrackNumber(maxTrackNumber);

        if (result.getValid() >= 0) {
            start = disk.getOffsetStart();  // header size
            List<DiskImageTrack> tracks = disk.getTracks();
            for (int pos = 0; pos < tracks.size(); pos++) {
                DiskImageTrack track = tracks.get(pos);

                // インターリーブの計算
                track.calcInterleave();
                // サイズの再計算
                track.shrink(false);
                // オフセットの設定
                disk.setOffset(pos, start);

                start += track.getSize();
            }

            // ディスクを追加
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            disk.setWriteProtect(header.writeProtected == JV3_WPROTECT);
            disk.clearModify();

            file.add(disk, modFlags);
        }

        return start;
    }

    /**
     * ファイルを解析
     *
     * @param iStream 解析対象データ
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        result.clear();
        ((SeekableDataInputStream) iStream).position(0);

        parseDisk(iStream);

        return result.getValid();
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> hints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * チェック
     *
     * @param iStream 解析対象データ
     * @return 0: 正常, -1: 対象データではない
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        Jv3Header header = new Jv3Header();

        for (int diskPart = 0; diskPart < 2; diskPart++) {
            int len = iStream.available();
            if (diskPart > 0 && len == 0) {
                break;
            }
            if (len < Jv3Header.SIZE) {
                // too short
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
            Serdes.Util.deserialize(iStream, header);
            // 書き込み禁止エリア
            if (header.writeProtected != JV3_WRITABLE && header.writeProtected != JV3_WPROTECT) {
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }
            // ディスクサイズを計算
            int dataSize = 0;
            int errZero = 0;
            int errTracks = 0;
            int errSectors = 0;
            for (int i = 0; i < 2901; i++) {
                if (header.ids[i].trackNumber == JV3_FREE || header.ids[i].sectorNumber == JV3_FREE) {
                    continue;
                }
                // 全て０か
                if (header.ids[i].trackNumber == 0 && header.ids[i].sectorNumber == 0 && header.ids[i].flags == 0) {
                    errZero++;
                }
                // トラック番号が連続しているか
                if (i > 0) {
                    if (!((header.ids[i - 1].trackNumber & 0xff) == (header.ids[i].trackNumber & 0xff) || (header.ids[i - 1].trackNumber & 0xff) + 1 == (header.ids[i].trackNumber & 0xff))) {
                        errTracks++;
                    }
                }
                // トラック番号は80以内か
                if ((header.ids[i].trackNumber & 0xff) > 80) {
                    errTracks++;
                }
                // セクタ番号は32以内か
                if ((header.ids[i].sectorNumber & 0xff) > 32) {
                    errSectors++;
                }
                dataSize += 128 << jv3SizeMap[header.ids[i].flags & JV3_SIZE];
            }

            if (errZero >= 40) {
                // ゼロパディングなのでJV3形式ではない
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }
            if (errTracks >= 20) {
                // トラック番号がJV3形式ではない
                result.setError(DiskResult.ERRV_ID_TRACK, 0);
                return result.getValid();
            }
            if (errSectors >= 20) {
                // セクタ番号がJV3形式ではない
                result.setError(DiskResult.ERRV_ID_SECTOR, 0);
                return result.getValid();
            }

            int current = (int) ((SeekableDataInputStream) iStream).position();
            int fileOffset = current + dataSize;
            ((SeekableDataInputStream) iStream).position(fileOffset);
            if (fileOffset != dataSize || fileOffset < Jv3Header.SIZE + dataSize) {
                // ファイルサイズ足りない
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
        }

        return 0;
    }
}
