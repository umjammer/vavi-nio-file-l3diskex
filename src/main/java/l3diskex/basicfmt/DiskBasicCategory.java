/*
 * @author Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * DISK BASIC category class (grouped by manufacturer, OS, etc.)
 */
public class DiskBasicCategory {

    private String name;
    private String description;

    public DiskBasicCategory() {
    }

    public DiskBasicCategory(DiskBasicCategory src) {
        this.name = src.name;
        this.description = src.description;
    }

    public DiskBasicCategory(String n_name, String n_description) {
        this.name = n_name;
        this.description = n_description;
    }

    /** Category name */
    public String getName() {
        return name;
    }

    /** Description */
    public String getDescription() {
        return description;
    }

    /** Set description */
    public void setDescription(String str) {
        description = str;
    }

    /**
     * Load DiskBasicCategory element
     *
     * @param node       Node
     * @param localeName Locale name
     * @param errMsgs    [out] Error messages
     * @return true / false
     * @see "category_types.xml"
     */
    public static boolean load(List<DiskBasicCategory> list, Node node, String localeName, StringBuilder errMsgs) {
        boolean valid = false;

        while (node != null && !valid) {
            if ("DiskBasicCategories".equals(node.getNodeName())) {
                valid = true;
                break;
            }
            node = node.getNextSibling();
        }
        if (!valid) {
            return false;
        }

        Node item = node.getFirstChild();
        while (item != null && valid) {
            if ("DiskBasicCategory".equals(item.getNodeName())) {
                String type_name = ((Element) item).getAttribute("name");
                String desc = "";
                String descLocale = "";

                Node itemNode = item.getFirstChild();
                while (itemNode != null) {
                    if ("Description".equals(itemNode.getNodeName())) {
                        if (((Element) itemNode).hasAttribute("lang")) {
                            String lang = ((Element) itemNode).getAttribute("lang");
                            if (localeName.contains(lang)) {
                                descLocale = itemNode.getTextContent();
                            }
                        } else {
                            desc = itemNode.getTextContent();
                        }
                    }
                    itemNode = itemNode.getNextSibling();
                }
                if (!descLocale.isEmpty()) {
                    desc = descLocale;
                }

                DiskBasicCategory c = new DiskBasicCategory(type_name, desc);

                if (find(list, type_name) == null) {
                    list.add(c);
                } else {
                    errMsgs.append("\n");
                    errMsgs.append("Duplicate type name in DiskBasicCategory : ");
                    errMsgs.append(type_name);
                    valid = false;
                    break;
                }
            }
            item = item.getNextSibling();
        }
        return valid;
    }

    /**
     * Search category
     *
     * @param category Category name
     * @return Category
     */
    public static DiskBasicCategory find(List<DiskBasicCategory> list, String category) {
        for (DiskBasicCategory item : list) {
            if (item.getName().equals(category)) {
                return item;
            }
        }
        return null;
    }
}
