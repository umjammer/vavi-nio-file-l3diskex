///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.writer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.DiskWriter;
import l3diskex.diskimg.DiskWriter.DiskImageWriter;


/**
 * べたディスクライター
 */
public class DiskPlainWriter extends DiskImageWriter {

    /**
     * ディスク1つを保存
     *
     * @param disk       ディスク1つのイメージ
     * @param sideNumber サイド番号(0-) / -1のときは両面
     * @param oStream    出力先
     * @return 0 正常, -1 エラー
     */
    private int saveDisk(DiskImageDisk disk, int sideNumber, OutputStream oStream) throws IOException {
        if (disk == null) {
            result.setError(DiskResult.ERR_NO_DISK);
            return result.getValid();
        }

        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        int trackStart = sideNumber < 0 ? 0 : sideNumber;
        int trackCount = tracks.size();
        int trackStep = sideNumber < 0 ? 1 : 2;

        for (int trackNum = trackStart; trackNum < trackCount; trackNum += trackStep) {
            DiskImageTrack track = tracks.get(trackNum);
            if (track == null) continue;
            List<DiskImageSector> sectors = track.getSectors();
            if (sectors == null) continue;
            // セクタ番号順に出力する
            List<DiskImageSector> sortedSectors = new ArrayList<>(sectors);
            sortedSectors.sort(Comparator.comparingInt(DiskImageSector::getIDR));
            for (DiskImageSector sector : sortedSectors) {
                if (sector == null) continue;

                // write sector body
                byte[] buffer = sector.getSectorBuffer();
                int bufferSize = sector.getSectorBufferSize();
                if (buffer != null && bufferSize > 0) {
                    oStream.write(buffer, 0, bufferSize);
                }
                //sector.clearModify();
            }
        }

        //disk.clearModify();
        return result.getValid();
    }

    //
    // べた形式で保存
    //
    public DiskPlainWriter(DiskWriter writer, DiskResult result) {
        super(writer, result);
    }

    /**
     * べたイメージでファイルに保存
     *
     * @param image      ディスクイメージ
     * @param diskNumber ディスク番号(0-) / -1のときは全体
     * @param sideNumber サイド番号(0-) / -1のときは両面
     * @param oStream    出力先
     * @return 0 正常, -1 エラー
     */
    @Override
    public int saveDisk(DiskImage image, int diskNumber, int sideNumber, OutputStream oStream) throws IOException {
        result.clear();

        DiskImageFile file = image.getFile();
        if (file == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        if (diskNumber < 0) {
            // 最初のディスクだけを保存
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                result.setError(DiskResult.ERR_NO_DISK);
                return result.getValid();
            }
            for (int diskNum = 0; diskNum < 1; diskNum++) {
                DiskImageDisk disk = disks.get(diskNum);
                saveDisk(disk, -1, oStream);
            }
        } else {
            // 指定したディスクを保存
            DiskImageDisk disk = file.getDisk(diskNumber);
            saveDisk(disk, sideNumber, oStream);
        }

        return result.getValid();
    }
}
