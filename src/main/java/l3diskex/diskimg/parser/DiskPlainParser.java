///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParam.TrackParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/**
 * べたディスクパーサー
 */
public class DiskPlainParser extends DiskImageParser {

    @Override
    public boolean isSupported(String type) {
        return "plain".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /**
     * インターリーブの解析
     *
     * @param track        トラック
     * @param interleave   インターリーブ
     * @param sectorOffset セクタオフセット
     */
    protected void parseInterleave(DiskImageTrack track, int interleave, int sectorOffset) {
        if (track == null) return;

        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null) {
            return;
        }
        int count = sectors.size();

        List<Integer> sectorNums = new ArrayList<>();
        for (DiskImageSector sector : sectors) {
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

    /**
     * セクタデータの解析
     *
     * @param istream      入力ディスクイメージ
     * @param diskNumber   ディスク番号
     * @param diskParam    ディスクパラメータ
     * @param trackNumber  トラック番号
     * @param sideNumber   サイド番号
     * @param sectorNumber セクタ番号
     * @param sectorNums   セクタ数
     * @param sectorSize   セクタサイズ
     * @param isDummy      ダミーセクタか(0でパディングするか)
     * @param track        [in,out] トラック
     * @return 作成したセクタのサイズ（ヘッダ含む）
     */
    protected int parseSector(InputStream istream, int diskNumber, DiskParam diskParam, int trackNumber, int sideNumber, int sectorNumber, int[] sectorNums, int[] sectorSize, boolean isDummy, DiskImageTrack track) throws IOException {
        // 特殊なセクタにするか
        int[][] sectorId = new int[1][];
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
            int len = istream.readNBytes(buf, 0, siz);
            if (len == 0) {
                // ファイルデータが足りない
                //result.setError(DiskResult.ERRV_INVALID_DISK, 0);
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

    /**
     * トラックデータの解析
     *
     * @param istream     入力ディスクイメージ
     * @param offsetPos   オフセット位置(index)
     * @param offset      オフセットバイト
     * @param diskNumber  ディスク番号
     * @param diskParam   ディスクパラメータ
     * @param trackNumber トラック番号
     * @param sideNumber  サイド番号
     * @param isDummySide ダミーサイドか
     * @param disk        [in,out] ディスク
     * @return 作成したトラックのサイズ
     */
    protected int parseTrack(InputStream istream, int offsetPos, int offset, int diskNumber, DiskParam diskParam, int trackNumber, int sideNumber, boolean isDummySide, DiskImageDisk disk) throws IOException {
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, 1);
        disk.setMaxTrackNumber(trackNumber);

        int[] sectorNums = {diskParam.getSectorsPerTrack()};
        int[] sectorSize = {diskParam.getSectorSize()};

        // 特殊なトラックならセクタ番号＆サイズを得る
        diskParam.findParticularTrack(trackNumber, sideNumber, sectorNums, sectorSize);

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

    /**
     * ディスクデータの解析
     *
     * @param iStream    入力ディスクイメージ
     * @param diskNumber ディスク番号
     * @param diskParam  ディスクパラメータ
     * @return オフセット
     */
    protected int parseDisk(InputStream iStream, int diskNumber, DiskParam diskParam) throws IOException {
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // パラメータの計算値がディスクサイズの２倍なら
        // 表面にのみデータをセットする
        int dummySide = -1;
        int streamLength = iStream.available();
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
                offset += parseTrack(iStream, offsetPos, offset, diskNumber, diskParam, trackNum, sideNum, sideNum == dummySide, disk);
                offsetPos++;
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
        }

        return offset;
    }

    /**
     * ベタファイルを解析
     *
     * @param iStream   解析対象データ
     * @param diskParam ディスクパラメータ
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        // パラメータ
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        parseDisk(iStream, 0, diskParam);

        return result.getValid();
    }

    @Override
    public int check(InputStream iStream) {
        return -1;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        int rc = 0;
        int streamSize = iStream.available(); // TODO assume available as length

        // パラメータで判断
        if (diskParam != null) {
            // 特定している
            diskParams.add(diskParam);
            return rc;
        }

        if (diskHints != null) {
            // パラメータヒントあり

            // 優先順位の高い候補
            for (DiskTypeHint diskHint : diskHints) {
                String hint = diskHint.getHint();
                DiskParam param = gDiskTemplates.find(hint);
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
                    if (diskParams.contains(param)) {
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
            calcParamFromSize(streamSize, manualParam);
        }

        // GUIで選択ダイアログを表示させる
        rc = 1;

        return rc;
    }

    // セクタサイズヒント
    private static final int[] secSizeHints = {
            256, 128, 0
    };
    // セクタ数ヒント
    private static final int[] secs256 = {10, 16, 18, 0};
    private static final int[] secs512 = {9, 10, 0};
    private static final int[] secs1024 = {4, 5, 0};
    private static final int[][] secsHint = {
            secs256,
            secs512,
            secs1024,
    };
    private static final int[] tracks = {80, 77, 40, 35, 512, 511, 256, 255, 128, 127, 64, 63, 0};

    /** ディスクサイズから尤もらしいパラメータを計算する */
    protected void calcParamFromSize(int diskSize, DiskParam diskParam) {
        // トラック数はディスクサイズで
        int maxTracks = 41;
        int minTracks = 40;
        if (diskSize > 1_000_000) {
            // 2HD?
            maxTracks = 82;
            minTracks = 80;
        } else if (diskSize > 500_000) {
            // 2DD?
            maxTracks = 82;
            minTracks = 80;
        }

        int value = 0;
        int desidedSecSizeIdx = -1;
        int desidedAllSectors = 0;

        for (int secSizeIdx = 0; secSizeHints[secSizeIdx] != 0; secSizeIdx++) {
            // セクタサイズで割る
            value = diskSize % secSizeHints[secSizeIdx]; // 余り
            if (value == 0) {
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
        if (sectors != null) {
            for (int sides = 1; sides <= 2 && !desided; sides++) {
                for (int tracks = minTracks; tracks <= maxTracks && !desided; tracks++) {
                    for (int ss = 0; sectors[ss] != 0 && !desided; ss++) {
                        value = (tracks * sides * sectors[ss]);
                        if (desidedAllSectors <= value) {
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
                value = desidedAllSectors % sides;
                if (value == 0) {
                    desidedSides = sides;
                    desidedAllSectors = desidedAllSectors / sides;
                    break;
                }
            }
            // トラック数で割る
            for (int t = 0; tracks[t] != 0; t++) {
                value = desidedAllSectors % tracks[t];
                if (value == 0) {
                    desidedTracks = tracks[t];
                    desidedSectors = desidedAllSectors / tracks[t];
                    break;
                }
            }
            if (value != 0) {
                // セクタ数で割る
                for (int s = 32; s >= 2; s--) {
                    value = desidedAllSectors % s;
                    if (value == 0) {
                        desidedTracks = desidedAllSectors / s;
                        desidedSectors = s;
                        break;
                    }
                }
            }
            if (value != 0) {
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
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
