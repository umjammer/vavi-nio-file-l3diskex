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
     * @param ostream    出力先
     * @return 0 正常, -1 エラー
     */
    private int saveDisk(DiskImageDisk disk, int sideNumber, OutputStream ostream) throws IOException {
        if (disk == null) {
            p_result.setError(DiskResult.ERR_NO_DISK);
            return p_result.getValid();
        }

        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
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
            for (int idx = 0; idx < sortedSectors.size(); idx++) {
                DiskImageSector sector = sortedSectors.get(idx);
                if (sector == null) continue;

                // write sector body
                byte[] buffer = sector.getSectorBuffer();
                int bufferSize = sector.getSectorBufferSize();
                if (buffer != null && bufferSize > 0) {
                    ostream.write(buffer, 0, bufferSize);
                }
                //sector.clearModify();
            }
        }

        //disk.clearModify();
        return p_result.getValid();
    }

    //
    // べた形式で保存
    //
    public DiskPlainWriter(DiskWriter dw_, DiskResult result_) {
        super(dw_, result_);
    }

    /**
     * べたイメージでファイルに保存
     *
     * @param image      ディスクイメージ
     * @param diskNumber ディスク番号(0-) / -1のときは全体
     * @param sideNumber サイド番号(0-) / -1のときは両面
     * @param ostream    出力先
     * @return 0 正常, -1 エラー
     */
    @Override
    public int saveDisk(DiskImage image, int diskNumber, int sideNumber, OutputStream ostream) throws IOException {
        p_result.clear();

        DiskImageFile file = image.getFile();
        if (file == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        if (diskNumber < 0) {
            // 最初のディスクだけを保存
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                p_result.setError(DiskResult.ERR_NO_DISK);
                return p_result.getValid();
            }
            for (int diskNum = 0; diskNum < 1; diskNum++) {
                DiskImageDisk disk = disks.get(diskNum);
                saveDisk(disk, -1, ostream);
            }
        } else {
            // 指定したディスクを保存
            DiskImageDisk disk = file.getDisk(diskNumber);
            saveDisk(disk, sideNumber, ostream);
        }

        return p_result.getValid();
    }
}
