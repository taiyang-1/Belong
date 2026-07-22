package com.example.belong.mapper;

import com.example.belong.dto.ChatConversationSummary;
import com.example.belong.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Select("SELECT * FROM chat_messages WHERE user_id = #{userId} ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<ChatMessage> findLatestByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Select("""
            SELECT
                c.conversation_id,
                COALESCE(first_user.content, last_message.content) AS title,
                last_message.content AS last_message,
                c.updated_at,
                c.message_count
            FROM (
                SELECT
                    conversation_id,
                    MAX(id) AS last_id,
                    MAX(created_at) AS updated_at,
                    COUNT(*) AS message_count
                FROM chat_messages
                WHERE user_id = #{userId}
                GROUP BY conversation_id
                ORDER BY updated_at DESC, last_id DESC
                LIMIT #{limit}
            ) c
            JOIN chat_messages last_message
                ON last_message.id = c.last_id
            LEFT JOIN (
                SELECT cm.conversation_id, cm.content
                FROM chat_messages cm
                JOIN (
                    SELECT conversation_id, MIN(id) AS first_user_id
                    FROM chat_messages
                    WHERE user_id = #{userId} AND role = 'user'
                    GROUP BY conversation_id
                ) first_ids ON first_ids.first_user_id = cm.id
            ) first_user ON first_user.conversation_id = c.conversation_id
            ORDER BY c.updated_at DESC, c.last_id DESC
            """)
    List<ChatConversationSummary> findRecentConversationsByUserId(
            @Param("userId") String userId,
            @Param("limit") int limit
    );

    @Select("""
            SELECT *
            FROM chat_messages
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatMessage> findByUserIdAndConversationId(
            @Param("userId") String userId,
            @Param("conversationId") String conversationId
    );

    @Insert("INSERT INTO chat_messages(user_id, conversation_id, role, content, suggested_actions, created_at) " +
            "VALUES(#{userId}, #{conversationId}, #{role}, #{content}, #{suggestedActions}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage chatMessage);

    @Delete("""
            DELETE FROM chat_messages
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            """)
    int deleteByUserIdAndConversationId(
            @Param("userId") String userId,
            @Param("conversationId") String conversationId
    );
}
