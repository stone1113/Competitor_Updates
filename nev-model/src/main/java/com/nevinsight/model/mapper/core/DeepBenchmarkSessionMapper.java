package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.DeepBenchmarkSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeepBenchmarkSessionMapper extends BaseMapper<DeepBenchmarkSession> {

    @Select("SELECT * FROM deep_benchmark_session " +
            "WHERE user_token = #{userToken} " +
            "ORDER BY last_active_ts DESC LIMIT #{limit}")
    List<DeepBenchmarkSession> findByUserToken(@Param("userToken") String userToken,
                                                 @Param("limit") int limit);
}
