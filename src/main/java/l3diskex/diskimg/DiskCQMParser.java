package l3diskex.diskimg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.util.ByteUtil;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/* ---------------------------- */
/*  Main CQM parser class       */
/* ---------------------------- */
public class DiskCQMParser extends DiskPlainParser {

    /* --- CQM header representation ----------------------------------- */

    private static final int HEADER_SIZE = 133;   // bytes before comment

    private static class CqmDskHeader {

        byte[] ident = new byte[3];
        int sectorSize;
        int secPerCluster;
        int reservedSecs;
        int numOfFat;
        int rootEntCnt;
        int totalSec16;
        int mediaId;
        int fatSize16;
        int sectorsPerTrack;
        int numOfHeads;
        int hiddenSectors;
        int totalSec32;
        byte[] desc = new byte[60];
        int blind;
        int density;
        int usedTracks;
        int totalTracks;
        int dataCrc;
        byte[] volumeLabel = new byte[11];
        int time;
        int date;
        int commentLength;
        int sectorBase;
        byte[] unknown1 = new byte[2];
        int interleave;
        int skew;
        int drive;
        byte[] reserved2 = new byte[13];
        int headCrc;
        /* comment bytes are variable – omitted from the fixed layout */
    }

    /** Reads a CQM header from the given stream. */
    private static CqmDskHeader readHeader(InputStream is) throws IOException {
        byte[] buf = new byte[HEADER_SIZE];
        int read = 0;
        while (read < HEADER_SIZE) {
            int r = is.read(buf, read, HEADER_SIZE - read);
            if (r == -1) break;
            read += r;
        }
        if (read < HEADER_SIZE) throw new EOFException("Header too short");

        CqmDskHeader h = new CqmDskHeader();
        int pos = 0;

        // ident[3]
        System.arraycopy(buf, pos, h.ident, 0, 3);
        pos += 3;

        // sector_size (2 bytes, big‑endian)
        h.sectorSize = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // sec_per_cluster (1 byte)
        h.secPerCluster = buf[pos++] & 0xFF;

        // reserved_secs (2)
        h.reservedSecs = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // num_of_fat (1)
        h.numOfFat = buf[pos++] & 0xFF;

        // root_ent_cnt (2)
        h.rootEntCnt = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // total_sec_16 (2)
        h.totalSec16 = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // media_id (1)
        h.mediaId = buf[pos++] & 0xFF;

        // fat_size_16 (2)
        h.fatSize16 = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // sectors_per_track (2)
        h.sectorsPerTrack = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // num_of_heads (2)
        h.numOfHeads = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // hidden_sectors (4)
        h.hiddenSectors = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        pos += 4;

        // total_sec_32 (4)
        h.totalSec32 = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        pos += 4;

        // desc[60]
        System.arraycopy(buf, pos, h.desc, 0, 60);
        pos += 60;

        // blind (1)
        h.blind = buf[pos++] & 0xFF;

        // density (1)
        h.density = buf[pos++] & 0xFF;

        // used_tracks (1)
        h.usedTracks = buf[pos++] & 0xFF;

        // total_tracks (1)
        h.totalTracks = buf[pos++] & 0xFF;

        // data_crc (4)
        h.dataCrc = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        pos += 4;

        // volume_label[11]
        System.arraycopy(buf, pos, h.volumeLabel, 0, 11);
        pos += 11;

        // time (2)
        h.time = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // date (2)
        h.date = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // comment_length (2)
        h.commentLength = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;

        // sector_base (1)
        h.sectorBase = buf[pos++] & 0xFF;

        // unknown1[2] (2)
        System.arraycopy(buf, pos, h.unknown1, 0, 2);
        pos += 2;

        // interleave (1)
        h.interleave = buf[pos++] & 0xFF;

        // skew (1)
        h.skew = buf[pos++] & 0xFF;

        // drive (1)
        h.drive = buf[pos++] & 0xFF;

        // reserved2[13] (13)
        System.arraycopy(buf, pos, h.reserved2, 0, 13);
        pos += 13;

        // head_crc (1)
        h.headCrc = buf[pos++] & 0xFF;

        // comment bytes are variable – omitted here

        return h;
    }

    /* ---------------------------- */
    /*  Constructor / Destructor   */
    /* ---------------------------- */

    public DiskCQMParser(DiskImageFile file,
                         short mod_flags,
                         DiskResult result) {
        super(file, mod_flags, result);
    }

    /* ---------------------------- */
    /*  ExpandData implementation   */
    /* ---------------------------- */

