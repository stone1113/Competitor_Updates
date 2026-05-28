package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("web_search_news")
public class WebSearchNews extends BaseEntity {

    @TableField("url_hash")
    private String urlHash;

    @TableField("source_tool")
    private String sourceTool;

    @TableField("search_query")
    private String searchQuery;

    @TableField("brand_name")
    private String brandName;

    /** brand | industry */
    @TableField("category")
    private String category;

    @TableField("title")
    private String title;

    @TableField("url")
    private String url;

    @TableField("content")
    private String content;

    /** v8: 图片 URL 逗号分隔（仅官号源传递） */
    @TableField("image_urls")
    private String imageUrls;

    /** v8: qwen-vl 提取的图片文字 + 政策摘要 */
    @TableField("image_ocr_text")
    private String imageOcrText;

    /** v8: OCR 完成时间（NULL = 未跑） */
    @TableField("ocr_ts")
    private Long ocrTs;

    @TableField("published_date")
    private String publishedDate;

    @TableField("relevance_score")
    private Double relevanceScore;

    @TableField("crawl_date")
    private LocalDate crawlDate;

    /** launch | price_finance | campaign | sales_milestone | other | spam | NULL(未分类) */
    @TableField("event_type")
    private String eventType;

    /** 0-10 LLM 评估的事件重要度 */
    @TableField("event_importance")
    private Integer eventImportance;

    /** LLM 提取的一句话事件摘要 */
    @TableField("event_summary")
    private String eventSummary;

    /** LLM 提取的具体车型（逗号分隔） */
    @TableField("model_mentioned")
    private String modelMentioned;

    /** 事件分类完成时间（NULL 表示未分类） */
    @TableField("extract_ts")
    private Long extractTs;
}
