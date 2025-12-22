/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;

import l3diskex.Parambase.TemplatesBase;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormats;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicParams;
import l3diskex.diskimg.DiskParam.DiskParamName;
import org.xml.sax.SAXException;

import static l3diskex.basicfmt.DiskBasicCategory.find;


/**
 * Provides templates for DISK BASIC parameters
 */
public class DiskBasicTemplates extends TemplatesBase {

    private static final Logger logger = System.getLogger(DiskBasicTemplates.class.getName());

    public static final DiskBasicTemplates diskBasicTemplates = new DiskBasicTemplates();

    private final DiskBasicFormats formats = new DiskBasicFormats();
    private final DiskBasicParams types = new DiskBasicParams();
    private final List<DiskBasicCategory> categories = new ArrayList<>();

    private DiskBasicTemplates() {
    }

    /**
     * Read XML file
     *
     * @param dataPath   Folder where XML files are located
     * @param localeName Locale name
     * @param errMsgs    Error messages
     * @return true/false
     * @see "basicTypes.xml"
     */
    public boolean load(String dataPath, String localeName, StringBuilder errMsgs) {
        categories.clear();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(dataPath + "basic_types.xml"));
        } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }

        // start processing the XML file
        if (!doc.getDocumentElement().getNodeName().equals("DiskBasics")) {
logger.log(Level.ERROR, doc.getDocumentElement());
            return false;
        }

        boolean valid;
        valid = formats.load(doc.getDocumentElement().getFirstChild(), localeName, errMsgs);
        assert !formats.list.isEmpty();
        if (!valid) {
logger.log(Level.ERROR, "formats.load");
            return false;
        }
logger.log(Level.INFO, "formats: " + formats.list.size());

        valid = types.load(doc.getDocumentElement().getFirstChild(), localeName, formats, errMsgs);
        assert !types.list.isEmpty();
        if (!valid) {
logger.log(Level.ERROR, "types.load");
            return false;
        }
logger.log(Level.INFO, "types: " + types.list.size());

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(dataPath + "category_types.xml"));
        } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }

        valid = DiskBasicCategory.load(categories, doc.getDocumentElement(), localeName, errMsgs);
        assert !categories.isEmpty();
        if (!valid) {
logger.log(Level.ERROR, "categories.load");
        }
logger.log(Level.INFO, "categories: " + categories.size());

        return valid;
    }

    /**
     * Search for parameters matching category and type
     *
     * @param category   Category name; if empty string, excluded from search conditions
     * @param basicType Type name
     * @return Matched parameters
     */
    public DiskBasicParam findType(String category, String basicType) {
        return types.find(category, basicType);
    }

    /**
     * Search for parameters whose category matches and is included in the type list
     *
     * @param category    Category name; if empty string, excluded from search conditions
     * @param basicTypes Type name list
     * @return Matched parameters
     */
    public DiskBasicParam findType(String category, List<DiskParamName> basicTypes) {
        return types.find(category, basicTypes);
    }

    /**
     * Search for parameters matching category, type, number of sides, and number of sectors
     * First, search by category & type, and if not found, search by category & number of sides & number of sectors
     *
     * @param category   Category name (required)
     * @param basicType Type name (required)
     * @param sides      Number of sides
     * @param sectors    Number of sectors/track; if -1, excluded from search conditions
     * @return Matched parameters
     */
    public DiskBasicParam findType(String category, String basicType, int sides, int sectors) {
        return types.find(category, basicType, sides, sectors);
    }

    /**
     * Search for types matching DISK BASIC format type
     *
     * @param formatTypes DISK BASIC format types
     * @param types        [out] Matched type list
     * @return Number of items in the list
     */
    public int findTypes(List<Integer> formatTypes, DiskBasicParams types) {
        return this.types.findTypes(formatTypes, types);
    }

    /**
     * Search for type name list matching category number
     *
     * @param categoryIndex Category number
     * @param typeNames     [out] Type name list
     * @return Number of items in the list
     */
    public int findTypeNames(int categoryIndex, List<String> typeNames) {
        return types.findNames(categories.get(categoryIndex).getName(), typeNames);
    }

    /**
     * Search for type name list matching category name
     *
     * @param categoryName Category name
     * @param typeNames    [out] Type name list
     * @return Number of items in the list
     */
    public int findTypeNames(String categoryName, List<String> typeNames) {
        return types.findNames(categoryName, typeNames);
    }

    /**
     * Search for format type
     *
     * @param formatType Format type
     */
    public DiskBasicFormat findFormat(int formatType) {
        return formats.find(formatType);
    }

    /**
     * Obtain parameters matching the type list
     * Sort parameter list by description
     *
     * @param typeNames Type name list
     * @param params       [out] Parameter list
     * @return Number of items in the parameter list
     */
    public int findParams(List<DiskParamName> typeNames, DiskBasicParams params) {
        for (DiskParamName typeName : typeNames) {
            DiskBasicParam param = findType("", typeName.getName());
            if (param == null) continue;
            params.list.add(param);
        }
        params.list.sort(Comparator.comparing(DiskBasicParam::getBasicDescription));
        return params.list.size();
    }

    /**
     * Search for category
     *
     * @param category Category name
     * @return Category
     */
    public DiskBasicCategory findCategory(String category) {
        return find(categories, category);
    }

    /**
     * Returns category name
     *
     * @param index Index
     * @return Category name
     */
    public String getCategoryName(int index) {
        return categories.get(index).getName();
    }
}
