/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import static java.nio.charset.CodingErrorAction.REPORT;


/**
 * Character code conversion operations
 */
public class CharCodes {

    private static final Logger logger = System.getLogger(Utils.class.getName());

    /**
     * Conversion information for a single character code
     *
     * @see CharCodeMap
     */
    public static class CharCode {

        public String str;
        public byte[] code = new byte[4];
        public int codeLen;

        public CharCode() {
            Arrays.fill(code, (byte) 0);
            codeLen = 0;
        }

        /**
         * Register character
         *
         * @param newStr  character (string)
         * @param newCode character code (hex string)
         */
        public CharCode(String newStr, String newCode) {
            long lCode;

            Arrays.fill(code, (byte) 0);

            str = newStr;
            codeLen = newCode.length() / 2;
            if (codeLen >= code.length) codeLen = code.length - 1;
            for (int i = 0; i < codeLen; i++) {
                try {
                    lCode = Long.parseLong(newCode.substring(i * 2, i * 2 + 2), 16);
                    code[i] = (byte) (lCode & 0xff);
                } catch (NumberFormatException e) {
                    code[i] = 0;
                }
            }
        }
    }

    /**
     * Character code conversion map
     *
     * @see CharCode
     */
    public static class CharCodeMap {

        protected String name;
        protected List<CharCode> list = new ArrayList<>();
        protected int type;
        protected String fontEncoding;
        protected String description;

        protected CharCodeMap(CharCodeMap src) {
            this.name = src.name;
            this.type = src.type;
            this.fontEncoding = src.fontEncoding;
            this.description = src.description;
        }

        public CharCodeMap() {
            type = 0;
            fontEncoding = StandardCharsets.UTF_8.name();
        }

        /**
         * Register map information
         *
         * @param name Map name
         * @param type Map type
         */
        public CharCodeMap(String name, int type) {
            this.name = name;
            this.type = type;
            fontEncoding = StandardCharsets.UTF_8.name();
        }

        public void initialize() {
        }

        /** Returns the map name */
        public final String getName() {
            return name;
        }

        /** Returns the map list */
        public List<CharCode> getList() {
            return list;
        }

        /** Returns the encoding number */
        public final String getFontEncoding() {
            return fontEncoding;
        }

        /** Sets the encoding number */
        public void setFontEncoding(String val) {
            fontEncoding = val;
        }

        /** Returns the map description */
        public final String getDescription() {
            return description;
        }

        /** Sets the map description */
        public void setDescription(String val) {
            description = val;
        }

        /** Converts control codes (if necessary) */
        public void convCtrlCodes(byte[] str, int len) {
        }

        /**
         * Checks if the character code is in the character conversion table
         *
         * @param src         Character code (1-2 bytes)
         * @param remain      Remaining bytes of src
         * @param dst         [out] String
         * @param unknownChar Character to replace with if conversion is not possible
         * @return Converted byte count
         */
        public int findString(byte[] src, int offset, int remain, StringBuilder dst, byte unknownChar) {
            boolean match = false;
            int len = 0;

            if (remain == 0) return len;

            for (CharCode itm : list) {
                boolean codeMatch = true;
                if (itm.codeLen > remain) continue;
                for (int k = 0; k < itm.codeLen; k++) {
                    if (itm.code[k] != src[offset + k]) {
                        codeMatch = false;
                        break;
                    }
                }

                if (codeMatch) {
                    dst.append(itm.str);
                    len = itm.codeLen;
                    match = true;
                    break;
                }
            }
            if (!match) {
                if (0x20 <= (src[offset] & 0xff) && (src[offset] & 0xff) <= 0x7e) {
                    dst.append((char) (src[offset] & 0xff)); // Simple cast for ASCII
                    match = true;
                } else {
                    dst.append((char) (unknownChar & 0xff));
                }
                len = 1;
            }
            return len;
        }

        /**
         * Checks if the character is in the character conversion table
         *
         * @param src String (1 character)
         * @param dst [in,out] Byte array Nullable
         * @param pos [out] Position if found
         * @return true: Match, false: No match
         */
        public boolean findCode(String src, byte[] dst, int[] pos) {
            boolean match = false;
            for (CharCode itm : list) {
                if (itm.str.equals(src)) {
                    if (dst != null) {
                        System.arraycopy(itm.code, 0, dst, pos[0], itm.codeLen);
                    }
                    pos[0] += itm.codeLen;
                    match = true;
                    break;
                }
            }
            if (!match) {
                char c = src.charAt(0);
                if (c < 0x80) {
                    if (dst != null) {
                        dst[pos[0]] = (byte) c;
                    }
                    pos[0]++;
                    match = true;
                }
            }
            return match;
        }

