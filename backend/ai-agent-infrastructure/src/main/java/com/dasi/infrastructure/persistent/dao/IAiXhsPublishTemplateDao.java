package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishTemplateDao {
    AiXhsPublishTemplate queryByTemplateId(@Param("templateId") String templateId);

    List<AiXhsPublishTemplate> page(@Param("userId") Long userId,
                    @Param("keyword") String keyword,
                    @Param("offset") Integer offset,
                    @Param("size") Integer size);

    Integer count(@Param("userId") Long userId,
                  @Param("keyword") String keyword);

    void insert(AiXhsPublishTemplate po);

    void update(AiXhsPublishTemplate po);
}
