/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import l3diskex.Parambase.TemplatesBase;
import l3diskex.Utils;
import org.xml.sax.SAXException;


public class FileParam {

    private static final Logger logger = System.getLogger(FileParam.class.getName());

    public static FileTypes fileTypes = new FileTypes();

    /**
     * FileFormat
     */
    public static class FileFormat {

        /** index inside the list */
        private final int index;
        /** file type ("d88", "plain",...) */
        private String name;
        /** description */
        private final String description;

        public FileFormat() {
            index = 0;
            name = "";
            description = "";
        }

        public FileFormat(int idx, String name, String desc) {
            index = idx;
            this.name = name;
            description = desc;
        }

        public int getIndex() {
            return index;
        }

        public void setName(String val) {
            name = val;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        @Override public String toString() {
            return new StringJoiner(", ", FileFormat.class.getSimpleName() + "[", "]")
                    .add("index=" + index)
                    .add("name='" + name + "'")
                    .add("description='" + description + "'")
                    .toString();
        }
    }

    /**
     * DiskTypeHint
     */
    public static class DiskTypeHint {

        private String hint;
        private int kind;

        public DiskTypeHint() {
            hint = "";
            kind = 0;
        }

        public DiskTypeHint(String hint) {
            this.hint = hint;
            kind = 0;
        }

        public DiskTypeHint(String hint, int kind) {
            this.hint = hint;
            this.kind = kind;
        }

        public void set(String hint, int kind) {
            this.hint = hint;
            this.kind = kind;
        }

        public String getHint() {
            return hint;
        }

        public int getKind() {
            return kind;
        }

        @Override public String toString() {
            return new StringJoiner(", ", DiskTypeHint.class.getSimpleName() + "[", "]")
                    .add("hint='" + hint + "'")
                    .add("kind=" + kind)
                    .toString();
        }
    }

    /**
     * FileParamFormat
     */
    public static class FileParamFormat {

        /** file type ("d88", "plain",...) */
        private String type;
        /** list of hints */
        private final List<DiskTypeHint> hints;

        public FileParamFormat() {
            type = "";
            hints = new ArrayList<>();
        }

        public FileParamFormat(String type) {
            this.type = type;
            hints = new ArrayList<>();
        }

        public void addHint(String val, int kind) {
            hints.add(new DiskTypeHint(val, kind));
        }

        public void setType(String val) {
            type = val;
        }

        public String getType() {
            return type;
        }

        public List<DiskTypeHint> getHints() {
            return hints;
        }

        @Override public String toString() {
            return new StringJoiner(", ", FileParamFormat.class.getSimpleName() + "[", "]")
                    .add("type='" + type + "'")
                    .add("hints=" + hints)
                    .toString();
        }
    }

    //
    // FileParam
    //

    /** extension */
    protected String extension;
    /** list of formats */
    protected List<FileParamFormat> formats;
    /** description */
    protected String description;

    public FileParam() {
        clearFileParam();
    }

    public FileParam(FileParam src) {
        setFileParam(src);
    }

    public FileParam(String ext, List<FileParamFormat> formats, String desc) {
        setFileParam(ext, formats, desc);
    }

    public FileParam assign(FileParam src) {
        setFileParam(src);
        return this;
    }

    public void setFileParam(FileParam src) {
        extension = src.extension;
        formats = src.formats;
        description = src.description;
    }

    public void setFileParam(String ext, List<FileParamFormat> formats, String desc) {
        extension = ext;
        this.formats = formats;
        description = desc;
        extension = extension.toLowerCase(Locale.ROOT);
    }

    public void clearFileParam() {
        extension = "";
        formats = new ArrayList<>();
        description = "";
    }

    public String getExt() {
        return extension;
    }

    public List<FileParamFormat> getFormats() {
        return formats;
    }

    public String getDescription() {
        return description;
    }

    @Override public String toString() {
        return new StringJoiner(", ", FileParam.class.getSimpleName() + "[", "]")
                .add("extension='" + extension + "'")
                .add("formats=" + formats)
                .add("description='" + description + "'")
                .toString();
    }

    /**
     * WildCard
     */
    public record WildCard(String format, String ext, String card) {

    }

    /**
     * FileTypes
     */
    public static class FileTypes extends TemplatesBase {

        /** file formats */
        private final List<FileFormat> formats = new ArrayList<>();
        /** file parameters */
        private final List<FileParam> types = new ArrayList<>();