        //
        // CharCodeMaps
        //

        /**
         * Returns the map at the specified position
         *
         * @param index Position
         */
        public static CharCodeMap getMap(List<CharCodeMap> list, int index) {
            return (index >= 0 && index < list.size() ? list.get(index) : null);
        }

        /**
         * Finds a map
         *
         * @param name Map name
         */
        public static CharCodeMap findMap(List<CharCodeMap> list, String name) {
            CharCodeMap match = null;
            for (CharCodeMap item : list) {
                if (name.equals(item.getName())) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        @Override public String toString() {
            return new StringJoiner(", ", CharCodeMap.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("list.size=" + list.size())
                    .add("type=" + type)
                    .add("fontEncoding='" + fontEncoding + "'")
                    .add("description='" + description + "'")
                    .toString();
        }
    }

    /**
     * Character code conversion map for 2-byte characters (Shift-JIS)
     */
    static class CharCodeMapMB extends CharCodes.CharCodeMap {

        protected Charset cs;

        public CharCodeMapMB() {
            cs = null;
        }

        /**
         * Register map information
         *
         * @param n_name Map name
         * @param n_type Map type = 1
         */
        public CharCodeMapMB(String n_name, int n_type) {
            super(n_name, n_type);
            cs = null;
        }

        @Override
        public void initialize() {
            cs = Charset.forName(fontEncoding);
        }

        /**
         * Converts a 1-byte character code (SJIS) to a string
         *
         * @param src         Character code (1-2 bytes)
         * @param remain      Remaining bytes of src
         * @param dst         [out] String
         * @param unknownChar Character to replace with if conversion is not possible
         * @return Converted byte count
         */
        @Override
        public int findString(byte[] src, int offset, int remain, StringBuilder dst, byte unknownChar) {
            byte[] c = new byte[4];
            int pos = 0;

            c[0] = src[offset + 0];
            c[1] = remain > 1 ? src[offset + 1] : 0;
            c[2] = 0;
            if ((c[0] & 0xff) < 0x20) {
                // ascii control char
                c[0] = unknownChar;
                c[1] = 0;
                String converted = new String(c, 0, 1, cs);
                dst.append(converted);
                pos++;
                return pos;
            } else if ((c[0] & 0xff) < 0x80) {
                // ascii visible char
                c[1] = 0;
                String converted = new String(c, 0, 1, cs);
                dst.append(converted);
                pos++;
                return pos;
            }

            if (remain <= 1) {
                c[1] = 0;
            } else {
                if ((c[1] & 0xff) < 0x20) {
                    // ascii control char
                    c[1] = unknownChar;
                }
            }

            CharsetDecoder decoder = cs.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);
            try {
                // Try to decode 2 bytes (if remain > 1) or 1 byte
                int bytesToDecode = remain > 1 ? 2 : remain;
                String decodedStr = decoder.decode(ByteBuffer.wrap(src, offset, bytesToDecode)).toString();
                int len = decodedStr.length();

                if (len > 0) {
                    // Conversion successful, even if it's 1 byte for a potential 2-byte character
                    if (bytesToDecode == 2) {
                        // Check if it's a 2-byte character
                        if (decodedStr.length() == 1) {
                            dst.append(decodedStr);
                            pos += 2; // Consume 2 bytes for the multi-byte character
                        } else if (decodedStr.length() == 2) {
                            // This case is unlikely for a single multi-byte character like SJIS
                            dst.append(decodedStr);
                            pos += 2;
                        } else {
                            // Fallback to 1-byte conversion if 2-byte failed or unexpected length
                            // Try 1-byte conversion
                            decodedStr = decoder.decode(java.nio.ByteBuffer.wrap(src, offset, 1)).toString();
                            if (!decodedStr.isEmpty()) {
                                dst.append(decodedStr);
                                pos += 1;
                            } else {
                                // Conversion failed for 1 byte too
                                dst.append((char) (unknownChar & 0xff));
                                pos++;
                            }
                        }

                    } else if (bytesToDecode == 1) {
                        // 1-byte conversion
                        dst.append(decodedStr);
                        pos++;
                    }

                    return pos;

                } else {
                    // Conversion failed for the requested bytesToDecode. Fallback to 1-byte conversion.
                    // Re-initialize decoder for 1-byte conversion attempt
                    decoder = cs.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);
                    decodedStr = decoder.decode(java.nio.ByteBuffer.wrap(src, offset, 1)).toString();
                    len = decodedStr.length();

                    if (len > 0) {
                        // Conversion successful for 1 byte
                        dst.append(decodedStr);
                        pos++;
                    } else {
                        // Conversion failed for 1 byte too
                        dst.append((char) (unknownChar & 0xff));
                        pos++;
                    }
                    return pos;
                }

            } catch (Exception e) {
logger.log(Level.TRACE, e);
                try {
                    decoder = cs.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);
                    String decodedStr = decoder.decode(java.nio.ByteBuffer.wrap(src, offset, 1)).toString();
                    if (!decodedStr.isEmpty()) {
                        // Conversion successful for 1 byte
                        dst.append(decodedStr);
                        pos++;
                    } else {
                        // Conversion failed for 1 byte too
                        dst.append((char) (unknownChar & 0xff));
                        pos++;
                    }
                } catch (Exception e2) {
logger.log(Level.TRACE, e2);
                    // Conversion failed for 1 byte too
                    dst.append((char) (unknownChar & 0xff));
                    pos++;
                }
                return pos;
            }
        }