    /**
     * Reads a CQM compressed stream from {@code istream} and writes the
     * decompressed data to {@code ostream}.
     *
     * @return The number of bytes written to {@code ostream}.
     */
    private int ExpandData(InputStream istream, OutputStream ostream)
            throws IOException {

        byte[] buf = new byte[2];
        int size = 0;

        while (true) {
            int n = istream.read(buf, 0, 2);
            if (n < 2) break;          // incomplete pair → stop

            short nShort = ByteUtil.readBeShort(buf, 0);
            size += 2;

            if (nShort < 0) {               // negative – copy next byte
                int m = istream.read(buf, 0, 1);
                if (m < 1) break;
                for (int i = 0; i < -nShort; i++) {
                    ostream.write(buf[0] & 0xFF);
                    size++;
                }
            } else if (nShort > 0) {        // positive – read n bytes
                byte[] chunk = new byte[nShort];
                int read = 0;
                while (read < nShort) {
                    int r = istream.read(chunk, read, nShort - read);
                    if (r == -1) break;
                    read += r;
                }
                if (read == 0) break;
                ostream.write(chunk, 0, read);
                size += read;
            } else {                        // n == 0 – stop
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

        // 1. Read the fixed part of the header
        CqmDskHeader header = readHeader(istream);

        // 2. Skip the comment bytes
        if (header.commentLength > 0) {
            long skipped = istream.skip(header.commentLength);
            if (skipped != header.commentLength) {
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0, 0);
                return result.getValid();
            }
        }

        // 3. Compute the expected decompressed size
        int sectorSize = header.sectorSize;
        int sectorsPerTrack = header.sectorsPerTrack;
        int numOfHeads = header.numOfHeads;
        int totalTracks = header.totalTracks;

        int diskSizeHint = (numOfHeads
                * (totalTracks + 2)
                * sectorsPerTrack
                * sectorSize);

        // 4. Expand the data into a byte array
        ByteArrayOutputStream otemp = new ByteArrayOutputStream(diskSizeHint);
        ExpandData(istream, otemp);
        byte[] data = otemp.toByteArray();

        // 5. Feed the decompressed data to the base parser
        ByteArrayInputStream itemp = new ByteArrayInputStream(data);
        int sts = super.parse(itemp, disk_param);

        return sts;
    }

    public int check(InputStream istream,
                     List<DiskParam> disk_params,
                     DiskParam manual_param,
                     List<DiskTypeHint> hints) throws IOException {

        // 1. Ensure stream starts at the beginning (not possible on raw InputStream)
        // (Assume caller provides stream positioned correctly.)

        // 2. Read header
        DiskCQMParser.CqmDskHeader header = readHeader(istream);

        // 3. Validate identifier
        if (header.ident[0] != (byte) 'C' ||
                header.ident[1] != (byte) 'Q' ||
                header.ident[2] != (byte) 0x14) {
            return -1;
        }

        // 4. Validate critical fields
        int sectorSize = header.sectorSize;
        if (sectorSize <= 0 || sectorSize > 4096) {
            result.setError(DiskResult.ERRV_SECTOR_SIZE_HEADER, 0, sectorSize);
            return result.getValid();
        }
        int sectorsPerTrack = header.sectorsPerTrack;
        if (sectorsPerTrack <= 0) {
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0, sectorsPerTrack);
            return result.getValid();
        }
        int sidesPerDisk = header.numOfHeads;
        if (sidesPerDisk <= 0 || sidesPerDisk > 2) {
            result.setError(DiskResult.ERRV_SIDES_HEADER, 0, sidesPerDisk);
            return result.getValid();
        }
        int tracksPerSide = header.sectorsPerTrack;
        if (tracksPerSide <= 0 || tracksPerSide > 128) {
            result.setError(DiskResult.ERRV_TRACKS_HEADER, 0, tracksPerSide);
            return result.getValid();
        }

        // 5. Interleave – ensure it is positive and reasonable
        int interleave = header.interleave;
        if (interleave <= 0 || interleave > sectorsPerTrack) {
            interleave = 1;
        }

        // 6. Look for a matching template (stubbed – always null)
        DiskParam dummy = new DiskParam();
        DiskParam param = gDiskTemplates.findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                interleave, dummy.getTrackNumberBaseOnDisk(), dummy.getSideNumberBaseOnDisk(), dummy.getSectorNumberBaseOnDisk(), 0,
                dummy.getSingles(), dummy.getParticularTracks());
        if (param != null) {
            disk_params.add(param);
        }

        // 7. If no template – create a manual parameter
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

            return 1;              // success
        }

        return 0;
    }
}
