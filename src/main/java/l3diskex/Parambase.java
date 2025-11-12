/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
 * parameter template.
 */
public class Parambase {

    /** Holds the file name convention (for validator) */
    public static final class ValidNameRule {

        /** Characters that can be set at the beginning of a file name */
        private String validFirstChars;
        /** Characters that can be set in a file name */
        private String validChars;
        /** Characters that cannot be set in a file name */
        private String invalidChars;
        /** Characters that cannot be duplicated in a file name */
        private String deduplicateChars;
        /** Whether a file name is required */
        private boolean nameRequire;
        /* Size of file name */
        private int maxLength;

        public ValidNameRule() {
            nameRequire = false;
            maxLength = 0;
        }

        /** Make empty */
        public void empty() {
            validFirstChars = "";
            validChars = "";
            invalidChars = "";
            deduplicateChars = "";
            nameRequire = false;
            maxLength = 0;
        }

        /** Characters that can be set at the beginning of a file name */
        public String getValidFirstChars() {
            return validFirstChars;
        }

        /** Characters that can be set in a file name */
        public String getValidChars() {
            return validChars;
        }

        /** Characters that cannot be set in a file name */
        public String getInvalidChars() {
            return invalidChars;
        }

        /** Characters that cannot be duplicated in a file name */
        public String getDeduplicateChars() {
            return deduplicateChars;
        }

        /** Whether a file name is required */
        public boolean isNameRequired() {
            return nameRequire;
        }

        /** Size of file name */
        public int getMaxLength() {
            return maxLength;
        }

        /** Characters that can be set at the beginning of a file name */
        public void setValidFirstChars(String str) {
            validFirstChars = str;
        }

        /** Characters that can be set in a file name */
        public void setValidChars(String str) {
            validChars = str;
        }

        /** Characters that cannot be set in a file name */
        public void setInvalidChars(String str) {
            invalidChars = str;
        }

        /** Characters that cannot be duplicated in a file name */
        public void setDeduplicateChars(String str) {
            deduplicateChars = str;
        }

        /** Whether a file name is required */
        public void requireName(boolean val) {
            nameRequire = val;
        }

        /** Size of file name */
        public void setMaxLength(int val) {
            maxLength = val;
        }
    }

    /**
     * Holds special attributes, etc.
     *
     * @see MyAttributes
     */
    public static final class MyAttribute {

        /** index */
        private final int index;
        /** attribute type */
        private final int type;
        /** attribute value */
        private final int value;
        /** マスク */
        private final int mask;
        /** name */
        private String name;
        /** description */
        private String desc;

        public MyAttribute() {
            index = 0;
            type = 0;
            value = 0;
            mask = -1;
        }

        public MyAttribute(int index, int type, int value, int mask, String name, String desc) {
            this.index = index;
            this.type = type;
            this.value = value;
            this.mask = mask;
            this.name = name;
            this.desc = desc;

            if (this.name == null || this.name.isEmpty()) this.name = "???";
        }

        /** インデックス */
        public int getIndex() {
            return index;
        }

        /** 属性タイプ */
        public int getType() {
            return type;
        }

        /** 属性値 */
        public int getValue() {
            return value;
        }

        /** マスク */
        public int getMask() {
            return mask;
        }

        /** 名前 */
        public String getName() {
            return name;
        }

        /** description */
        public String getDescription() {
            return desc;
        }
    }

    /**
     * List of special attributes. An array of MyAttribute.
     */
    public static final class MyAttributes {

