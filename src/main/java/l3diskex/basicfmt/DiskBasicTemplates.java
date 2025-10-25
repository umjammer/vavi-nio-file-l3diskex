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
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormats;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicParams;
import l3diskex.diskimg.DiskParam.DiskParamName;
import org.xml.sax.SAXException;

import static l3diskex.basicfmt.DiskBasicCategory.find;


/**
 * DISK BASICパラメータのテンプレートを提供する
 */
public class DiskBasicTemplates extends TemplatesBase {

    private static final Logger logger = System.getLogger(DiskBasicTemplates.class.getName());

    public static final DiskBasicTemplates gDiskBasicTemplates = new DiskBasicTemplates();

    private final DiskBasicFormats formats = new DiskBasicFormats();
    private final DiskBasicParams types = new DiskBasicParams();
    private final List<DiskBasicCategory> categories = new ArrayList<>();

    private DiskBasicTemplates() {
    }

    /**
     * XMLファイル読み込み
     *
     * @param data_path   XMLファイルがあるフォルダ
     * @param locale_name ローケル名
     * @param errmsgs     エラーメッセージ
     * @return true/false
     * @see "basic_types.xml"
     */
    public boolean load(String data_path, String locale_name, StringBuilder errmsgs) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(data_path + "basic_types.xml"));
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
        valid = formats.load(doc.getDocumentElement().getFirstChild(), locale_name, errmsgs);
        assert !formats.list.isEmpty();
        if (!valid) {
logger.log(Level.ERROR, "formats.load");
            return false;
        }
logger.log(Level.INFO, "formats: " + formats.list.size());

        valid = types.load(doc.getDocumentElement().getFirstChild(), locale_name, formats, errmsgs);
        assert !types.list.isEmpty();
        if (!valid) {
logger.log(Level.ERROR, "types.load");
            return false;
        }
logger.log(Level.INFO, "types: " + types.list.size());

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(data_path + "category_types.xml"));
        } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }

        valid = DiskBasicCategory.load(categories, doc.getDocumentElement(), locale_name, errmsgs);
        assert !categories.isEmpty();
        if (!valid) {
logger.log(Level.ERROR, "categories.load");
        }
logger.log(Level.INFO, "categories: " + categories.size());

        return valid;
    }

    /**
     * カテゴリとタイプに一致するパラメータを検索
     *
     * @param n_category   カテゴリ名 空文字列の場合は検索条件からはずす
     * @param n_basic_type タイプ名
     * @return 一致したパラメータ
     */
    public DiskBasicParam findType(String n_category, String n_basic_type) {
        return types.find(n_category, n_basic_type);
    }

    /**
     * カテゴリが一致し、タイプリストに含まれるパラメータを検索
     *
     * @param n_category    カテゴリ名 空文字列の場合は検索条件からはずす
     * @param n_basic_types タイプ名リスト
     * @return 一致したパラメータ
     */
    public DiskBasicParam findType(String n_category, List<DiskParamName> n_basic_types) {
        return types.find(n_category, n_basic_types);
    }

    /**
     * カテゴリ、タイプ、サイド数とセクタ数が一致するパラメータを検索
     * まず、カテゴリ＆タイプで検索し、なければカテゴリ＆サイド数＆セクタ数で検索
     *
     * @param n_category   カテゴリ名 必須
     * @param n_basic_type タイプ名 必須
     * @param n_sides      サイド数
     * @param n_sectors    セクタ数/トラック -1の場合は検索条件からはずす
     * @return 一致したパラメータ
     */
    public DiskBasicParam findType(String n_category, String n_basic_type, int n_sides, int n_sectors) {
        return types.find(n_category, n_basic_type, n_sides, n_sectors);
    }

    /**
     * DISK BASICフォーマット種類に一致するタイプを検索
     *
     * @param n_format_types DISK BASICフォーマット種類
     * @param n_types        [out] 一致したタイプリスト
     * @return リストの数
     */
    public int findTypes(List<Integer> n_format_types, DiskBasicParams n_types) {
        return types.findTypes(n_format_types, n_types);
    }

    /**
     * カテゴリ番号に一致するタイプ名リストを検索
     *
     * @param n_category_index カテゴリ番号
     * @param n_type_names     [out]      タイプ名リスト
     * @return リストの数
     */
    public int findTypeNames(int n_category_index, List<String> n_type_names) {
        return types.findNames(categories.get(n_category_index).getName(), n_type_names);
    }

    /**
     * カテゴリ名に一致するタイプ名リストを検索
     *
     * @param n_category_name カテゴリ名
     * @param n_type_names    [out] タイプ名リスト
     * @return リストの数
     */
    public int findTypeNames(String n_category_name, List<String> n_type_names) {
        return types.findNames(n_category_name, n_type_names);
    }

    /**
     * フォーマット種類を検索
     *
     * @param format_type フォーマット種類
     */
    public DiskBasicFormat findFormat(DiskBasicFormatType format_type) {
        return formats.find(format_type);
    }

    /**
     * タイプリストと一致するパラメータを得る
     * パラメータリストは説明文でソートする
     *
     * @param n_type_names タイプ名リスト
     * @param params       [out] パラメータリスト
     * @return パラメータリストの数
     */
    public int findParams(List<DiskParamName> n_type_names, DiskBasicParams params) {
        for (int n = 0; n < n_type_names.size(); n++) {
            DiskBasicParam param = findType("", n_type_names.get(n).getName());
            if (param == null) continue;
            params.list.add(param);
        }
        params.list.sort(Comparator.comparing(DiskBasicParam::getBasicDescription));
        return params.list.size();
    }

    /**
     * カテゴリを検索
     *
     * @param n_category カテゴリ名
     * @return カテゴリ
     */
    public DiskBasicCategory findCategory(String n_category) {
        return find(categories, n_category);
    }

    /**
     * カテゴリ名を返す
     *
     * @param idx インデックス
     * @return カテゴリ名
     */
    public String getCategoryName(int idx) {
        return categories.get(idx).getName();
    }
}
