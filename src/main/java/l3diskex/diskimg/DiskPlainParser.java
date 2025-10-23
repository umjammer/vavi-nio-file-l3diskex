package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam.DiskParticulars;
import l3diskex.diskimg.DiskParam.TrackParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.FileParam.DiskTypeHint;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/**
 * べたディスクパーサー
 */
public class DiskPlainParser extends DiskImageParser {

    // Assuming DiskImageParser has:
    // DiskImageFile file;
    // short modFlags;
    // DiskResult result;

    // Constructors are often simpler in Java.
    // Assuming DiskImageParser has a constructor that takes DiskImageFile, short, and DiskResult
    public DiskPlainParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    // Java doesn't use destructors like C++. The finalizer can be used, but generally
    // a close() method is preferred for resources. Here, we'll just omit the destructor
    // or leave it as a simple method if resources need explicit release.
    // protected void finalize() throws Throwable {
    //     super.finalize();
    // }

    // C++ method: void ParseInterleave(DiskImageTrack *track, int interleave, int sector_offset);
    protected void parseInterleave(DiskImageTrack track, int interleave, int sectorOffset) {
        if (track == null) return;

        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null) {
            return;
        }
        int count = sectors.size();

        List<Integer> sectorNums = new ArrayList<>();
        for (int idx = 0; idx < count; idx++) {
            DiskImageSector sector = sectors.get(idx);
            sectorNums.add(sector.getSectorNumber());
        }

        List<Integer> sectorIdxs = new ArrayList<>();
        if (!DiskImageTrack.calcSectorNumbersForInterleave(interleave, count, sectorIdxs, 0)) {
            return;
        }

        for (int idx = 0; idx < count; idx++) {
            DiskImageSector sector = sectors.get(idx);
            if (sector != null) {
                sector.setSectorNumber(sectorNums.get(sectorIdxs.get(idx)));
            }
        }

