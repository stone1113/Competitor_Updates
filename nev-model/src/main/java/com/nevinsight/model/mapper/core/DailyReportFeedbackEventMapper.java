package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DailyReportFeedbackEventMapper extends BaseMapper<DailyReportFeedbackEvent> {

    @Select("SELECT * FROM daily_report_feedback_event WHERE event_id = #{eventId} LIMIT 1")
    DailyReportFeedbackEvent findByEventId(@Param("eventId") String eventId);
}
