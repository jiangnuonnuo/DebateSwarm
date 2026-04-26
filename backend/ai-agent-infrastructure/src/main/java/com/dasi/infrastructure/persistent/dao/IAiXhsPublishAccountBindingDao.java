package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishAccountBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishAccountBindingDao {
    AiXhsPublishAccountBinding queryByBindingId(@Param("bindingId") String bindingId);

    AiXhsPublishAccountBinding queryDefaultByUserId(@Param("userId") Long userId);

    void insert(AiXhsPublishAccountBinding po);

    void update(AiXhsPublishAccountBinding po);
}
