/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;


/**
 * Public entry‑point: DiskParser
 * All other classes are nested inside for simplicity.
 */
public class DiskParser {

    /* -----------------------------------------------------------------
     *  Private data
     * ----------------------------------------------------------------- */
    private Path filepath;      // replacement for Path
    private InputStream stream;       // replacement for InputStream
    private DiskImageFile file;          // parsed disk image
    private DiskResult result;        // parsing result
    private String imageType;    // image type string

    /* -----------------------------------------------------------------
     *  Constructors / Destructors
     * ----------------------------------------------------------------- */
    public DiskParser(String filepath, InputStream stream,
                      DiskImageFile file, DiskResult result) {
        this.filepath = Path.of(filepath);
        this.stream = stream;
        this.file = file;
        this.result = result;
        this.imageType = "";
    }

    // Java has garbage collection – explicit destructor is unnecessary.

    /* -----------------------------------------------------------------
     *  Public interface
     * ----------------------------------------------------------------- */
    public int parse(String file_format, DiskParam param_hint) throws IOException {
        return parse(file_format, param_hint, DiskImageFile.MODIFY_NONE);
    }

    public int parseAdd(String file_format, DiskParam param_hint) throws IOException {
        return parse(file_format, param_hint, DiskImageFile.MODIFY_ADD);
    }

    public int check(String file_format, List<DiskParam> disk_params,
                     DiskParam manual_param) throws IOException {
        return check(file_format, disk_params, manual_param,
                     DiskImageFile.MODIFY_NONE);
    }

    public String getImageType() {
        return imageType;
    }

    /* -----------------------------------------------------------------
     *  Private helpers
     * ----------------------------------------------------------------- */
    private int parse(String file_format, DiskParam param_hint, short mod_flags) throws IOException {
        return SelectPerser(file_format, param_hint, mod_flags, new boolean[1]);
    }

    private int check(String file_format, List<DiskParam> disk_params,
                      DiskParam manual_param, short mod_flags) throws IOException {
        return SelectChecker(file_format, null, null,
                disk_params, manual_param, mod_flags, new boolean[1]);
    }

    private int SelectPerser(String type, DiskParam disk_param,
                             short mod_flags, boolean[] support) throws IOException {
        int rc = -1;
        if ("d88".equals(type)) {
            DiskD88Parser ps = new DiskD88Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("cpcdsk".equals(type)) {
            DiskDskParser ps = new DiskDskParser(file, mod_flags, result);
            if (ps.check(stream) != 0) {
                rc = ps.Parse(stream);
            }
            support[0] = true;
        } else if ("fdi".equals(type)) {
            DiskFDIParser ps = new DiskFDIParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("cqmimg".equals(type)) {
            DiskCQMParser ps = new DiskCQMParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("teletd0".equals(type)) {
            DiskTD0Parser ps = new DiskTD0Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("difcdim".equals(type)) {
            DiskDIMParser ps = new DiskDIMParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("v98fdd".equals(type)) {
            DiskVFDParser ps = new DiskVFDParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("imd".equals(type)) {
            DiskIMDParser ps = new DiskIMDParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("dskstr".equals(type)) {
            DiskSTRParser ps = new DiskSTRParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("g64".equals(type)) {
            DiskG64Parser ps = new DiskG64Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("2mg".equals(type)) {
            Disk2MGParser ps = new Disk2MGParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("adc".equals(type)) {
            DiskADCParser ps = new DiskADCParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("dmk".equals(type)) {
            DiskDmkParser ps = new DiskDmkParser(file, mod_flags, result);
            if (ps.check(stream) >= 0) {
                rc = ps.parse(stream, null);
            }
            support[0] = true;
        } else if ("jv3".equals(type)) {
            DiskJV3Parser ps = new DiskJV3Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("hfe".equals(type)) {
            DiskHfeParser ps = new DiskHfeParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("plain".equals(type)) {
            DiskPlainParser ps = new DiskPlainParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        }
        return rc;
    }

    private int SelectChecker(String type, List<DiskTypeHint> disk_hints,
                              DiskParam disk_param, List<DiskParam> disk_params,
                              DiskParam manual_param, short mod_flags,
                              boolean[] support) throws IOException {
        int rc = -1;
        if ("d88".equals(type)) {
            DiskD88Parser ps = new DiskD88Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("cpcdsk".equals(type)) {
            DiskDskParser ps = new DiskDskParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("fdi".equals(type)) {
            DiskFDIParser ps = new DiskFDIParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("cqmimg".equals(type)) {
            DiskCQMParser ps = new DiskCQMParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("teletd0".equals(type)) {
            DiskTD0Parser ps = new DiskTD0Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("difcdim".equals(type)) {
            DiskDIMParser ps = new DiskDIMParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("v98fdd".equals(type)) {
            DiskVFDParser ps = new DiskVFDParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("imd".equals(type)) {
            DiskIMDParser ps = new DiskIMDParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("dskstr".equals(type)) {
            DiskSTRParser ps = new DiskSTRParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("g64".equals(type)) {
            DiskG64Parser ps = new DiskG64Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("2mg".equals(type)) {
            Disk2MGParser ps = new Disk2MGParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("adc".equals(type)) {
            DiskADCParser ps = new DiskADCParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("dmk".equals(type)) {
            DiskDmkParser ps = new DiskDmkParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("jv3".equals(type)) {
            DiskJV3Parser ps = new DiskJV3Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("hfe".equals(type)) {
            DiskHfeParser ps = new DiskHfeParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("plain".equals(type)) {
            DiskPlainParser ps = new DiskPlainParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        }
        return rc;
    }

    /* -----------------------------------------------------------------
     *  DiskImageParser: a generic parser base class
     * ----------------------------------------------------------------- */
    public static abstract class DiskImageParser {

        protected DiskImageFile file;
        protected short modFlags;
        protected DiskResult result;

        public DiskImageParser(DiskImageFile file, short mod_flags,
                               DiskResult result) {
            this.file = file;
            this.modFlags = mod_flags;
            this.result = result;
        }

        public int check(InputStream istream) throws IOException {
            return result.getValid();
        }

        public int check(InputStream istream, List<DiskTypeHint> hints,
                         DiskParam disk_param, List<DiskParam> disk_params,
                         DiskParam manual_param) throws IOException {
            return result.getValid();
        }

        public int parse(InputStream istream, DiskParam disk_param /* = null */) throws IOException {
            return result.getValid();
        }
    }
}
