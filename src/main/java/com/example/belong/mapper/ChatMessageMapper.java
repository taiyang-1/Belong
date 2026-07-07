package com.example.belong.mapper;

import com.example.belong.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Select("SELECT * FROM chat_messages WHERE user_id = #{userId} ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<ChatMessage> findLatestByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Insert("INSERT INTO chat_messages(user_id, conversation_id, role, content, suggested_actions, created_at) " +
            "VALUES(#{userId}, #{conversationId}, #{role}, #{content}, #{suggestedActions}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage chatMessage);
}
