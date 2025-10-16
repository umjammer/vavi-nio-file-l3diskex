package l3diskex.diskimg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam.DiskParticular;
import l3diskex.diskimg.DiskParam.DiskParticulars;
import l3diskex.diskimg.FileParam.DiskTypeHint;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


public class Disk2MGParser extends DiskPlainParser {

    /* ------------------------------------------------------------------ */
    /*  Constants                                                       */
    /* ------------------------------------------------------------------ */
    private static final String DISK_2MG_HEADER = "2IMG";
    private static final int HEADER_LENGTH = 80;

    /* ------------------------------------------------------------------ */
    /*  Representation of the 80‑byte header                            */
    /* ------------------------------------------------------------------ */
    private static class TwomgHeader {
        byte[] ident = new byte[4];
        byte[] creator = new byte[4];
        short header_size;
        short version;
        int format_type;
        int flags;
        int blocks;
        int offset_data;
        int data_size;
        int offset_comm;
        int comm_size;
        int offset_creat;
        int creat_size;
        byte[] reserved = new byte[16];
    }

    /* ------------------------------------------------------------------ */
    /*  Constructor                                                      */
    /* ------------------------------------------------------------------ */
    public Disk2MGParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    /* ------------------------------------------------------------------ */
    /*  Parse the image data                                            */
    /* ------------------------------------------------------------------ */
    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        byte[] allData = readAllBytes(istream);
        if (allData.length < HEADER_LENGTH) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }

        TwomgHeader header = parseHeader(Arrays.copyOfRange(allData, 0, HEADER_LENGTH));

        int offsetData = header.offset_data;
        if (offsetData > allData.length) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }

        byte[] dataSegment = Arrays.copyOfRange(allData, offsetData, allData.length);
        int rc = super.parse(new ByteArrayInputStream(dataSegment), diskParam);

        if (rc >= 0) {
            DiskImageDisk disk = file.getDisk(0);
            if (disk != null) {
                int flags = header.flags;
                if ((flags & 0x80000000) != 0) {
                    disk.setWriteProtect(true);
                }
            }
        }
        return rc;
    }

    /* ------------------------------------------------------------------ */
    /*  Check the image header                                           */
    /* ------------------------------------------------------------------ */
    @Override
    public int check(InputStream istream, List<DiskTypeHint> hints, DiskParam diskParam,
                     List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        byte[] allData = readAllBytes(istream);
        if (allData.length < HEADER_LENGTH) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }

        TwomgHeader header = parseHeader(Arrays.copyOfRange(allData, 0, HEADER_LENGTH));

        /* Header string check */
        byte[] headerStringBytes = DISK_2MG_HEADER.getBytes(StandardCharsets.US_ASCII);
        if (!Arrays.equals(header.ident, headerStringBytes)) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        int format_type = header.format_type;
        if (format_type == 2) {
            result.setError(DiskResult.ERRV_UNSUPPORTED_TYPE, 0, "NIB");
            return result.getValid();
        } else if (format_type > 2) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        int data_size = header.data_size;

        int sides_per_disk = 1;
        int tracks_per_side = 1;
        int sectors_per_track = 1;
        int sector_size = 256;
        DiskParticulars sd = new DiskParticulars();
        DiskParticulars pt = new DiskParticulars();

        if (data_size <= 143360) {
            sides_per_disk = 1;
            tracks_per_side = 35;
            sectors_per_track = 16;
            sector_size = 256;
        } else if (data_size <= 819200) {
            sides_per_disk = 2;
            tracks_per_side = 80;
            sectors_per_track = 12;
            sector_size = 512;
            int i = 16;
            int n = sectors_per_track - 1;
            while (i < tracks_per_side) {
                pt.add(new DiskParticular(i, -1, -1, 16, n, 512));
                i += 16;
                n--;
            }
        }

        DiskParam param = gDiskTemplates.findStrict(sides_per_disk, tracks_per_side,
                sectors_per_track, sector_size, 1, 0, 0, 0, 0, sd, pt);
        if (param != null) {
            diskParams.add(param);
        }

        if (diskParams.size() == 0) {
            manualParam.setDiskParam(sides_per_disk, tracks_per_side, sectors_per_track,
                    sector_size, 0, 1, sd, pt);
            return 1;
        }

        return 0;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers for parsing the header                                  */
    /* ------------------------------------------------------------------ */
    private static TwomgHeader parseHeader(byte[] headerBytes) {
        TwomgHeader h = new TwomgHeader();
        int pos = 0;
        System.arraycopy(headerBytes, pos, h.ident, 0, 4);
        pos += 4;
        System.arraycopy(headerBytes, pos, h.creator, 0, 4);
        pos += 4;
        h.header_size = littleEndianShort(headerBytes, pos);
        pos += 2;
        h.version = littleEndianShort(headerBytes, pos);
        pos += 2;
        h.format_type = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.flags = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.blocks = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.offset_data = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.data_size = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.offset_comm = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.comm_size = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.offset_creat = bigEndianInt(headerBytes, pos);
        pos += 4;
        h.creat_size = bigEndianInt(headerBytes, pos);
        pos += 4;
        System.arraycopy(headerBytes, pos, h.reserved, 0, 8);
        return h;
    }

    private static short littleEndianShort(byte[] b, int offset) {
        return (short) ((b[offset] & 0xFF) | (b[offset + 1] << 8));
    }

    private static int bigEndianInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }
}
