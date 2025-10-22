/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import l3diskex.basicfmt.BasicCommon.FileTypeMask;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_INTEGER_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


/**
 * @author Sasaji
 */
public class Parambase {

    /**
     * 2. ValidNameRule – file name validation rule
     */
    public static final class ValidNameRule {

        /** ファイル名の先頭に設定できる文字 */
        private String validFirstChars;
        /** ファイル名に設定できる文字      */
        private String validChars;
        /** ファイル名に設定できない文字    */
        private String invalidChars;
        /** ファイル名に重複指定できない文字 */
        private String deduplicateChars;
        /** ファイル名が必須か             */
        private boolean nameRequire;
        /* ファイル名のサイズ              */
        private int maxLength;

        public ValidNameRule() {
            nameRequire = false;
            maxLength = 0;
        }

        /** 空にする */
        public void empty() {
            validFirstChars = "";
            validChars = "";
            invalidChars = "";
            deduplicateChars = "";
            nameRequire = false;
            maxLength = 0;
        }

        /* getters */
        public String getValidFirstChars() {
            return validFirstChars;
        }

        public String getValidChars() {
            return validChars;
        }

        public String getInvalidChars() {
            return invalidChars;
        }

        public String getDeduplicateChars() {
            return deduplicateChars;
        }

        public boolean isNameRequired() {
            return nameRequire;
        }

        public int getMaxLength() {
            return maxLength;
        }

        /* setters */
        public void setValidFirstChars(String str) {
            validFirstChars = str;
        }

        public void setValidChars(String str) {
            validChars = str;
        }

        public void setInvalidChars(String str) {
            invalidChars = str;
        }

        public void setDeduplicateChars(String str) {
            deduplicateChars = str;
        }

        public void requireName(boolean val) {
            nameRequire = val;
        }

        public void setMaxLength(int val) {
            maxLength = val;
        }
    }

    /**
     * 3. MyAttribute – an attribute with type, value, mask, name, desc
     */
    public static final class MyAttribute {

        private final int idx;
        private final int type;
        private final int value;
        private final int mask;
        private String name;
        private String desc;

        public MyAttribute() {
            idx = 0;
            type = 0;
            value = 0;
            mask = -1;
        }

        public MyAttribute(int nIdx, int nType, int nValue, int nMask,
                           String nName, String nDesc) {
            idx = nIdx;
            type = nType;
            value = nValue;
            mask = nMask;
            name = nName;
            desc = nDesc;

            if (name == null || name.isEmpty()) name = "???";
        }

        /* getters */
        public int getIndex() {
            return idx;
        }

        public int getType() {
            return type;
        }

        public int getValue() {
            return value;
        }

        public int getMask() {
            return mask;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return desc;
        }
    }

    /**
     * 4. MyAttributes – a list of MyAttribute (extends ArrayList)
     */
    public static final class MyAttributes extends ArrayList<MyAttribute> {

