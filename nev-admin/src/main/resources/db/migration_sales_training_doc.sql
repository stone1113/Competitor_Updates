-- v9.7 销售培训文档承载体（替代飞书文档）
-- RAGFlow Agent + MCP 流程产出的「猛士XXX 销售培训文档」存在这里
-- 业务团队从前端 /sales-training 页查看

CREATE TABLE IF NOT EXISTS sales_training_doc (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    title           VARCHAR(200) NOT NULL                  COMMENT '文档标题，如「猛士917 vs 仰望U8/坦克700 销售培训」',
    target_model    VARCHAR(64)  NOT NULL                  COMMENT '主推车型（本品），如「猛士917」',
    competitor_models VARCHAR(500)                          COMMENT '对标竞品逗号分隔，如「仰望U8 2024款,坦克700新能源 2026款」',
    content_md      MEDIUMTEXT   NOT NULL                  COMMENT '完整 Markdown 文档（含 9 个章节）',
    summary         VARCHAR(500)                            COMMENT '一句话摘要（可空）',
    sources_json    TEXT                                    COMMENT 'JSON 数组：[{type:"web",title:"...",url:"..."},...]',
    generator       VARCHAR(64)  DEFAULT 'ragflow-agent'   COMMENT '生成器：ragflow-agent / manual / claude-code',
    status          TINYINT      DEFAULT 1                  COMMENT '1=已发布 0=草稿',
    view_count      INT          DEFAULT 0                  COMMENT '浏览次数',
    add_ts          BIGINT                                  COMMENT '创建毫秒',
    last_modify_ts  BIGINT                                  COMMENT '最后修改毫秒',
    KEY idx_target_model (target_model),
    KEY idx_add_ts (add_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售培训文档';
