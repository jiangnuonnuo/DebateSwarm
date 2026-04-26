package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishKnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishKnowledgeDocDao {
    AiXhsPublishKnowledgeDoc queryByKnowledgeId(@Param("knowledgeId") String knowledgeId);

    List<AiXhsPublishKnowledgeDoc> page(@Param("userId") Long userId,
                    @Param("knowledgeScope") String knowledgeScope,
                    @Param("offset") Integer offset,
                    @Param("size") Integer size);

    Integer count(@Param("userId") Long userId,
                  @Param("knowledgeScope") String knowledgeScope);

    void insert(AiXhsPublishKnowledgeDoc po);

    void update(AiXhsPublishKnowledgeDoc po);
}
