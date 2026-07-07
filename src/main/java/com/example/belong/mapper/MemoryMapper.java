package com.example.belong.mapper;

import com.example.belong.entity.Memory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MemoryMapper {

    @Select("SELECT * FROM memories WHERE user_id = #{userId} AND is_archived = FALSE ORDER BY created_at DESC")
    List<Memory> findActiveByUserId(String userId);

    @Select("SELECT * FROM memories WHERE user_id = #{userId} AND is_archived = FALSE ORDER BY created_at DESC LIMIT #{limit}")
    List<Memory> findLatestActiveByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Select("SELECT * FROM memories WHERE id = #{id}")
    Memory findById(Long id);

    @Insert("INSERT INTO memories(user_id, type, content, confidence, sensitivity, expires_at, source, reason, is_archived, created_at, updated_at) " +
            "VALUES(#{userId}, #{type}, #{content}, #{confidence}, #{sensitivity}, #{expiresAt}, #{source}, #{reason}, #{isArchived}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Memory memory);

    @Update("UPDATE memories SET is_archived = TRUE, updated_at = NOW() WHERE id = #{id}")
    int archiveById(Long id);

    @Select("SELECT COUNT(*) FROM memories WHERE user_id = #{userId} AND content = #{content} AND is_archived = FALSE")
    int countByUserIdAndContent(@Param("userId") String userId, @Param("content") String content);
}