        /** wildcard for loading */
        private String wildCardForLoad = "";
        /** wildcards for saving */
        private final List<WildCard> wildCardForSave = new ArrayList<>();
        /** order of wildcards at save */
        private final List<Integer> indexForSave = new ArrayList<>();

        /** internal helper */
        private void makeWildcard() {
            // 3 columns as in the original implementation
            @SuppressWarnings("unchecked")
            List<List<String>> exts = new ArrayList<>(3);
            for (int i = 0; i < 3; ++i) {
                exts.add(new ArrayList<>());
            }

            for (int i = 0; i < DiskWriter.formatTypeNamesForSave.length; ++i) {
                String typeName = DiskWriter.formatTypeNamesForSave[i];
                if (typeName == null || typeName.isEmpty()) continue;

                int count = 0;
                for (FileParam type : types) {
                    for (FileParamFormat format : type.getFormats()) {
                        if (format.getType().equals(typeName)) {
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    exts.getFirst().add(typeName);
                }
            }

            // Build the load wildcard
            String sbLoad = "*." +
                    String.join(", *.", exts.getFirst()) +
                    "|*." +
                    String.join(", *.", exts.getFirst());

            wildCardForLoad = sbLoad;

            // Build the save wildcards
            wildCardForSave.clear();
            for (int i = 0; i < DiskWriter.formatTypeNamesForSave.length; ++i) {
                String name = DiskWriter.formatTypeNamesForSave[i];
                if (name == null || name.isEmpty()) continue;

                // Find a FileParam that contains this format
                for (FileParam type : types) {
                    for (FileParamFormat format : type.getFormats()) {
                        if (format.getType().equals(name)) {
                            // Build a simple card: "<format>.<ext>"
                            String card = name + "." + format.getType();
                            wildCardForSave.add(new WildCard(name, format.getType(), card));
                        }
                    }
                }
            }
            // Example ordering – in the real code this is more elaborate
            indexForSave.clear();
            for (int i = 0; i < wildCardForSave.size(); ++i) {
                indexForSave.add(i);
            }
        }

        /**
         * internal helper for XML parsing
         */
        private String getDescriptionFromChildren(Element parent, String tagName) {
            NodeList children = parent.getElementsByTagName(tagName);
            if (children.getLength() > 0) {
                // Assuming we only care about the first matching tag.
                Node firstChild = children.item(0);
                return firstChild.getTextContent().trim();
            }
            return "";
        }

        /**
         * Load XML file
         *
         * @param dataPath    File path
         * @param localeName  Locale (ja etc.)
         * @param errMessages [out] Error messages
         * @return false: error
         * @see "file_types.xml"
         */
        public boolean load(String dataPath, String localeName, StringBuilder errMessages) {
            formats.clear();
            types.clear();

            String xmlFile = dataPath + "file_types.xml";
            File file = new File(xmlFile);
            if (!file.exists()) {
                errMessages.append("File not found: ").append(xmlFile);
                return false;
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(file);
                Element root = doc.getDocumentElement();
                if (!"FileTypes".equals(root.getTagName())) {
                    errMessages.append("Root element is not <FileTypes>");
                    return false;
                }

                NodeList children = root.getChildNodes();
                for (int n = 0; n < children.getLength(); ++n) {
                    Node node = children.item(n);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element elem = (Element) node;

                    switch (elem.getTagName()) {
                        case "FileFormatType": {
                            // <FileFormatType name="plain">
                            String nameAttr = elem.getAttribute("name");
                            String description = getDescriptionFromChildren(elem, "Description");
                            formats.add(new FileParam.FileFormat(formats.size(), nameAttr, description));
                            break;
                        }
                        case "FileType": {
                            // <FileType Extension="d88">
                            String extAttr = elem.getAttribute("ext");
                            String desc = getDescriptionFromChildren(elem, "Description");
                            List<FileParamFormat> fpFormats = new ArrayList<>();

                            // Process nested <Format> elements
                            NodeList subNodes = elem.getChildNodes();
                            for (int m = 0; m < subNodes.getLength(); ++m) {
                                Node subNode = subNodes.item(m);
                                if (subNode.getNodeType() != Node.ELEMENT_NODE) continue;
                                Element subElem = (Element) subNode;
                                if ("Format".equals(subElem.getTagName())) {
                                    String typeAttr = subElem.getAttribute("type");
                                    FileParamFormat fpf = new FileParamFormat(typeAttr);

                                    // Process <Hint> elements inside <Format>
                                    NodeList hintNodes = subElem.getChildNodes();
                                    for (int h = 0; h < hintNodes.getLength(); ++h) {
                                        Node hintNode = hintNodes.item(h);
                                        if (hintNode.getNodeType() != Node.ELEMENT_NODE) continue;
                                        Element hintElem = (Element) hintNode;
                                        if ("DiskTypeHint".equals(hintElem.getTagName())) {
                                            String hintText = hintElem.getTextContent().trim();
                                            if (!hintText.isEmpty()) {
                                                String sKind = hintElem.getAttribute("kind");
                                                int kind = 0;
                                                if (!sKind.isEmpty()) {
                                                    kind = Utils.toInt(sKind);
                                                }
                                                fpf.addHint(hintText, kind);
                                            }
                                        }
                                    }
                                    fpFormats.add(fpf);
                                }
                            }
                            types.add(new FileParam(extAttr, fpFormats, desc));
                            break;
                        }
                    }
                }

                makeWildcard();
logger.log(Level.INFO, "formats: " + formats.size() + ", " + formats);
logger.log(Level.INFO, "types: " + types.size() + ", " + types);
                return true;

            } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                errMessages.append("XML parse error: ").append(e.getMessage());
                return false;
            }
        }

        /** FindExt */
        public FileParam findExt(String ext) {
            for (FileParam type : types) {
                if (type.getExt().equalsIgnoreCase(ext)) {
                    return type;
                }
            }
            return null;
        }

        /** IndexOfExt */
        public int indexOfExt(String ext) {
            for (int i = 0; i < types.size(); ++i) {
                if (types.get(i).getExt().equalsIgnoreCase(ext)) {
                    return i;
                }
            }
            return -1;
        }

        /** FindFormat by name */
        public FileFormat findFormat(String name) {
            for (FileFormat ff : formats) {
                if (ff.getName().equalsIgnoreCase(name)) {
                    return ff;
                }
            }
            return null;
        }

        /** FindFormat by index */
        public FileFormat findFormat(int index) {
            if (index >= 0 && index < formats.size()) {
                return formats.get(index);
            }
            return null;
        }

        /** File loading helpers */
        public String getWildcardForLoad() {
            return wildCardForLoad;
        }

        public String getWildcardForSave(String format, String ext) {
            indexForSave.clear();
            wildCardForSave.clear();

            // Build the save list in the same order as the C++ code
            for (int i = 0; i < DiskWriter.formatTypeNamesForSave.length; ++i) {
                String name = DiskWriter.formatTypeNamesForSave[i];
                if (name == null || name.isEmpty()) continue;

                for (FileParam fp : types) {
                    for (FileParamFormat fmt : fp.getFormats()) {
                        if (fmt.getType().equals(name)) {
                            String card = name + "." + fp.getExt();
                            wildCardForSave.add(new WildCard(name, fp.getExt(), card));
                            indexForSave.add(wildCardForSave.size() - 1);
                        }
                    }
                }
            }

            // Build the final string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < indexForSave.size(); ++i) {
                int pos = indexForSave.get(i);
                sb.append(wildCardForSave.get(pos).card());
                if (i < indexForSave.size() - 1) {
                    sb.append("|");
                }
            }
            return sb.toString();
        }

        public FileFormat getFilterForSave(int index) {
            if (index < 0 || index >= indexForSave.size()) return null;
            int pos = indexForSave.get(index);
            String fmtName = wildCardForSave.get(pos).format();
            return findFormat(fmtName);
        }

        public void getFormatByIndexForSave(int index, StringBuilder format) {
            if (index < 0 || index >= indexForSave.size()) {
                format.setLength(0);
                return;
            }
            int pos = indexForSave.get(index);
            format.append(wildCardForSave.get(pos).format());
        }

        public void getExtByIndexForSave(int index, StringBuilder ext) {
            if (index < 0 || index >= indexForSave.size()) {
                ext.setLength(0);
                return;
            }
            int pos = indexForSave.get(index);
            ext.append(wildCardForSave.get(pos).ext());
        }

        public FileTypes() {
        }

        public FileParam getItemPtr(int index) {
            return types.get(index);
        }

        public FileParam getItem(int index) {
            return types.get(index);
        }

        public int count() {
            return types.size();
        }
    }
}
