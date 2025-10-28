///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/// CopyQMディスクパーサ
public class DiskCQMParser extends DiskPlainParser {

    /// Copy QM形式ヘッダ
    @Serdes(bigEndian = false)
    public static class CqmDskHeader {

        static final int SIZE = 133;

        @Element(sequence = 1)
        byte[] ident = new byte[3];
        @Element(sequence = 2, value = "unsigned short")
        int sectorSize;
        @Element(sequence = 3, value = "unsigned byte")
        int secPerCluster;
        @Element(sequence = 4, value = "unsigned short")
        int reservedSecs;
        @Element(sequence = 5, value = "unsigned byte")
        int numOfFat;
        @Element(sequence = 6, value = "unsigned short")
        int rootEntCnt;
        @Element(sequence = 7, value = "unsigned short")
        int totalSec16;
        @Element(sequence = 8, value = "unsigned byte")
        int mediaId;
        @Element(sequence = 9, value = "unsigned short")
        int fatSize16;
        @Element(sequence = 10, value = "unsigned short")
        int sectorsPerTrack;
        @Element(sequence = 11, value = "unsigned short")
        int numOfHeads;
        @Element(sequence = 12, value = "unsigned short")
        int hiddenSectors;
        @Element(sequence = 13)
        int totalSec32;
        @Element(sequence = 14)
        byte[] desc = new byte[60];
        @Element(sequence = 15, value = "unsigned byte")
        int blind;
        @Element(sequence = 16, value = "unsigned byte")
        int density;
        @Element(sequence = 17, value = "unsigned byte")
        int usedTracks;
        @Element(sequence = 18, value = "unsigned byte")
        int totalTracks;
        @Element(sequence = 19)
        int dataCrc;
        @Element(sequence = 20)
        byte[] volumeLabel = new byte[11];
        @Element(sequence = 21, value = "unsigned short")
        int time;
        @Element(sequence = 22, value = "unsigned short")
        int date;
        @Element(sequence = 23, value = "unsigned short")
        int commentLength;
        @Element(sequence = 24, value = "unsigned byte")
        int sectorBase;
        @Element(sequence = 25)
        byte[] unknown1 = new byte[2];
        @Element(sequence = 26, value = "unsigned byte")
        int interleave;
        @Element(sequence = 27, value = "unsigned byte")
        int skew;
        @Element(sequence = 28, value = "unsigned byte")
        int drive;
        @Element(sequence = 29)
        byte[] reserved2 = new byte[13];
        @Element(sequence = 30, value = "unsigned byte")
        int headCrc;
    }

    public DiskCQMParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /** データを展開 */
    private int expandData(InputStream istream, OutputStream ostream) throws IOException {
        int size = 0;

        byte[] buf = new byte[2];

        int len = 1;
        while (len > 0) {
            len = istream.readNBytes(buf, 0, 2);
            if (len < 2) {
                break;
            }
            size += len;

            short n = ByteUtil.readBeShort(buf, 0);
            if (n < 0) {
                // negative
                // copy next byte
                len = istream.read(buf, 0, 1);
                if (len < 1) break;
                for (int i = 0; i < -n; i++) {
                    ostream.write(buf[0] & 0xff);
                    size++;
                }
            } else if (n > 0) {
                // positive
                // read n bytes
                byte[] c = new byte[n];
                len = istream.readNBytes(c, 0, len);
                if (len == 0) {
                    break;
                }
                ostream.write(c, 0, len);

                size += len;
            } else {
                // n == 0 – stop
                break;
            }
        }
        return size;
    }

    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        if (disk_param == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0, 0);
            return result.getValid();
        }

        ((SeekableDataInputStream) istream).position(0);

        if (istream.available() < CqmDskHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        CqmDskHeader header = new CqmDskHeader();
        Serdes.Util.deserialize(istream, header);
        int comment_length = header.commentLength;

        ((SeekableDataInputStream) istream).position(CqmDskHeader.SIZE + comment_length); // wxFromCurrent

        //
        int sectorSize = header.sectorSize;
        int sectorsPerTrack = header.sectorsPerTrack;
        int numOfHeads = header.numOfHeads;
        int totalTracks = header.totalTracks;

        int diskSizeHint = numOfHeads * (totalTracks + 2) * sectorsPerTrack * sectorSize;

        // Expand the data into a byte array
        ByteArrayOutputStream otemp = new ByteArrayOutputStream(diskSizeHint);
        expandData(istream, otemp);

        ByteArrayInputStream itemp = new ByteArrayInputStream(otemp.toByteArray());

        int sts = super.parse(itemp, disk_param);

        return sts;
    }

    public int check(InputStream istream,
                     List<DiskParam> disk_params,
                     DiskParam manual_param,
                     List<DiskTypeHint> hints) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        if (istream.available() < CqmDskHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        CqmDskHeader header = new CqmDskHeader();
        Serdes.Util.deserialize(istream, header);
        if (header.ident[0] != (byte) 'C' || header.ident[1] != (byte) 'Q' || header.ident[2] != (byte) 0x14) {
            // not CopyCQ image
            return -1;
        }
        int sectorSize = header.sectorSize;
        if (sectorSize <= 0 || sectorSize > 4096) {
            // invalid
            result.setError(DiskResult.ERRV_SECTOR_SIZE_HEADER, 0, sectorSize);
            return result.getValid();
        }
        int sectorsPerTrack = header.sectorsPerTrack;
        if (sectorsPerTrack <= 0) {
            // invalid
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0, sectorsPerTrack);
            return result.getValid();
        }
        int sidesPerDisk = header.numOfHeads;
        if (sidesPerDisk <= 0 || sidesPerDisk > 2) {
            // invalid
            result.setError(DiskResult.ERRV_SIDES_HEADER, 0, sidesPerDisk);
            return result.getValid();
        }
        int tracksPerSide = header.sectorsPerTrack;
        if (tracksPerSide <= 0 || tracksPerSide > 128) {
            // invalid
            result.setError(DiskResult.ERRV_TRACKS_HEADER, 0, tracksPerSide);
            return result.getValid();
        }

        int interleave = header.interleave;
        if (interleave <= 0 || interleave > sectorsPerTrack) interleave = 1;

        // Look for a matching template
        DiskParam dummy = new DiskParam();
        DiskParam param = gDiskTemplates.findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                interleave, dummy.getTrackNumberBaseOnDisk(), dummy.getSideNumberBaseOnDisk(), dummy.getSectorNumberBaseOnDisk(), 0,
                dummy.getSingles(), dummy.getParticularTracks());
        if (param != null) {
            disk_params.add(param);
        }

        // If no template – create a manual parameter
        if (disk_params.isEmpty()) {
            manual_param.setDiskParam(
                    sidesPerDisk,
                    tracksPerSide,
                    sectorsPerTrack,
                    sectorSize,
                    0,
                    interleave,
                    dummy.getSingles(),
                    dummy.getParticularTracks());
            return 1;
        }

        return 0;
    }
}
