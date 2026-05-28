package com.nevinsight.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "add_ts", fill = FieldFill.INSERT)
    private Long addTs;

    @TableField(value = "last_modify_ts", fill = FieldFill.INSERT_UPDATE)
    private Long lastModifyTs;
}
