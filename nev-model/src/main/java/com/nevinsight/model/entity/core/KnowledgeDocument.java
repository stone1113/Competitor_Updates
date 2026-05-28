package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_name")
    private String docName;

    @TableField("doc_type")
    private String docType;

    @TableField("file_path")
    private String filePath;

    @TableField("collection_name")
    private String collectionName;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