        /**
         * Checks if the character is in the character conversion table
         *
         * @param src String (1 character)
         * @param dst [in,out] Byte array Nullable
         * @param pos [out] Position if found
         * @return true: Match, false: No match
         */
        @Override
        public boolean findCode(String src, byte[] dst, int[] pos) {
            boolean match = false;
            int currentPos = pos[0];

            CharsetEncoder encoder = cs.newEncoder();
            try {
                ByteBuffer bb = encoder.encode(CharBuffer.wrap(src));
                int len = bb.limit();

                if (len > 0) {
                    match = true;
                    if (dst != null) {
                        bb.get(dst, currentPos, len);
                    }
                    pos[0] += len;
                }
            } catch (Exception e) {
logger.log(Level.ERROR, e.getMessage(), e);
            }
            return match;
        }
    }

    /**
     * Character code conversion map for 1-byte characters (iso-8859-1)
     */
    static class CharCodeMapSB extends CharCodeMapMB {

        public CharCodeMapSB() {
        }

        public CharCodeMapSB(String n_name, int n_type) {
            super(n_name, n_type);
        }

        /**
         * Converts a 1-byte character code to a string
         *
         * @param src         Character code (1 byte)
         * @param remain      Remaining bytes of src
         * @param dst         [out] String
         * @param unknownChar Character to replace with if conversion is not possible
         * @return Converted byte count
         */
        @Override
        public int findString(byte[] src, int offset, int remain, StringBuilder dst, byte unknownChar) {
            byte[] c = new byte[2];
            int pos = 0;

            c[0] = src[offset + 0];
            c[1] = 0;
            if ((c[0] & 0xff) < 0x20) {
                // ascii control char
                c[0] = unknownChar;
                String converted = new String(c, 0, 1, cs);
                dst.append(converted);
                pos++;
                return pos;
            } else if ((c[0] & 0xff) < 0x80) {
                // ascii visible char
                String converted = new String(c, 0, 1, cs);
                dst.append(converted);
                pos++;
                return pos;
            }

            CharsetDecoder decoder = cs.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);
            try {
                // Try to decode 1 byte
                String decodedStr = decoder.decode(java.nio.ByteBuffer.wrap(src, offset, 1)).toString();
                int len = decodedStr.length();

                if (len > 0) {
                    // Conversion successful
                    dst.append(decodedStr);
                    pos += len; // len will be 1 for single-byte encoding
                } else {
                    // Conversion failed
                    dst.append((char) (unknownChar & 0xff));
                    pos++;
                }
            } catch (Exception e) {
                // Conversion failed
                dst.append((char) (unknownChar & 0xff));
                pos++;
            }