        track.setInterleave(interleave);
    }

    // C++ method: wxUint32 ParseSector(wxInputStream &istream, int disk_number, const DiskParam *disk_param, int track_number, int side_number, int sector_number, int sector_nums, int sector_size, bool is_dummy, DiskImageTrack *track);
    protected int parseSector(InputStream istream, int diskNumber, DiskParam diskParam, int trackNumber, int sideNumber, int sectorNumber, int[] sectorNums, int[] sectorSize, boolean isDummy, DiskImageTrack track) {
        // 特殊なセクタにするか
        int[][] sectorId = new int[1][]; // Equivalent to const wxUint16 *sector_id
        if (diskParam.findParticularSector(trackNumber, sideNumber, sectorNumber, sectorSize, sectorId)) {
            if ((sectorId[0][1] & TrackParam.ID_IS_VALID) != 0) {
                sideNumber = (sectorId[0][1] & ~TrackParam.ID_IS_VALID);
            }
            if ((sectorId[0][2] & TrackParam.ID_IS_VALID) != 0) {
                sectorNumber = (sectorId[0][2] & ~TrackParam.ID_IS_VALID);
            }
        }

        // 単密度か
        boolean singleDensity = diskParam.findSingleDensity(trackNumber, sideNumber, sectorNumber, sectorSize[0]);

        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize[0], sectorNums[0], singleDensity, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        if (!isDummy) {
            int len = 0;
            try {
                // Java's InputStream.read(byte[], offset, length) returns number of bytes read, or -1 for end of stream
                // We'll read directly into buf from 0 up to siz
                int totalRead = 0;
                while (totalRead < siz) {
                    int bytesRead = istream.read(buf, totalRead, siz - totalRead);
                    if (bytesRead == -1) break; // End of stream
                    totalRead += bytesRead;
                }
                len = totalRead;
            } catch (IOException e) {
                // Handle exception if necessary, but C++ original just checked length
            }

            if (len == 0 && siz > 0) {
                // ファイルデータが足りない
                // result->SetError(DiskResult::ERRV_INVALID_DISK, 0); // Assuming result is an instance variable
                // ので０パディング
                isDummy = true;
            }
        }
        if (isDummy) {
            // ダミーセクタ or 足りない分
            sector.fill((byte) 0);
        }

        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    // C++ method: wxUint32 ParseTrack(wxInputStream &istream, int offset_pos, wxUint32 offset, int disk_number, const DiskParam *disk_param, int track_number, int side_number, bool is_dummy_side, DiskImageDisk *disk);
    protected int parseTrack(InputStream istream, int offsetPos, int offset, int diskNumber, DiskParam diskParam, int trackNumber, int sideNumber, boolean isDummySide, DiskImageDisk disk) {
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, 1);
        disk.setMaxTrackNumber(trackNumber);

        int[] sectorNums = {diskParam.getSectorsPerTrack()};
        int[] sectorSize = {diskParam.getSectorSize()};

        // 特殊なトラックならセクタ番号＆サイズを得る
        diskParam.findParticularTrack(trackNumber, sideNumber, sectorNums, sectorSize);
        // Assuming getters on DiskParam retrieve the modified values if found

        // トラック全体が単密度の場合はセクタ数とサイズを得る
        diskParam.findSingleDensity(trackNumber, sideNumber, sectorNums, sectorSize);

        int trackSize = 0;
        int sectorOffset = diskParam.getSectorNumberBaseOnDisk();

        // セクタ番号の付番方法(0:サイド毎、1:トラック毎)
        if (diskParam.getNumberingSector() != 0) {
            sectorOffset += sideNumber * sectorNums[0];
        }

        for (int sectorNumber = 0; sectorNumber < sectorNums[0] && result.getValid() >= 0; sectorNumber++) {
            trackSize += parseSector(istream, diskNumber, diskParam, trackNumber, sideNumber, sectorNumber + sectorOffset, sectorNums, sectorSize, isDummySide, track);
        }

        if (result.getValid() >= 0) {
            // インターリーブ
            parseInterleave(track, diskParam.getInterleave(), sectorOffset);
            // トラックサイズ設定
            track.setSize(trackSize);
            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offsetPos, offset);
        }

        return trackSize;
    }

    // C++ method: virtual wxUint32 ParseDisk(wxInputStream &istream, int disk_number, const DiskParam *disk_param);
    protected int parseDisk(InputStream istream, int diskNumber, DiskParam diskParam) throws IOException {
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // パラメータの計算値がディスクサイズの２倍なら
        // 表面にのみデータをセットする
        int dummySide = -1;
        int streamLength = istream.available();
        if (streamLength * 2 <= diskParam.calcDiskSize()) {
            dummySide = diskParam.getSideNumberBaseOnDisk() + 1;
        }

        int offset = disk.getOffsetStart();
        int offsetPos = 0;
        int trackNum = diskParam.getTrackNumberBaseOnDisk();
        int tracksPerSide = diskParam.getTracksPerSide() + trackNum;
        int sideNumSt = diskParam.getSideNumberBaseOnDisk();
        int sideNumEd = diskParam.getSidesPerDisk() + sideNumSt;

        for (; trackNum < tracksPerSide && result.getValid() >= 0; trackNum++) {
            for (int sideNum = sideNumSt; sideNum < sideNumEd && result.getValid() >= 0; sideNum++) {
                // トラック作成
                offset += parseTrack(istream, offsetPos, offset, diskNumber, diskParam, trackNum, sideNum, sideNum == dummySide, disk);
                offsetPos++;
                // C++ block for DISKD88_MAX_TRACKS check omitted since it's commented out
            }
        }
        disk.setSize(offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam majorDiskParam = disk.calcMajorNumber();
            if (majorDiskParam != null) {
                disk.setDensity(majorDiskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        } else {
            // In C++ it was 'delete disk;'. In Java, we rely on GC.
        }

        return offset;
    }

    // C++ method: virtual int Parse(wxInputStream &istream, const DiskParam *disk_param);
    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        // パラメータ
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        parseDisk(istream, 0, diskParam);

        return result.getValid();
    }

    // C++ method: virtual int Check(wxInputStream &istream);
    @Override
    public int check(InputStream istream) {
        return -1;
    }

    // C++ method: virtual int Check(wxInputStream &istream, const DiskTypeHints *disk_hints, const DiskParam *disk_param, DiskParamPtrs &disk_params, DiskParam &manual_param);
    @Override
    public int check(InputStream istream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        int rc = 0;
        int streamSize = istream.available(); // Cast to int, assuming size is not too large for int (max 2GB)

        // パラメータで判断
        if (diskParam != null) {
            // 特定している
            diskParams.add(diskParam);
            return rc;
        }

        if (diskHints != null) {
            // パラメータヒントあり

            // 優先順位の高い候補
            for (int i = 0; i < diskHints.size(); i++) {
                String hint = diskHints.get(i).getHint();
                DiskParam param = gDiskTemplates.find(hint); // Assuming gDiskTemplates is a static or globally accessible object
                if (param != null) {
                    int diskSizeHint = param.calcDiskSize();
                    if (streamSize == diskSizeHint) {
                        // ファイルサイズが一致
                        diskParams.add(param);
                    }
                }
            }
        }

        // ディスクテンプレート全体から探す
        for (int mag = 1; mag <= 2; mag++) {
            boolean separator = (diskParams.isEmpty());
            for (int i = 0; i < gDiskTemplates.size(); i++) {
                DiskParam param = gDiskTemplates.get(i);
                if (param != null) {
                    // 同じ候補がある場合スキップ
                    if (diskParams.indexOf(param) >= 0) {
                        continue;
                    }

                    int diskSizeHint = param.calcDiskSize();
                    if (streamSize * mag == diskSizeHint) {
                        if (!separator) {
                            diskParams.add(null);
                            separator = true;
                        }
                        // ファイルサイズが一致
                        diskParams.add(param);
                    }
                }
            }
        }

        // 候補がないとき、ディスクサイズからパラメータを計算
        if (diskParams.isEmpty()) {
            calcParamFromSize(streamSize, manualParam); // manualParam is passed by reference/mutable object
        }

        // GUIで選択ダイアログを表示させる
        rc = 1;

        return rc;
    }

    // C++ method: void CalcParamFromSize(int disk_size, DiskParam &disk_param);
    protected void calcParamFromSize(int diskSize, DiskParam diskParam) {
        // セクタサイズヒント
        int[] secSizeHints = {
                256, 128, 0
        };
        // セクタ数ヒント
        int[] secs256 = {10, 16, 18, 0};
        int[] secs512 = {9, 10, 0}; // Note: 512 is the size, but C++ code uses this for sec_size_idx=1 (128 bytes/sector)
        int[] secs1024 = {4, 5, 0}; // Note: 1024 is the size, but C++ code uses this for sec_size_idx=2 (0 bytes/sector, which seems wrong, but following the C++ logic)
        // Correcting based on sec_size_hints: 0=256, 1=128, 2=0 (error in C++ logic for index 2, but following structure)
        // Re-analyzing C++: sec_size_hints are 256, 128, 0. The secs_hint array is indexed by sec_size_idx.
        // C++: const int secs256[] = {	10, 16, 18, 0 };
        // C++: const int secs512[] = {	9, 10, 0 };
        // C++: const int secs1024[] = { 4, 5, 0 };
        // This naming is confusing but I must follow the array assignment logic:
        int[][] secsHint = {
                secs256, // for secSizeHint 256
                secs512, // for secSizeHint 128
                secs1024, // for secSizeHint 0 (or a large size)
                null
        };

        // トラック数はディスクサイズで
        int maxTracks = 41;
        int minTracks = 40;
        if (diskSize > 1000000) {
            // 2HD?
            maxTracks = 82;
            minTracks = 80;
        } else if (diskSize > 500000) {
            // 2DD?
            maxTracks = 82;
            minTracks = 80;
        }

        int ival;
        int desidedSecSizeIdx = -1;
        int desidedAllSectors = 0;

        for (int secSizeIdx = 0; secSizeHints[secSizeIdx] != 0; secSizeIdx++) {
            // セクタサイズで割る
            ival = diskSize % secSizeHints[secSizeIdx];    // 余り
            if (ival == 0) {
                desidedSecSizeIdx = secSizeIdx;
                desidedAllSectors = diskSize / secSizeHints[secSizeIdx];
                break;
            }
        }
        if (desidedSecSizeIdx < 0) {
            // セクタサイズ候補なし
            return;
        }

        int desidedTracks = 0;
        int desidedSides = 0;
        int desidedSectors = 0;
        boolean desided = false;
        int[] sectors = secsHint[desidedSecSizeIdx];

        // Ensure sectors array is valid before loop
        if (sectors != null) {
            for (int sides = 1; sides <= 2 && !desided; sides++) {
                for (int tracks = minTracks; tracks <= maxTracks && !desided; tracks++) {
                    for (int ss = 0; sectors[ss] != 0 && !desided; ss++) {
                        ival = (tracks * sides * sectors[ss]);
                        if (desidedAllSectors <= ival) {
                            desidedTracks = tracks;
                            desidedSides = sides;
                            desidedSectors = sectors[ss];
                            desided = true;
                            break;
                        }
                    }
                }
            }
        }


        // 候補がない
        if (!desided) {
            // ディスクサイズで割り切れる値を候補にする
            for (int sides = 2; sides >= 1; sides--) {
                ival = desidedAllSectors % sides;
                if (ival == 0) {
                    desidedSides = sides;
                    desidedAllSectors = desidedAllSectors / sides;
                    break;
                }
            }
            // トラック数で割る
            int[] ctracks = {80, 77, 40, 35, 512, 511, 256, 255, 128, 127, 64, 63, 0};
            ival = desidedAllSectors; // Reset ival for the check
            for (int t = 0; ctracks[t] != 0; t++) {
                if (desidedAllSectors % ctracks[t] == 0) {
                    ival = desidedAllSectors % ctracks[t]; // Re-calculate to match C++ logic's use of ival
                    desidedTracks = ctracks[t];
                    desidedSectors = desidedAllSectors / ctracks[t];
                    break;
                }
            }
            if (ival != 0) {
                // セクタ数で割る
                for (int s = 32; s >= 2; s--) {
                    ival = desidedAllSectors % s; // Re-calculate to match C++ logic's use of ival
                    if (desidedAllSectors % s == 0) {
                        ival = 0; // Set ival to 0 for success
                        desidedTracks = desidedAllSectors / s;
                        desidedSectors = s;
                        break;
                    }
                }
            }
            if (ival != 0) {
                for (int s = 1; s <= 32; s++) {
                    if ((desidedAllSectors / s) < 10000) {
                        desidedTracks = desidedAllSectors / s;
                        desidedSectors = s;
                        break;
                    }
                }
            }
        }

        diskParam.setDiskParam(
                desidedSides,
                desidedTracks,
                desidedSectors,
                secSizeHints[desidedSecSizeIdx],
                0,
                1,
                new DiskParticulars(), // Assuming a default constructor for DiskParticulars
                new DiskParticulars()
        );
    }
}