        /* 4.1 Find(type, value) */
        public MyAttribute find(int type, int value) {
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getType() == type &&
                        attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.2 Find(type, mask, value) */
        public MyAttribute find(int type, int mask, int value) {
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (((attr.getType() & mask) == (type & mask)) &&
                        attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.3 FindType(type, mask) */
        public MyAttribute findType(int type, int mask) {
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (((attr.getType() & mask) == (type & mask))) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.4 FindValue(value) */
        public MyAttribute findValue(int value) {
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.5 Find(type, name) */
        public MyAttribute find(int type, String name) {
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getType() == type && attr.getName().equals(name)) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.6 Find(name) */
        public MyAttribute find(String name) {
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getName().equals(name)) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.7 FindUpperCase(name) */
        public MyAttribute findUpperCase(String name) {
            String iname = name.toUpperCase();
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getName().toUpperCase().equals(iname)) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.8 FindUpperCase(name, type, mask) */
        public MyAttribute findUpperCase(String name, int type, int mask) {
            String iname = name.toUpperCase();
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getName().toUpperCase().equals(iname) &&
                        ((attr.getType() & mask) == (type & mask))) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.9 FindUpperCase(name, type, mask, value) */
        public MyAttribute findUpperCase(String name, int type, int mask, int value) {
            String iname = name.toUpperCase();
            MyAttribute match = null;
            for (int i = 0; i < size(); i++) {
                MyAttribute attr = get(i);
                if (attr.getName().toUpperCase().equals(iname) &&
                        ((attr.getType() & mask) == (type & mask)) &&
                        attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /* 4.10 GetIndexByValue(value) */
        public int getIndexByValue(int value) {
            int idx = -1;
            MyAttribute attr = findValue(value);
            if (attr != null) idx = attr.getIndex();
            return idx;
        }

        /* 4.11 GetTypeByValue(value) */
        public int getTypeByValue(int value) {
            MyAttribute attr = findValue(value);
            return (attr != null) ? attr.getType() : -1;
        }

        /* 4.12 GetTypeByIndex(idx) */
        public int getTypeByIndex(int idx) {
            if (idx >= 0 && idx < size())
                return get(idx).getType();
            return -1;
        }

        /* 4.13 GetValueByIndex(idx) */
        public int getValueByIndex(int idx) {
            if (idx >= 0 && idx < size())
                return get(idx).getValue();
            return -1;
        }
    }

    /* --- */
    /* 8. StSpecialAttrNames – name / type pair used in LoadMyAttributesInTypes */
    /* --- */
    public static final class StSpecialAttrNames {

        public final String name;
        public final int type;

        public StSpecialAttrNames(String n, int t) {
            name = n;
            type = t;
        }
    }

    /* --- */
    /* 9. TemplatesBase – helper methods for loading XML data                */
    /* --- */
    public static class TemplatesBase {

        /* 9.1 LoadDescription – static helper */
        protected static boolean loadDescription(Node node,
                                                 String localeName,
                                                 StringBuilder desc,
                                                 StringBuilder descLocale) {
            if (node == null) return false;
            if (((Element) node).hasAttribute("Locale")) {
                String locale = ((Element) node).getAttribute("Locale");
                if (locale.equals(localeName)) {
                    descLocale.setLength(0);
                    descLocale.append(node.getTextContent());
                    return true;
                }
            }

            String content = node.getTextContent();
            desc.setLength(0);
            desc.append(content);
            return true;
        }

        /* 9.2 LoadVariousParam – static helper */
        protected static boolean loadVariousParam(Node node,
                                                  String val,
                                                  Object[] nVal /* wrapped as array for pass‑by‑reference */) {
            if (node == null) return false;
            String tagName = node.getLocalName();
            if (tagName == null) return false;

            if (tagName.equals("int"))
                nVal[0] = Integer.parseInt(val);
            else if (tagName.equals("bool"))
                nVal[0] = Boolean.parseBoolean(val);
            else if (tagName.equals("string"))
                nVal[0] = val;
            else
                nVal[0] = null;      // unknown type

            return true;
        }

        /* 9.3 LoadMyAttribute – load a single MyAttribute */
        protected static boolean loadMyAttribute(Node node,
                                                 String localeName,
                                                 int type,
                                                 MyAttributes attrs) {
            if (node == null) return false;
            String name = ((Element) node).getAttribute("Name");
            String desc = ((Element) node).getAttribute("Description");
            String locale = ((Element) node).getAttribute("Locale");

            /* create a temporary attribute – index is set to the list size */
            int idx = attrs.size();
            MyAttribute attr = new MyAttribute(idx, type, 0, -1, name, desc);
            attrs.add(attr);
            return true;
        }

        static final Map<String, EnumSet<FileTypeMask>> specialAttrNames = new HashMap<>() {{
            put("MachineBinary",	EnumSet.of(FILE_TYPE_MACHINE_MASK, FILE_TYPE_BINARY_MASK));
            put("BasicBinary",	EnumSet.of(FILE_TYPE_BASIC_MASK, FILE_TYPE_BINARY_MASK));
            put("BasicAscii",		EnumSet.of(FILE_TYPE_BASIC_MASK, FILE_TYPE_ASCII_MASK));
            put("BasicInteger",	EnumSet.of(FILE_TYPE_BASIC_MASK, FILE_TYPE_INTEGER_MASK));
            put("DataAscii",		EnumSet.of(FILE_TYPE_DATA_MASK, FILE_TYPE_ASCII_MASK));
            put("DataBinary",		EnumSet.of(FILE_TYPE_DATA_MASK, FILE_TYPE_BINARY_MASK));
            put("DataRandom",		EnumSet.of(FILE_TYPE_DATA_MASK, FILE_TYPE_RANDOM_MASK));
            put("Binary",			EnumSet.of(FILE_TYPE_BINARY_MASK));
            put("Ascii",			EnumSet.of(FILE_TYPE_ASCII_MASK));
            put("Random",			EnumSet.of(FILE_TYPE_RANDOM_MASK));
            put("Volume",			EnumSet.of(FILE_TYPE_VOLUME_MASK));
            put("System",			EnumSet.of(FILE_TYPE_SYSTEM_MASK));
        }};

        /* 9.4 LoadMyAttributesInTypes – load attributes for all extension types */
        protected static boolean loadMyAttributesInTypes(Node node,
                                                         String localeName,
                                                         StringBuilder errmsgs,
                                                         Parambase.MyAttributes attrs) {
            if (node == null) return false;
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                String childName = child.getLocalName();
                if (childName != null && !childName.isEmpty()) {
                    for (Map.Entry<String, EnumSet<FileTypeMask>> e : specialAttrNames.entrySet()) {
                        if (childName.equals(e.getKey())) {
                            /* load each attribute inside this element */
                            Node sub = child.getFirstChild();
                            while (sub != null) {
                                loadMyAttribute(sub, localeName, e.getValue().stream().mapToInt(FileTypeMask::getValue).sum(), attrs);
                                sub = sub.getNextSibling();
                            }
                        }
                    }
                }
                child = child.getNextSibling();
            }
            return true;
        }

        /* 9.5 LoadValidChars – load file name validation characters */
        protected static boolean loadValidChars(Node node,
                                                Parambase.ValidNameRule validChars,
                                                StringBuilder errmsgs) {
            if (node == null) return false;
            /* The original code iterated over the children and added
             * characters to the rule.  This translation keeps the same
             * algorithm but uses Java string manipulation.
             */
            Node child = node.getFirstChild();
            while (child != null) {
                String name = child.getLocalName();
                if (name == null) {
                    child = child.getNextSibling();
                    continue;
                }

                if (name.equals("ValidFirst"))
                    validChars.setValidFirstChars(child.getTextContent());
                else if (name.equals("Valid"))
                    validChars.setValidChars(child.getTextContent());
                else if (name.equals("Invalid"))
                    validChars.setInvalidChars(child.getTextContent());
                else if (name.equals("Deduplicate"))
                    validChars.setDeduplicateChars(child.getTextContent());
                else if (name.equals("MaxLength"))
                    validChars.setMaxLength(Integer.parseInt(child.getTextContent()));
                else if (name.equals("NameRequired"))
                    validChars.requireName(Boolean.parseBoolean(child.getTextContent()));
                /* ignore other tags */
                child = child.getNextSibling();
            }
            return true;
        }

        /* 9.6 LoadFileNameCompareCase – compare case setting */
        protected static boolean loadFileNameCompareCase(Node node, boolean[] val) {
            if (node == null) return false;
            String content = node.getTextContent();
            val[0] = content.equalsIgnoreCase("Ignore");
            return true;
        }

        /* ------------------------------------------------------------------ */
        /* Constructor / destructor                                           */
        /* ------------------------------------------------------------------ */
        public TemplatesBase() {
            /* nothing special – the C++ ctor was empty                    */
        }
    }
}
