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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import l3diskex.Parambase.TemplatesBase;
import org.xml.sax.SAXException;


public class FileParam {

    private static final Logger logger = System.getLogger(FileParam.class.getName());

    /**
     * FileFormat
     */
    public static class FileFormat {

        // index inside the list
        private final int m_idx;
        // file type ("d88","plain",...)
        private String m_name;
        // description
        private final String m_description;

        public FileFormat() {
            m_idx = 0;
            m_name = "";
            m_description = "";
        }

        public FileFormat(int idx, String name, String desc) {
            m_idx = idx;
            m_name = name;
            m_description = desc;
        }

        public int getIndex() {
            return m_idx;
        }

        public void setName(String val) {
            m_name = val;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }
    }

    /**
     * DiskTypeHint
     */
    public static class DiskTypeHint {

        private String m_hint;
        private int m_kind;

        public DiskTypeHint() {
            m_hint = "";
            m_kind = 0;
        }

        public DiskTypeHint(String hint) {
            m_hint = hint;
            m_kind = 0;
        }

        public DiskTypeHint(String hint, int kind) {
            m_hint = hint;
            m_kind = kind;
        }

        public void set(String hint, int kind) {
            m_hint = hint;
            m_kind = kind;
        }

        public String getHint() {
            return m_hint;
        }

        public int getKind() {
            return m_kind;
        }
    }

    /**
     * FileParamFormat
     */
    public static class FileParamFormat {

        private String m_type;   // file type ("d88","plain",...)
        private final List<DiskTypeHint> m_hints;  // list of hints

        public FileParamFormat() {
            m_type = "";
            m_hints = new ArrayList<>();
        }

        public FileParamFormat(String type) {
            m_type = type;
            m_hints = new ArrayList<>();
        }

        public void addHint(String val, int kind) {
            m_hints.add(new DiskTypeHint(val, kind));
        }

        public void setType(String val) {
            m_type = val;
        }

        public String getType() {
            return m_type;
        }

        public List<DiskTypeHint> getHints() {
            return m_hints;
        }
    }

    /**
     * FileParam
     */
    protected String m_extension; // extension
    protected List<FileParamFormat> m_formats;   // list of formats
    protected String m_description; // description

    public FileParam() {
        clearFileParam();
    }

    public FileParam(FileParam src) {
        setFileParam(src);
    }

    public FileParam(String n_ext, List<FileParamFormat> n_formats, String n_desc) {
        setFileParam(n_ext, n_formats, n_desc);
    }

    /* assignment operator --------------------------------------------*/
    public FileParam assign(FileParam src) {
        setFileParam(src);
        return this;
    }

    /* setters --------------------------------------------------------*/
    public void setFileParam(FileParam src) {
        m_extension = src.m_extension;
        m_formats = src.m_formats;
        m_description = src.m_description;
    }

    public void setFileParam(String n_ext, List<FileParamFormat> n_formats, String n_desc) {
        m_extension = n_ext;
        m_formats = n_formats;
        m_description = n_desc;
        m_extension = m_extension.toLowerCase(Locale.ROOT);
    }

    public void clearFileParam() {
        m_extension = "";
        m_formats = new ArrayList<>();
        m_description = "";
    }

    /* getters --------------------------------------------------------*/
    public String getExt() {
        return m_extension;
    }

    public List<FileParamFormat> getFormats() {
        return m_formats;
    }

    public String getDescription() {
        return m_description;
    }

    /**
     * WildCard
     */
    public static class WildCard {

        private final String m_format;
        private final String m_ext;
        private final String m_card;

        public WildCard() {
            m_format = "";
            m_ext = "";
            m_card = "";
        }

        public WildCard(String n_format, String n_ext, String n_card) {
            m_format = n_format;
            m_ext = n_ext;
            m_card = n_card;
        }

        public String getFormat() {
            return m_format;
        }

        public String getExt() {
            return m_ext;
        }

