package l3diskex.diskimg;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskWriter.DiskImageWriter;
import vavi.io.SeekableDataOutputStream;


/**
 *  べたディスクライター
 */
public class DiskPlainWriter extends DiskImageWriter {

    /**
     *  PRIVATE HELPERS
     */

    /**
     *  ディスク1つを保存
     *
     *  @param disk        ディスク1つのイメージ
     *  @param sideNumber  サイド番号(0-) / -1のときは両面
     *  @param ostream     出力先
     *  @return 0 正常, -1 エラー
     */
    private int SaveDisk(DiskImageDisk disk, int sideNumber, SeekableDataOutputStream ostream) {
        if (disk == null) {
            p_result.setError(DiskResult.ERR_NO_DISK);
            return p_result.getValid();
        }

        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        int trackStart  = sideNumber < 0 ? 0 : sideNumber;
        int trackCount  = tracks.size();
        int trackStep   = sideNumber < 0 ? 1 : 2;

        for (int trackNum = trackStart; trackNum < trackCount; trackNum += trackStep) {
            DiskImageTrack track = tracks.get(trackNum);
            if (track == null) continue;

            List<DiskImageSector> sectors = track.getSectors();
            if (sectors == null) continue;

            /* セクタ番号順に出力する */
            List<DiskImageSector> sortedSectors = new ArrayList<>(sectors);
            sortedSectors.sort(Comparator.comparingInt(DiskImageSector::getIDR));

            for (int idx = 0; idx < sortedSectors.size(); idx++) {
                DiskImageSector sector = sortedSectors.get(idx);
                if (sector == null) continue;

                byte[] buffer = sector.getSectorBuffer();
                int bufferSize = sector.getSectorBufferSize();
                if (buffer != null && bufferSize > 0) {
                    try {
                        ostream.write(buffer, 0, bufferSize);
                    } catch (IOException e) {
                        p_result.setError(DiskResult.ERR_NO_DATA);
                        return p_result.getValid();
                    }
                }
                // sector.clearModify();   // ignored in this simplified model
            }
        }

        // disk.clearModify();          // ignored
        return p_result.getValid();
    }

    /**
     *  PUBLIC API
     */

    /**
     * コンストラクタ
     *
     * @param dw_      DiskWriter
     * @param result_  DiskResult
     */
    public DiskPlainWriter(DiskWriter dw_, DiskResult result_) {
        super(dw_, result_);
    }

    /**
     * べたイメージでファイルに保存
     *
     * @param image        ディスクイメージ
     * @param diskNumber   ディスク番号(0-) / -1のときは全体
     * @param sideNumber   サイド番号(0-) / -1のときは両面
     * @param ostream      出力先
     * @return 0 正常, -1 エラー
     */
    public int SaveDisk(DiskImage image, int diskNumber, int sideNumber,
                        SeekableDataOutputStream ostream) {
        p_result.clear();

        DiskImageFile file = image.getFile();
        if (file == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        if (diskNumber < 0) {                         // 1枚だけ保存
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                p_result.setError(DiskResult.ERR_NO_DISK);
                return p_result.getValid();
            }
            for (int diskNum = 0; diskNum < 1; diskNum++) {
                DiskImageDisk disk = disks.get(diskNum);
                SaveDisk(disk, -1, ostream);
            }
        } else {                                      // 指定ディスク保存
            DiskImageDisk disk = file.getDisk(diskNumber);
            SaveDisk(disk, sideNumber, ostream);
        }

        return p_result.getValid();
    }
}
