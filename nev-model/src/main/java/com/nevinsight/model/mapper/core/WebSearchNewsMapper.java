package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.WebSearchNews;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WebSearchNewsMapper extends BaseMapper<WebSearchNews> {

    @Select("SELECT * FROM web_search_news WHERE brand_name = #{brand} AND crawl_date = #{date} " +
            "ORDER BY relevance_score DESC LIMIT #{limit}")
    List<WebSearchNews> findTopByBrandAndDate(
            @Param("brand") String brand,
            @Param("date") LocalDate date,
            @Param("limit") int limit);

    /** 32h 窗口内某 category 的新闻（self/competitor/industry），按 add_ts 倒序。 */
    @Select("SELECT * FROM web_search_news " +
            "WHERE category = #{category} AND add_ts >= #{sinceMs} " +
            "ORDER BY add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findByCategoryAndAddTsAfter(
            @Param("category") String category,
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /** 找未分类的 N 条（extract_ts IS NULL）—— PR11 用。 */
    @Select("SELECT * FROM web_search_news WHERE extract_ts IS NULL " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<WebSearchNews> findPendingExtraction(@Param("limit") int limit);

    /** Bocha 网页全文补采候选：content 仍像搜索摘要的近 24h 网页。 */
    @Select("SELECT * FROM web_search_news " +
            "WHERE source_tool = 'bocha' AND add_ts >= #{sinceMs} " +
            "  AND url IS NOT NULL AND url <> '' " +
            "  AND (content IS NULL OR CHAR_LENGTH(content) < 2500) " +
            "ORDER BY add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findBochaFullTextCandidates(
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /** 写回网页全文；重置 extract_ts，让事件分类基于全文重跑。 */
    @org.apache.ibatis.annotations.Update(
        "UPDATE web_search_news SET content=#{content}, extract_ts=NULL, last_modify_ts=#{ts} WHERE id=#{id}")
    int updateFullContent(@Param("id") Long id,
                          @Param("content") String content,
                          @Param("ts") Long ts);

    /** UPDATE event 5 字段（PR11 写回）。 */
    @org.apache.ibatis.annotations.Update(
        "UPDATE web_search_news SET event_type=#{eventType}, event_importance=#{importance}, " +
        "event_summary=#{summary}, model_mentioned=#{models}, extract_ts=#{extractTs} WHERE id=#{id}")
    int updateEventFields(@Param("id") Long id,
                          @Param("eventType") String eventType,
                          @Param("importance") Integer importance,
                          @Param("summary") String summary,
                          @Param("models") String models,
                          @Param("extractTs") Long extractTs);

    /** PR12 用：按 event_type 拉 32h 内事件，importance DESC + add_ts DESC。 */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type = #{eventType} AND add_ts >= #{sinceMs} " +
            "ORDER BY event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findByEventType(
            @Param("eventType") String eventType,
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /** 按事件类型取 32h 内事件：微博官号 > 其他官号 > 网页补采。 */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type = #{eventType} AND add_ts >= #{sinceMs} " +
            "ORDER BY CASE " +
            "    WHEN source_tool = 'weibo_official' THEN 0 " +
            "    WHEN source_tool LIKE '%\\_official' ESCAPE '\\\\' THEN 1 " +
            "    ELSE 2 END, " +
            "  event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findByEventTypeOfficialFirst(
            @Param("eventType") String eventType,
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /** 竞品日报「市场热度」：不限对标品牌，先取较宽候选池，展示层再按市场信号收敛。 */
    @Select("SELECT * FROM web_search_news " +
            "WHERE category = 'industry' AND add_ts >= #{sinceMs} " +
            "  AND event_type IN ('launch', 'price_finance', 'campaign', 'sales_milestone', 'other') " +
            "  AND event_importance >= 4 " +
            "ORDER BY CASE " +
            "    WHEN url LIKE '%autohome.com.cn%' OR url LIKE '%dongchedi.com%' THEN 0 " +
            "    WHEN url LIKE '%yiche.com%' OR url LIKE '%pcauto.com.cn%' OR url LIKE '%gasgoo.com%' THEN 1 " +
            "    WHEN url LIKE '%auto.sina.com.cn%' OR url LIKE '%auto.sohu.com%' OR url LIKE '%k.sina.com.cn%' THEN 2 " +
            "    ELSE 9 END, " +
            "  event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findMarketHotEvents(
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /** 竞品日报「战略动作」：取近 72h 固定对标品牌的宽候选池，应用层再按战略信号收敛。 */
    @Select("SELECT * FROM web_search_news " +
            "WHERE add_ts >= #{sinceMs} " +
            "  AND brand_name IN ('猛士','仰望','坦克','方程豹','问界','路虎') " +
            "  AND (event_type IS NULL OR event_type IN ('launch','price_finance','campaign','sales_milestone','other')) " +
            "ORDER BY CASE " +
            "    WHEN source_tool = 'weibo_official' THEN 0 " +
            "    WHEN source_tool LIKE '%\\_official' ESCAPE '\\\\' THEN 1 " +
            "    ELSE 2 END, " +
            "  event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findStrategicActionCandidates(
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /**
     * 竞品动态卡片「产品动态」板块：优先取微博官号/其他官号，官号不足时用网页补采兜底。
     * launch 事件继续由分类任务识别，展示层按车型聚合总结。
     */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type = 'launch' AND add_ts >= #{sinceMs} " +
            "ORDER BY CASE " +
            "    WHEN source_tool = 'weibo_official' THEN 0 " +
            "    WHEN source_tool LIKE '%\\_official' ESCAPE '\\\\' THEN 1 " +
            "    ELSE 2 END, " +
            "  event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findProductEventsOfficialFirst(
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /**
     * v7 销量板块专用：只取官号一手数据（source_tool 以 _official 结尾）的 sales_milestone 事件。
     * 严格白名单，过滤 Bocha 二手新闻；importance >= 5 防止低质量帖混入。
     */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type = 'sales_milestone' AND add_ts >= #{sinceMs} " +
            "  AND event_importance >= 5 " +
            "  AND source_tool LIKE '%\\_official' ESCAPE '\\\\' " +
            "ORDER BY event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findAuthoritativeSalesEvents(
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /**
     * v7 销量板块「每品牌最新一条」：不限时间窗，每个 brand_name 取最新的权威销量事件。
     * 用窗口函数 ROW_NUMBER() PARTITION BY brand_name ORDER BY add_ts DESC（MySQL 8+）。
     * 仍要求 importance>=5 + source_tool 以 _official 结尾。
     */
    @Select("SELECT * FROM ( " +
            "  SELECT *, ROW_NUMBER() OVER (PARTITION BY brand_name ORDER BY add_ts DESC) AS rn " +
            "  FROM web_search_news " +
            "  WHERE event_type = 'sales_milestone' " +
            "    AND event_importance >= 5 " +
            "    AND source_tool LIKE '%\\_official' ESCAPE '\\\\' " +
            ") t WHERE rn = 1 ORDER BY add_ts DESC")
    List<WebSearchNews> findLatestAuthoritativeSalesByBrand();

    /**
     * v8: 按 event_type + 仅官号源（source_tool 以 _official 结尾）+ 32h 窗口取事件。
     * 用于 price_finance 板块严格官号过滤。
     */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type = #{eventType} AND add_ts >= #{sinceMs} " +
            "  AND source_tool LIKE '%\\_official' ESCAPE '\\\\' " +
            "ORDER BY event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findOfficialEventsByType(
            @Param("eventType") String eventType,
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /**
     * v8: 找出需要 OCR 增强的官号条目（产品动态 + 价格金融 + 营销传播有图但未 OCR）。
     * 海报里常含配置参数、权益价格和活动机制，需要回填到卡片各板块。
     */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type IN ('price_finance', 'launch', 'campaign') " +
            "  AND source_tool LIKE '%\\_official' ESCAPE '\\\\' " +
            "  AND image_urls IS NOT NULL AND image_urls <> '' " +
            "  AND ocr_ts IS NULL " +
            "ORDER BY (event_type='price_finance') DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findOcrPending(@Param("limit") int limit);

    /**
     * v8: 写回 OCR 结果 + 提权 importance + 标 ocr_ts。
     */
    @org.apache.ibatis.annotations.Update(
        "UPDATE web_search_news SET image_ocr_text = #{ocrText}, " +
        "  event_summary = #{summary}, event_importance = #{importance}, " +
        "  ocr_ts = #{ocrTs} WHERE id = #{id}")
    int updateOcrFields(@Param("id") Long id,
                        @Param("ocrText") String ocrText,
                        @Param("summary") String summary,
                        @Param("importance") Integer importance,
                        @Param("ocrTs") Long ocrTs);

    /** v8: 即使 OCR 失败/无政策也标 ocr_ts，避免重跑。 */
    @org.apache.ibatis.annotations.Update(
        "UPDATE web_search_news SET ocr_ts = #{ocrTs} WHERE id = #{id}")
    int markOcrDone(@Param("id") Long id, @Param("ocrTs") Long ocrTs);

    /**
     * v9 聚合：只取明确权益/金融政策，纯售价/指导价不进入「价格 & 金融政策」。
     * 排序上仍保持微博官号/官号优先，网页补采仅作兜底。
     * 用于统一展示「价格 & 金融政策」板块，避免把普通上市定价从 launch 板块误挪走。
     */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type IN ('price_finance','launch','campaign') " +
            "  AND add_ts >= #{sinceMs} " +
            "  AND ( event_summary LIKE '%权益%' " +
            "     OR event_summary LIKE '%优惠%' " +
            "     OR event_summary LIKE '%补贴%' " +
            "     OR event_summary LIKE '%置换%' " +
            "     OR event_summary LIKE '%0息%' " +
            "     OR event_summary LIKE '%免息%' " +
            "     OR event_summary LIKE '%低息%' " +
            "     OR event_summary LIKE '%金融%' " +
            "     OR event_summary LIKE '%保险%' " +
            "     OR event_summary LIKE '%首付%' " +
            "     OR event_summary LIKE '%订金%' " +
            "     OR event_summary LIKE '%定金%' " +
            "     OR event_summary LIKE '%抵扣%' " +
            "     OR event_summary LIKE '%减免%' " +
            "     OR content LIKE '%权益%' " +
            "     OR content LIKE '%优惠%' " +
            "     OR content LIKE '%补贴%' " +
            "     OR content LIKE '%置换%' " +
            "     OR content LIKE '%0息%' " +
            "     OR content LIKE '%免息%' " +
            "     OR content LIKE '%低息%' " +
            "     OR content LIKE '%金融%' " +
            "     OR content LIKE '%保险%' " +
            "     OR content LIKE '%首付%' " +
            "     OR content LIKE '%订金%' " +
            "     OR content LIKE '%定金%' " +
            "     OR content LIKE '%抵扣%' " +
            "     OR content LIKE '%减免%' " +
            "     OR image_ocr_text LIKE '%权益%' " +
            "     OR image_ocr_text LIKE '%优惠%' " +
            "     OR image_ocr_text LIKE '%补贴%' " +
            "     OR image_ocr_text LIKE '%置换%' " +
            "     OR image_ocr_text LIKE '%0息%' " +
            "     OR image_ocr_text LIKE '%免息%' " +
            "     OR image_ocr_text LIKE '%低息%' " +
            "     OR image_ocr_text LIKE '%金融%' " +
            "     OR image_ocr_text LIKE '%保险%' " +
            "     OR image_ocr_text LIKE '%首付%' " +
            "     OR image_ocr_text LIKE '%订金%' " +
            "     OR image_ocr_text LIKE '%定金%' " +
            "     OR image_ocr_text LIKE '%抵扣%' " +
            "     OR image_ocr_text LIKE '%减免%' ) " +
            "ORDER BY CASE " +
            "    WHEN source_tool = 'weibo_official' THEN 0 " +
            "    WHEN source_tool LIKE '%\\_official' ESCAPE '\\\\' THEN 1 " +
            "    ELSE 2 END, " +
            "  event_importance DESC, add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findFinanceCandidates(
            @Param("sinceMs") long sinceMs,
            @Param("limit") int limit);

    /**
     * v8+: 按对标车型查最新金融政策帖（不限时间窗）。
     * 规则：
     *  - event_type IN ('price_finance', 'launch') — 上市帖里也常含定价
     *  - source_tool 必须官号（杜绝二手）
     *  - content / model_mentioned / event_summary 任一模糊命中车型名
     *  - 排序：优先含「图含：」标记的（OCR 已识到政策），其次 add_ts DESC
     */
    @Select("SELECT * FROM web_search_news " +
            "WHERE event_type IN ('price_finance','launch') " +
            "  AND source_tool LIKE '%\\_official' ESCAPE '\\\\' " +
            "  AND ( content LIKE CONCAT('%', #{modelKey}, '%') " +
            "     OR model_mentioned LIKE CONCAT('%', #{modelKey}, '%') " +
            "     OR event_summary LIKE CONCAT('%', #{modelKey}, '%') " +
            "     OR image_ocr_text LIKE CONCAT('%', #{modelKey}, '%') ) " +
            "ORDER BY CASE WHEN event_summary LIKE '%图含：%' THEN 0 ELSE 1 END, " +
            "         add_ts DESC LIMIT #{limit}")
    List<WebSearchNews> findLatestFinanceByModel(
            @Param("modelKey") String modelKey,
            @Param("limit") int limit);
}
