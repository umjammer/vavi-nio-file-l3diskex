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
     * @param dataPath   XMLファイルがあるフォルダ
     * @param localeName ローケル名
     * @param errMsgs     エラーメッセージ
     * @return true/false
     * @see "basicTypes.xml"
     */
    public boolean load(String dataPath, String localeName, StringBuilder errMsgs) {
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
     * カテゴリとタイプに一致するパラメータを検索
     *
     * @param category   カテゴリ名 空文字列の場合は検索条件からはずす
     * @param basicType タイプ名
     * @return 一致したパラメータ
     */
    public DiskBasicParam findType(String category, String basicType) {
        return types.find(category, basicType);
    }

    /**
     * カテゴリが一致し、タイプリストに含まれるパラメータを検索
     *
     * @param category    カテゴリ名 空文字列の場合は検索条件からはずす
     * @param basicTypes タイプ名リスト
     * @return 一致したパラメータ
     */
    public DiskBasicParam findType(String category, List<DiskParamName> basicTypes) {
        return types.find(category, basicTypes);
    }

    /**
     * カテゴリ、タイプ、サイド数とセクタ数が一致するパラメータを検索
     * まず、カテゴリ＆タイプで検索し、なければカテゴリ＆サイド数＆セクタ数で検索
     *
     * @param category   カテゴリ名 必須
     * @param basicType タイプ名 必須
     * @param sides      サイド数
     * @param sectors    セクタ数/トラック -1の場合は検索条件からはずす
     * @return 一致したパラメータ
     */
    public DiskBasicParam findType(String category, String basicType, int sides, int sectors) {
        return types.find(category, basicType, sides, sectors);
    }

    /**
     * DISK BASICフォーマット種類に一致するタイプを検索
     *
     * @param formatTypes DISK BASICフォーマット種類
     * @param types        [out] 一致したタイプリスト
     * @return リストの数
     */
    public int findTypes(List<Integer> formatTypes, DiskBasicParams types) {
        return this.types.findTypes(formatTypes, types);
    }

    /**
     * カテゴリ番号に一致するタイプ名リストを検索
     *
     * @param categoryIndex カテゴリ番号
     * @param typeNames     [out] タイプ名リスト
     * @return リストの数
     */
    public int findTypeNames(int categoryIndex, List<String> typeNames) {
        return types.findNames(categories.get(categoryIndex).getName(), typeNames);
    }

    /**
     * カテゴリ名に一致するタイプ名リストを検索
     *
     * @param categoryName カテゴリ名
     * @param typeNames    [out] タイプ名リスト
     * @return リストの数
     */
    public int findTypeNames(String categoryName, List<String> typeNames) {
        return types.findNames(categoryName, typeNames);
    }

    /**
     * フォーマット種類を検索
     *
     * @param formatType フォーマット種類
     */
    public DiskBasicFormat findFormat(int formatType) {
        return formats.find(formatType);
    }

    /**
     * タイプリストと一致するパラメータを得る
     * パラメータリストは説明文でソートする
     *
     * @param typeNames タイプ名リスト
     * @param params       [out] パラメータリスト
     * @return パラメータリストの数
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
     * カテゴリを検索
     *
     * @param category カテゴリ名
     * @return カテゴリ
     */
    public DiskBasicCategory findCategory(String category) {
        return find(categories, category);
    }

    /**
     * カテゴリ名を返す
     *
     * @param index インデックス
     * @return カテゴリ名
     */
    public String getCategoryName(int index) {
        return categories.get(index).getName();
    }
}