            return pos;
        }
    }

    /**
     * Character code conversion map for Ascii 7-bit
     */
    static class CharCodeMap7 extends CharCodes.CharCodeMap {

        public CharCodeMap7() {
        }

        public CharCodeMap7(String n_name, int n_type) {
            super(n_name, n_type);
        }

        /**
         * Converts control codes (if necessary)
         *
         * @param str [in,out] String
         * @param len Buffer size
         */
        @Override
        public void convCtrlCodes(byte[] str, int len) {
            for (int i = 0; i < len; i++) {
                byte ch = str[i];
                int unsignedCh = ch & 0xFF;
                if (0x80 <= unsignedCh && unsignedCh <= 0x9f) {
                    ch &= 0x7f;
                    str[i] = ch;
                }
            }
        }

        /**
         * Converts a 1-byte character code to a string
         *
         * @param src         Character code
         * @param remain      Remaining bytes of src
         * @param dst         [out] String
         * @param unknownChar Character to replace with if conversion is not possible
         * @return Converted byte count
         */
        @Override
        public int findString(byte[] src, int offset, int remain, StringBuilder dst, byte unknownChar) {
            //bool match = false;
            int len = 0;

            if (remain == 0) return len;

            byte ch = src[offset + 0];

            if (0x80 <= (ch & 0xff)) {
                ch &= 0x7f;
            }
            if (0x20 <= (ch & 0xff) && (ch & 0xff) <= 0x7e) {
                dst.append((char) (ch & 0xff));
            } else {
                dst.append((char) (unknownChar & 0xff));
            }
            len = 1;

            return len;
        }

        /**
         * Checks if the character is in the character conversion table
         *
         * @param src String (1 character)
         * @param dst [in,out] Byte array Nullable
         * @param pos [out] Position if found
         * @return true: Match, false: No match
         */
        @Override
        public boolean findCode(String src, byte[] dst, int[] pos) {
            byte c = src.getBytes(StandardCharsets.US_ASCII)[0];
            if ((c & 0xFF) >= 0x80) {
                c &= 0x7f;
            }
            if (dst != null) {
                dst[pos[0]] = c;
            }
            pos[0]++;

            return true;
        }
    }

    /**
     * Character code selection list (List of CharCodeMap pointers)
     *
     * @see CharCodeMap
     */
    public static class CharCodeChoice {

        List<CharCodeMap> charCodeMaps = new ArrayList<>();

        private final String name;
        private final List<String> itemNames;

        public CharCodeChoice(String n_name, List<String> n_item_names) {
            name = n_name;
            itemNames = n_item_names;
        }

        /** Sets the maps to be used */
        public void assignMaps() {
            for (String itemName : itemNames) {
                CharCodeMap map = CharCodeMap.findMap(CharCodes.charCodeMaps, itemName);
                if (map != null) {
                    charCodeMaps.add(map);
                }
            }
        }

        /** Returns the selection list name */
        public final String getName() {
            return name;
        }

        /**
         * Returns the map name
         *
         * @param index Index
         */
        public final String getItemName(int index) {
            if (index < 0 || index >= charCodeMaps.size()) {
                index = 0;
            }
            return charCodeMaps.get(index).getName();
        }

        /**
         * Finds the map that matches the map name
         *
         * @param name Map name
         */
        public CharCodeMap Find(String name) {
            CharCodeMap match = null;
            for (CharCodeMap item : charCodeMaps) {
                if (name.equals(item.getName())) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /**
         * Finds the map that matches the map name
         *
         * @param name Map name
         */
        public int IndexOf(String name) {
            int match = 0;
            for (int i = 0; i < charCodeMaps.size(); i++) {
                if (name.equals(charCodeMaps.get(i).getName())) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        //
        // CharCodeChoices
        //

        /**
         * Sets the maps to be used
         */
        public static void assignMaps(List<CharCodeChoice> list) {
            for (CharCodeChoice item : list) {
                item.assignMaps();
            }
        }

        /**
         * Finds the selection list that matches the selection list name
         *
         * @param name List name
         */
        public static CharCodeChoice find(List<CharCodeChoice> list, String name) {
            CharCodeChoice match = null;
            for (CharCodeChoice item : list) {
                if (name.equals(item.getName())) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /**
         * Finds the map that matches the selection list name
         *
         * @param name      List name
         * @param itemIndex Position within the list
         */
        public static String getItemName(List<CharCodeChoice> list, String name, int itemIndex) {
            CharCodeChoice choice = find(list, name);
            if (choice != null) {
                return choice.getItemName(itemIndex);
            } else {
                return CharCodeMap.getMap(CharCodes.charCodeMaps, 0).getName();
            }
        }

        /**
         * Finds the map that matches the selection list name
         *
         * @param name     List name
         * @param itemName Map name
         */
        public static int indexOf(List<CharCodeChoice> list, String name, String itemName) {
            int idx = 0;
            CharCodeChoice choice = find(list, name);
            if (choice != null) {
                idx = choice.IndexOf(itemName);
            }
            return idx;
        }

        @Override public String toString() {
            return new StringJoiner(", ", CharCodeChoice.class.getSimpleName() + "[", "]")
                    .add("charCodeMaps=" + charCodeMaps)
                    .add("name='" + name + "'")
                    .add("itemNames=" + itemNames)
                    .toString();
        }
    }

    private CharCodeMap cache;

    public static List<CharCodeMap> charCodeMaps = new ArrayList<>();
    public static List<CharCodeChoice> charCodeChoices = new ArrayList<>();

    /**
     * Loads Maps element
     *
     * @param item          XML node
     * @param localeName    Locale name
     * @param errorMessages [out] Error messages
     * @return true / false
     */
    private static boolean loadMaps(Node item, String localeName, StringBuilder errorMessages) {
        item = item.getFirstChild();
        while (item != null) {
            if (item.getNodeName().equals("Map")) {
                String sName = ((Element) item).getAttribute("name");
                String sType = ((Element) item).getAttribute("type");
                long type = 0;
                CharCodeMap map;
                String desc = "", descLocale = "";
                try {
                    type = Long.parseLong(sType);
                } catch (NumberFormatException e) {
                    type = 0;
                }

                map = switch ((int) type) {
                    case 1 -> new CharCodeMap7(sName, (int) type);
                    case 2 -> new CharCodeMapMB(sName, (int) type);
                    case 3 -> new CharCodeMapSB(sName, (int) type);
                    default -> new CharCodeMap(sName, (int) type);
                };

                Node mitem = item.getFirstChild();
                while (mitem != null) {
                    switch (mitem.getNodeName()) {
                        case "Char" -> {
                            String codestr = ((Element) mitem).getAttribute("code");
                            String str = mitem.getTextContent();
                            CharCode p = new CharCode(str, codestr);
                            map.getList().add(p);
                        }
                        case "FontEncoding" -> {
                            String str = mitem.getTextContent();
                            map.setFontEncoding(str);
                        }
                        case "Description" -> {
                            if (!((Element) mitem).getAttribute("lang").isEmpty()) {
                                String lang = ((Element) mitem).getAttribute("lang");
                                if (localeName.contains(lang)) {
                                    descLocale = mitem.getTextContent();
                                }
                            } else {
                                desc = mitem.getTextContent();
                            }
                        }
                    }
                    mitem = mitem.getNextSibling();
                }
                if (!descLocale.isEmpty()) {
                    desc = descLocale;
                }
                map.initialize();
                map.setDescription(desc);
                if (CharCodeMap.findMap(charCodeMaps, sName) == null) {
                    charCodeMaps.add(map);
                } else {
                    errorMessages.append("\n");
                    errorMessages.append("Duplicate name in CharCodes::Maps : ");
                    errorMessages.append(sName);
                    return false;
                }
            }
            item = item.getNextSibling();
        }
logger.log(Level.TRACE, "charCodeMaps: " + charCodeMaps.size() + ", " + charCodeMaps);
        assert !charCodeMaps.isEmpty();
        return true;
    }

    /**
     * Loads Choices element
     *
     * @param item          XML node
     * @param localeName    Locale name
     * @param errorMessages [out] Error messages
     * @return true / false
     */
    private static boolean loadChoices(Node item, String localeName, StringBuilder errorMessages) {
        item = item.getFirstChild();
        while (item != null) {
            if (item.getNodeName().equals("Choice")) {
                String sName = ((Element) item).getAttribute("name");
                List<String> itemNames = new ArrayList<>();
                Node mItem = item.getFirstChild();
                while (mItem != null) {
                    if (mItem.getNodeName().equals("Item")) {
                        String str = mItem.getTextContent();
                        itemNames.add(str);
                    }
//#if 0
//				    else if (mItem.getNodeName().equals("Description")) {
//                        if (((Element) mItem).hasAttribute("lang")) {
//                            String lang = ((Element) mItem).getAttribute("lang");
//                            if (localeName.contains(lang)) {
//                                desc_locale = item.getNodeContent();
//                            }
//                        } else {
//                            desc = mItem.getNodeContent();
//                        }
//                    }
//#endif
                    mItem = mItem.getNextSibling();
                }
//#if 0
//                if (!desc_locale.IsEmpty()) {
//                    desc = desc_locale;
//                }
//#endif

                if (CharCodeChoice.find(charCodeChoices, sName) == null) {
                    charCodeChoices.add(new CharCodeChoice(sName, itemNames));
                } else {
                    errorMessages.append("\n");
                    errorMessages.append("Duplicate name in CharCodes::Choices : ");
                    errorMessages.append(sName);
                    return false;
                }
            }
            item = item.getNextSibling();
        }
logger.log(Level.TRACE, "charCodeChoices: " + charCodeChoices.size() + ", " + charCodeChoices);
        assert !charCodeChoices.isEmpty();
        return true;
    }

    public CharCodes() {
        cache = CharCodeMap.getMap(charCodeMaps, 0);
logger.log(Level.INFO, cache.name);
    }

    /**
     * Loads parameters from XML
     *
     * @param dataPath      Folder containing the XML file
     * @param localeName    Locale name
     * @param errorMessages [out] Error messages
     * @return true / false
     */
    public static boolean load(String dataPath, String localeName, StringBuilder errorMessages) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(dataPath + "char_codes.xml"));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }

        // start processing the XML file
        if (!doc.getDocumentElement().getNodeName().equals("CharCodes")) {
            logger.log(Level.ERROR, doc.getDocumentElement());
            return false;
        }

        boolean sts = true;
        Node item = doc.getDocumentElement().getFirstChild();
        while (item != null && sts) {
            if (item.getNodeName().equals("Maps")) {
                sts = loadMaps(item, localeName, errorMessages);
            } else if (item.getNodeName().equals("Choices")) {
                sts = loadChoices(item, localeName, errorMessages);
            }
            item = item.getNextSibling();
        }
        if (sts) {
            CharCodeChoice.assignMaps(charCodeChoices);
        }
        return sts;
    }

    /**
     * Converts character codes to a string
     *
     * @param src       Character code sequence
     * @param len       src byte count
     * @param dst       [out] String
     * @param terminalCode Termination code, -1: for all bytes
     */
    public void convToString(byte[] src, int offset, int len, StringBuilder dst, int terminalCode) {
        for (int i = 0; i < len; ) {
            if (terminalCode >= 0 && src[offset] == (byte) terminalCode) break;
            int remaining = len - i;
            int bytesToInspect = (remaining >= 2) ? 2 : remaining;
            int l = cache.findString(src, offset, bytesToInspect, dst, (byte) '_');
            if (l <= 0) l = 1;
            i += l;
            offset += l;
        }
    }

    /**
     * Converts a string to character codes
     *
     * @param src String
     * @param dst [out] Character code sequence
     * @param len dst buffer size
     * @return >=0: byte count, -1: Conversion failed
     */
    public int convToChars(String src, byte[] dst, int len) {
        boolean rc = true;
        int[] pos = {0};

        for (int i = 0; i < src.length() && pos[0] < (len - 1) && rc; i++) {
            String singleChar = src.substring(i, i + 1);
            rc = cache.findCode(singleChar, dst, pos);
        }
        if (dst != null && pos[0] < len) dst[pos[0]] = '\0';
        return rc ? pos[0] : -1;
    }

    /**
     * Converts control codes (if necessary)
     *
     * @param str [in,out] String
     * @param len Buffer size
     */
    public void convCtrlCodes(byte[] str, int len) {
        cache.convCtrlCodes(str, len);
    }

    /**
     * Checks if the character code is in the character conversion table
     *
     * @param src         Character code (1-2 bytes)
     * @param len         src byte count
     * @param dst         [out] String
     * @param unknownChar Character to replace with if conversion is not possible
     * @return Converted byte count
     */
    public int findString(byte[] src, int len, StringBuilder dst, byte unknownChar) {
        return cache.findString(src, 0, len, dst, unknownChar);
    }

    /**
     * Checks if the character is in the character conversion table
     *
     * @param src String (1 character)
     * @param dst [in,out] Byte array Nullable
     * @param pos [out] Position if found
     * @return true: Match, false: No match
     */
    public boolean findCode(String src, byte[] dst, int[] pos) {
        return cache.findCode(src, dst, pos);
    }

    /**
     * Sets the map
     *
     * @param name Map name
     */
    public void setMap(String name) {
        CharCodes.CharCodeMap newCache = CharCodeMap.findMap(charCodeMaps, name);
        if (newCache != null) {
            cache = newCache;
        } else {
            cache = CharCodeMap.getMap(charCodeMaps, 0);
        }
    }

    /**
     * Sets the map
     *
     * @param index Map number
     */
    public void setMap(int index) {
        CharCodes.CharCodeMap newCache = CharCodeMap.getMap(charCodeMaps, index);
        if (newCache != null) {
            cache = newCache;
        } else {
            cache = CharCodeMap.getMap(charCodeMaps, 0);
        }
    }
}