        public String getCard() {
            return m_card;
        }
    }

    /**
     * FileTypes
     */
    public static class FileTypes extends TemplatesBase {

        private final List<FileFormat> formats = new ArrayList<>(); // file formats
        private final List<FileParam> types = new ArrayList<>();  // file parameters

        private String wcardForLoad = "";   // wildcard for loading
        private final List<WildCard> wcardForSave = new ArrayList<>(); // wildcards for saving
        private final List<Integer> idxForSave = new ArrayList<>(); // order of wildcards at save

        /*
         *  MakeWildcard() – internal helper
         **/
        private void makeWildcard() {
            // 3 columns as in the original implementation
            @SuppressWarnings("unchecked")
            List<List<String>> exts = new ArrayList<>(3);
            for (int i = 0; i < 3; ++i) {
                exts.add(new ArrayList<>());
            }

            for (int i = 0; i < DiskWriter.cFormatTypeNamesForSave.length; ++i) {
                String typeName = DiskWriter.cFormatTypeNamesForSave[i];
                if (typeName == null || typeName.isEmpty()) continue;

                int count = 0;
                for (FileParam fp : types) {
                    for (FileParamFormat fmt : fp.getFormats()) {
                        if (fmt.getType().equals(typeName)) {
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    exts.get(0).add(typeName);
                }
            }

            // Build the load wildcard
            String sbLoad = "*." +
                    String.join(", *.", exts.get(0)) +
                    "|*." +
                    String.join(", *.", exts.get(0));

            wcardForLoad = sbLoad;

            // Build the save wildcards
            wcardForSave.clear();
            for (int i = 0; i < DiskWriter.cFormatTypeNamesForSave.length; ++i) {
                String name = DiskWriter.cFormatTypeNamesForSave[i];
                if (name == null || name.isEmpty()) continue;

                // Find a FileParam that contains this format
                for (FileParam fp : types) {
                    for (FileParamFormat fmt : fp.getFormats()) {
                        if (fmt.getType().equals(name)) {
                            // Build a simple card: "<format>.<ext>"
                            String card = name + "." + fmt.getType();
                            wcardForSave.add(new WildCard(name, fmt.getType(), card));
                        }
                    }
                }
            }
            // Example ordering – in the real code this is more elaborate
            idxForSave.clear();
            for (int i = 0; i < wcardForSave.size(); ++i) {
                idxForSave.add(i);
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
         * XMLファイルをロード
         *
         * @param dataPath   ファイルパス
         * @param localeName ローケル(jaなど)
         * @param errmsgs [out] エラーメッセージ
         * @return false: エラー
         * @see "file_types.xml"
         */
        public boolean load(String dataPath, String localeName, StringBuilder errmsgs) {
            String xmlFile = dataPath + "file_types.xml";
            File file = new File(xmlFile);
            if (!file.exists()) {
                errmsgs.append("File not found: ").append(xmlFile);
                return false;
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(file);
                Element root = doc.getDocumentElement();
                if (!"FileTypes".equals(root.getTagName())) {
                    errmsgs.append("Root element is not <FileTypes>");
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
                            String extAttr = elem.getAttribute("Extension");
                            String desc = getDescriptionFromChildren(elem, "Description");
                            List<FileParamFormat> fpFormats = new ArrayList<>();

                            // Process nested <Format> elements
                            NodeList subNodes = elem.getChildNodes();
                            for (int m = 0; m < subNodes.getLength(); ++m) {
                                Node subNode = subNodes.item(m);
                                if (subNode.getNodeType() != Node.ELEMENT_NODE) continue;
                                Element subElem = (Element) subNode;
                                if ("Format".equals(subElem.getTagName())) {
                                    String typeAttr = subElem.getAttribute("name");
                                    FileParamFormat fpf = new FileParamFormat(typeAttr);

                                    // Process <Hint> elements inside <Format>
                                    NodeList hintNodes = subElem.getChildNodes();
                                    for (int h = 0; h < hintNodes.getLength(); ++h) {
                                        Node hintNode = hintNodes.item(h);
                                        if (hintNode.getNodeType() != Node.ELEMENT_NODE) continue;
                                        Element hintElem = (Element) hintNode;
                                        if ("Hint".equals(hintElem.getTagName())) {
                                            String hintText = hintElem.getTextContent().trim();
                                            fpf.addHint(hintText, 0);
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
logger.log(Level.INFO, "formats: " + formats.size());
logger.log(Level.INFO, "types: " + types.size());
                return true;

            } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                errmsgs.append("XML parse error: ").append(e.getMessage());
                return false;
            }
        }

        /**
         * FindExt
         */
        public FileParam findExt(String n_ext) {
            for (FileParam fp : types) {
                if (fp.getExt().equalsIgnoreCase(n_ext)) {
                    return fp;
                }
            }
            return null;
        }

        /*
         *  IndexOfExt
         **/
        public int indexOfExt(String n_ext) {
            for (int i = 0; i < types.size(); ++i) {
                if (types.get(i).getExt().equalsIgnoreCase(n_ext)) {
                    return i;
                }
            }
            return -1; // mimics wxNOT_FOUND
        }

        /*
         *  FindFormat by name
         **/
        public FileFormat findFormat(String n_name) {
            for (FileFormat ff : formats) {
                if (ff.getName().equalsIgnoreCase(n_name)) {
                    return ff;
                }
            }
            return null;
        }

        /*
         *  FindFormat by index
         **/
        public FileFormat findFormat(int idx) {
            if (idx >= 0 && idx < formats.size()) {
                return formats.get(idx);
            }
            return null;
        }

        /*
         *  File loading helpers
         **/
        public String getWildcardForLoad() {
            return wcardForLoad;
        }

        public String getWildcardForSave(String n_format, String n_ext) {
            idxForSave.clear();
            wcardForSave.clear();

            // Build the save list in the same order as the C++ code
            for (int i = 0; i < DiskWriter.cFormatTypeNamesForSave.length; ++i) {
                String name = DiskWriter.cFormatTypeNamesForSave[i];
                if (name == null || name.isEmpty()) continue;

                for (FileParam fp : types) {
                    for (FileParamFormat fmt : fp.getFormats()) {
                        if (fmt.getType().equals(name)) {
                            String card = name + "." + fp.getExt();
                            wcardForSave.add(new WildCard(name, fp.getExt(), card));
                            idxForSave.add(wcardForSave.size() - 1);
                        }
                    }
                }
            }

            // Build the final string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < idxForSave.size(); ++i) {
                int pos = idxForSave.get(i);
                sb.append(wcardForSave.get(pos).getCard());
                if (i < idxForSave.size() - 1) {
                    sb.append("|");
                }
            }
            return sb.toString();
        }

        public FileFormat getFilterForSave(int index) {
            if (index < 0 || index >= idxForSave.size()) return null;
            int pos = idxForSave.get(index);
            String fmtName = wcardForSave.get(pos).getFormat();
            return findFormat(fmtName);
        }

        public void getFormatByIndexForSave(int index, StringBuilder format) {
            if (index < 0 || index >= idxForSave.size()) {
                format.setLength(0);
                return;
            }
            int pos = idxForSave.get(index);
            format.append(wcardForSave.get(pos).getFormat());
        }

        public void getExtByIndexForSave(int index, StringBuilder ext) {
            if (index < 0 || index >= idxForSave.size()) {
                ext.setLength(0);
                return;
            }
            int pos = idxForSave.get(index);
            ext.append(wcardForSave.get(pos).getExt());
        }

        /*
         *  Constructors / Destructors
         **/
        public FileTypes() {
        }

        /*
         *  Public API
         **/
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

    /**
     * Global instance (equivalent to the C++ `extern FileTypes gFileTypes;`)
     */
    public static FileTypes gFileTypes = new FileTypes();
}
