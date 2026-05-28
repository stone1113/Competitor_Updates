package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.TalkingPointVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TalkingPointVersionMapper extends BaseMapper<TalkingPointVersion> {

    @Select("SELECT * FROM talking_point_version WHERE talking_point_id = #{tpId} " +
            "ORDER BY version DESC")
    List<TalkingPointVersion> findAllVersions(@Param("tpId") Long talkingPointId);

    @Select("SELECT * FROM talking_point_version WHERE talking_point_id = #{tpId} AND is_current = 1")
    TalkingPointVersion findCurrentVersion(@Param("tpId") Long talkingPointId);
}
