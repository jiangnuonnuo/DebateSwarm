package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiPrompt;
import jakarta.validation.constraints.NotBlank;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IAiPromptDao {

    AiPrompt queryByPromptId(@Param("promptId") String promptId);

    void loadSystemPrompt(@Param("promptId") String promptId, @Param("systemPrompt") String systemPrompt);

    List<AiPrompt> page(@Param("keyword") String keyword,
                        @Param("offset") Integer offset,
                        @Param("size") Integer size);

    Integer count(@Param("keyword") String keyword);

    Integer countAll();

    void insert(AiPrompt aiPrompt);

    void update(AiPrompt aiPrompt);

    void delete(@Param("id") Long id);

    void updateSystenByPromptId(@Param("promptId") String promptId, @Param("systemPrompt") String systemPrompt);

    Integer deleteByPromptId(@Param("promptId") String promptId);
}
