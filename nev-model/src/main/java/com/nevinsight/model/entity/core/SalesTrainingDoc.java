package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 销售培训文档（RAGFlow Agent + MCP 产出的对标销售话术）。
 * 替代飞书文档承载体，业务团队从 m-monitor 前端 /sales-training 查看。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_training_doc")
public class SalesTrainingDoc extends BaseEntity {

    @TableField("title")
    private String title;

    @TableField("target_model")
    private String targetModel;

    @TableField("competitor_models")
    private String competitorModels;

    @TableField("content_md")
    private String contentMd;

    @TableField("summary")
    private String summary;

    /** JSON 数组：[{type:"web",title:"...",url:"..."},...] */
    @TableField("sources_json")
    private String sourcesJson;

    /** 生成器：ragflow-agent / manual / claude-code */
    @TableField("generator")
    private String generator;

    /** 1=已发布 0=草稿 */
    @TableField("status")
    private Integer status;

    @TableField("view_count")
    private Integer viewCount;
}