        /** Returns an item that matches the attribute type and value. */
        public static MyAttribute find(List<MyAttribute> list, int type, int value) {
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getType() == type && attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute type and value. */
        public static MyAttribute find(List<MyAttribute> list, int type, int mask, int value) {
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (((attr.getType() & mask) == (type & mask)) && attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute type. */
        public static MyAttribute findType(List<MyAttribute> list, int type, int mask) {
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (((attr.getType() & mask) == (type & mask))) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute value. */
        public static MyAttribute findValue(List<MyAttribute> list, int value) {
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute name. */
        public static MyAttribute find(List<MyAttribute> list, int type, String name) {
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getType() == type && attr.getName().equals(name)) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute name. */
        public static MyAttribute find(List<MyAttribute> list, String name) {
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getName().equals(name)) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute name. Match in upper case. */
        public static MyAttribute findUpperCase(List<MyAttribute> list, String name) {
            String iName = name.toUpperCase();
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getName().toUpperCase().equals(iName)) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute name and type. Match in upper case. */
        public static MyAttribute findUpperCase(List<MyAttribute> list, String name, int type, int mask) {
            String iName = name.toUpperCase();
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getName().toUpperCase().equals(iName) && ((attr.getType() & mask) == (type & mask))) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns an item that matches the attribute name, type, and value. Match in upper case. */
        public static MyAttribute findUpperCase(List<MyAttribute> list, String name, int type, int mask, int value) {
            String iName = name.toUpperCase();
            MyAttribute match = null;
            for (MyAttribute attr : list) {
                if (attr.getName().toUpperCase().equals(iName) &&
                        ((attr.getType() & mask) == (type & mask)) &&
                        attr.getValue() == (value & attr.getMask())) {
                    match = attr;
                    break;
                }
            }
            return match;
        }

        /** Returns the position of the item that matches the attribute value. */
        public static int getIndexByValue(List<MyAttribute> list, int value) {
            int index = -1;
            MyAttribute attr = findValue(list, value);
            if (attr != null) index = attr.getIndex();
            return index;
        }

        /** Returns the attribute type of the item that matches the attribute value. */
        public static int getTypeByValue(List<MyAttribute> list, int value) {
            MyAttribute attr = findValue(list, value);
            return (attr != null) ? attr.getType() : -1;
        }

        /** Returns the attribute type from the position. */
        public static int getTypeByIndex(List<MyAttribute> list, int index) {
            if (index >= 0 && index < list.size())
                return list.get(index).getType();
            return -1;
        }

        /** Returns the attribute value from the position. */
        public static int getValueByIndex(List<MyAttribute> list, int index) {
            if (index >= 0 && index < list.size())
                return list.get(index).getValue();
            return -1;
        }
    }

    /** Provides a template for disk parameters. */
    public static class TemplatesBase {

        /**
         * Descriptionエレメントをロード
         *
         * @param node       子ノード
         * @param localeName ローケル名
         * @param desc       [out] description
         * @param descLocale [out] description locale
         * @return true
         */
        protected static boolean loadDescription(Node node,
                                                 String localeName,
                                                 String[] desc,
                                                 String[] descLocale) {
            if (node == null) return false;
            if (((Element) node).hasAttribute("Locale")) {
                String locale = ((Element) node).getAttribute("Locale");
                if (locale.equals(localeName)) {
                    descLocale[0] = node.getTextContent();
                    return true;
                }
            }

            String content = node.getTextContent();
            desc[0] = content;
            return true;
        }

        /**
         * 独自エレメントのロード
         *
         * @param node 子ノード
         * @param value  value
         * @param result [out] converted value
         * @return true
         */
        protected static boolean loadVariousParam(Node node, String value, Object[] result) {
            if (node == null) return false;
            String tagName = node.getNodeName();
            if (tagName.isEmpty()) return false;

            result[0] = switch (tagName) {
                case "int" -> Integer.parseInt(value);
                case "bool" -> Boolean.parseBoolean(value);
                case "string" -> value;
                default -> null;
            };

            return true;
        }

        /**
         * SpecialAttributes/AttributesByExtension エレメントをロード
         *
         * @param node       子ノード
         * @param localeName ローケル名
         * @param type       タイプ
         * @param attrs      [out] 値
         * @return true
         */
        protected static boolean loadMyAttribute(Node node,
                                                 String localeName,
                                                 int type,
                                                 List<MyAttribute> attrs) {
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

        /** Map of attribute names and attribute relationships */
        static final Map<String, EnumSet<FileTypeMask>> specialAttrNames = new LinkedHashMap<>() {{
            put("MachineBinary", EnumSet.of(FILE_TYPE_MACHINE_MASK, FILE_TYPE_BINARY_MASK));
            put("BasicBinary", EnumSet.of(FILE_TYPE_BASIC_MASK, FILE_TYPE_BINARY_MASK));
            put("BasicAscii", EnumSet.of(FILE_TYPE_BASIC_MASK, FILE_TYPE_ASCII_MASK));
            put("BasicInteger", EnumSet.of(FILE_TYPE_BASIC_MASK, FILE_TYPE_INTEGER_MASK));
            put("DataAscii", EnumSet.of(FILE_TYPE_DATA_MASK, FILE_TYPE_ASCII_MASK));
            put("DataBinary", EnumSet.of(FILE_TYPE_DATA_MASK, FILE_TYPE_BINARY_MASK));
            put("DataRandom", EnumSet.of(FILE_TYPE_DATA_MASK, FILE_TYPE_RANDOM_MASK));
            put("Binary", EnumSet.of(FILE_TYPE_BINARY_MASK));
            put("Ascii", EnumSet.of(FILE_TYPE_ASCII_MASK));
            put("Random", EnumSet.of(FILE_TYPE_RANDOM_MASK));
            put("Volume", EnumSet.of(FILE_TYPE_VOLUME_MASK));
            put("System", EnumSet.of(FILE_TYPE_SYSTEM_MASK));
        }};

        /**
         * SpecialAttributes/AttributesByExtension エレメントをロード
         *
         * @param node       子ノード
         * @param localeName ローケル名
         * @param errMsgs    [out] エラーメッセージ
         * @param attrs      [out] 値
         * @return true
         */
        protected static boolean loadMyAttributesInTypes(Node node,
                                                         String localeName,
                                                         StringBuilder errMsgs,
                                                         List<MyAttribute> attrs) {
            Node citemnode = node.getFirstChild();
            while (citemnode != null) {
                for (Map.Entry<String, EnumSet<FileTypeMask>> e : specialAttrNames.entrySet()) {
                    if (citemnode.getNodeName().equals(e.getKey())) {
                        loadMyAttribute(citemnode, localeName, e.getValue().stream().mapToInt(FileTypeMask::getValue).sum(), attrs);
                        break;
                    }
                }
                citemnode = citemnode.getNextSibling();
            }
            return true;
        }

        /**
         * FileNameCharacters/VolumeNameCharacters エレメントをロード
         *
         * @param node       子ノード
         * @param validChars [out] 値
         * @param errMsgs    [out] エラー時メッセージ
         * @return true
         */
        protected static boolean loadValidChars(Node node,
                                                Parambase.ValidNameRule validChars,
                                                StringBuilder errMsgs) {
            boolean valid = true;
            String[] chars = new String[4];
            Node cnode = node.getFirstChild();
            while (cnode != null && cnode.getNodeType() == Node.ELEMENT_NODE) {
                int encoding = Utils.toInt(((Element) cnode).getAttribute("encoding"));
                String name = cnode.getNodeName();
                int num = -1;
                if (name.startsWith("Valid")) {
                    num = 0;
                    name = name.substring(5);
                } else if (name.startsWith("Invalid")) {
                    num = 1;
                    name = name.substring(7);
                } else if (name.startsWith("Duplicate")) {
                    num = 2;
                    name = name.substring(9);
                }

                if (num == 0) {
                    if (name.startsWith("First")) {
                        num = 3;
                        name = name.substring(5);
                    }
                }

                if (num >= 0) {
                    switch (name) {
                        case "CharSet" -> {
                            String[] tmp = new String[1];
                            Utils.decodeEscape(cnode.getTextContent(), tmp);
                            chars[num] = tmp[0];
                        }
                        case "Code" -> {
                            int c = Utils.toInt(cnode.getTextContent());
                            if (encoding == 0) {
                                if (c < 0 || c >= 0x80) {
                                    errMsgs.append("\n");
                                    errMsgs.append("Out of range in InvalidateCharacters::Code");
                                    errMsgs.append("(line #%d)".formatted(-1));
                                    valid = false;
                                } else {
                                    chars[num] = String.valueOf((char) c);
                                }
                            } else {
                                // unicode
                                chars[num] = String.valueOf(c);
                            }
                        }
                        case "CodeRange" -> {
                            int first = Utils.toInt(((Element) cnode).getAttribute("first"));
                            int last = Utils.toInt(((Element) cnode).getAttribute("last"));
                            if (encoding == 0) {
                                for (int c = first; c <= last; c++) {
                                    if (c < 0 || c >= 0x80) {
                                        errMsgs.append("\n");
                                        errMsgs.append("Out of range in InvalidateCharacters::CodeRange");
                                        errMsgs.append("(line #%d)".formatted(-1));
                                        valid = false;
                                        break;
                                    } else {
                                        chars[num] = String.valueOf((char) c);
                                    }
                                }
                            } else {
                                // unicode
                                for (int c = first; c <= last; c++) {
                                    chars[num] = String.valueOf(c);
                                }
                            }
                        }
                    }
                }
                cnode = cnode.getNextSibling();
            }
            if (valid) {
                validChars.setValidChars(chars[0]);
                validChars.setInvalidChars(chars[1]);
                validChars.setDeduplicateChars(chars[2]);
                validChars.setValidFirstChars(chars[3]);
            }
            return valid;
        }

        /** Load FileNameCompareCase element */
        protected static boolean loadFileNameCompareCase(Node node, boolean[] val) {
            String content = node.getTextContent();
            val[0] = content.equalsIgnoreCase("INSENSITIVE");
            return true;
        }

        public TemplatesBase() {
        }
    }
}
