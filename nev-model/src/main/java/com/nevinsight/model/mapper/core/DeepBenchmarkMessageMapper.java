package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.DeepBenchmarkMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeepBenchmarkMessageMapper extends BaseMapper<DeepBenchmarkMessage> {

    @Select("SELECT * FROM deep_benchmark_message " +
            "WHERE session_id = #{sessionId} ORDER BY add_ts ASC, id ASC")
    List<DeepBenchmarkMessage> findBySession(@Param("sessionId") Long sessionId);
}
