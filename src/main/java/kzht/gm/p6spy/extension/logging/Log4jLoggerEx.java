/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kzht.gm.p6spy.extension.logging;

import com.p6spy.engine.common.P6SpyProperties;
import com.p6spy.engine.logging.appender.Log4jLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * P6spy 用の拡張 Log4j ロガークラス <br />
 *
 * {@link com.p6spy.engine.logging.appender.Log4jLogger} は出力カテゴリー、
 * 非出力カテゴリーの設定が正しく動作しないので、期待通りの動作になるように拡張した。
 *
 * @author kazuhito
 *
 */
@Data
public class Log4jLoggerEx
        extends Log4jLogger {

    /**
     * デフォルトフォーマット
     */
    private static final String DEFAULT_FORMAT = "%e[ms] %s";

    /**
     * 非出力カテゴリー
     */
    private List<String> excludeCategories;

    /**
     * 出力カテゴリー
     */
    private List<String> includeCategories;

    /**
     * PreparedStatement の出力要否
     */
    private boolean preparedEnabled = true;

    /**
     * Statement の出力要否
     */
    private boolean sqlEnabled = true;

    /**
     * DbUnit によって発行されるクエリーの出力要否 <br />
     * スタックトレースから判定するため出力抑止の場合は多少コストが掛かる。
     */
    private boolean dbunitEnabled = true;

    /**
     * 出力フォーマット <br />
     *
     * 以下のプレースホルダーが指定可能である。これら以外のものは全て文字列として識別される。
     * <ul>
     * <li>%cat : カテゴリー</li>
     * <li>%cid : コネクション ID</li>
     * <li>%e : 経過時間 (msec)</li>
     * <li>%s : ステートメント</li>
     * </ul>
     */
    private String format;

    /**
     * コンストラクター
     */
    public Log4jLoggerEx() {
        P6SpyProperties p6SpyProperties = new P6SpyProperties();
        Properties properties = p6SpyProperties.forceReadProperties();

        excludeCategories = extractCategory(properties.getProperty("excludecategories"));
        includeCategories = extractCategory(properties.getProperty("includecategories"));

        preparedEnabled
                = extractBooleanValue(properties.getProperty("commons.enable.preparedstatement"));
        sqlEnabled = extractBooleanValue(properties.getProperty("commons.enable.sqlstatement"));

        format = extractFormat(properties.getProperty("commons.format"));

        dbunitEnabled
                = extractBooleanValue(properties.getProperty("commons.enable.dbunitstatement"));
    }

    /**
     * 設定に応じてクエリーを出力する。<br />
     *
     * @param connectionId コネクション ID
     * @param now 現在時刻 (使用しない)
     * @param elapsed 経過時間 (msec)
     * @param category カテゴリー
     * @param prepared パラメーターライズドクエリー
     * @param sql SQL クエリー
     */
    @Override
    public void logSQL(int connectionId, String now, long elapsed, String category,
            String prepared, String sql) {
        if (shouldWrite(category)) {
            if (preparedEnabled && StringUtils.isNotBlank(prepared)) {
                super.logText(format(connectionId, now, elapsed, category, prepared));
            }

            if (sqlEnabled && StringUtils.isNotBlank(sql)) {
                super.logText(format(connectionId, now, elapsed, category, sql));
            }
        }
    }

    /**
     * 指定フォーマットに従って出力メッセージを整形する。
     *
     * ちょっと効率悪いかも...
     *
     * @param connectionId コネクション ID
     * @param now 現在時刻 (使用しない)
     * @param elapsed 経過時間 (msec)
     * @param category カテゴリー
     * @param statement ステートメント
     * @return 整形された出力メッセージ
     */
    private String format(int connectionId, String now, long elapsed, String category,
            String statement) {
        return format.replace("%cid", String.valueOf(connectionId)).replace("%e",
                String.valueOf(elapsed)).replace("%cat", category).replace("%s", statement);
    }

    /**
     * カテゴリーと出力コンポーネント (DbUnit or アプリケーション) から出力対象かどうかを判定する。<br />
     *
     * @param category ログカテゴリー
     * @return 出力対象の場合は <code>true</code>, そうでない場合は <code>false</code> を返却する。
     */
    private boolean shouldWrite(String category) {
        // P6spy のデフォルト動作では exclude 設定の方が優先されるので踏襲する。
        if (excludeCategories.contains(category)) {
            return false;
        }

        if (includeCategories.isEmpty() || includeCategories.contains(category)) {
            if (dbunitEnabled) {
                return true;
            } else {
                return isApplicationQuery();
            }
        } else {
            return false;
        }
    }

    /**
     * アプリケーション (Hibernate) が発行したクエリーなのかどうかを判定する。<br />
     * 判定はスタックトレースから行い、文字列 : "SessionImpl" が見つかったらアプリケーションから発行されたものと見なす。
     *
     * @return アプリケーションが発行した場合は <code>true</code>, そうでない場合は <code>false</code>
     * を返却する。
     */
    private boolean isApplicationQuery() {
        for (StackTraceElement ste : new Throwable().getStackTrace()) {
            if (ste.toString().indexOf("SessionImpl") >= 0) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractCategory(String categories) {
        List<String> categoryList = new ArrayList<String>();

        if (StringUtils.isNotBlank(categories)) {
            for (String category : categories.trim().split(",")) {
                categoryList.add(category.trim());
            }
        }

        return categoryList;
    }

    private boolean extractBooleanValue(String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        } else {
            return Boolean.valueOf(value).booleanValue();
        }
    }

    private String extractFormat(String format) {
        if (StringUtils.isBlank(format)) {
            return DEFAULT_FORMAT;
        } else {
            return format;
        }
    }
}
