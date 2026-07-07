package com.example.belong.service;

import com.example.belong.entity.ChatMessage;
import com.example.belong.mapper.ChatMessageMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageServiceTest {

    private final FakeChatMessageMapper chatMessageMapper = new FakeChatMessageMapper();
    private final ChatMessageService chatMessageService = new ChatMessageService(chatMessageMapper);

    @Test
    void buildRecentContextFormatsLatestMessagesInConversationOrder() {
        ChatMessage user = message(1L, "user", "今晚怎么安排？");
        ChatMessage assistant = message(2L, "assistant", "先做一个 30 分钟小任务。");
        chatMessageMapper.latest = List.of(assistant, user);

        String context = chatMessageService.buildRecentContext("demo-user", 20);

        assertThat(context).isEqualTo("""
                用户：今晚怎么安排？
                Belong：先做一个 30 分钟小任务。""");
    }

    @Test
    void normalizeLimitKeepsRequestsBetweenOneAndTwenty() {
        assertThat(chatMessageService.normalizeLimit(null)).isEqualTo(20);
        assertThat(chatMessageService.normalizeLimit(0)).isEqualTo(1);
        assertThat(chatMessageService.normalizeLimit(50)).isEqualTo(20);
        assertThat(chatMessageService.normalizeLimit(8)).isEqualTo(8);
    }

    @Test
    void getLatestMessagesReturnsConversationOrderAfterFetchingLatestRows() {
        ChatMessage first = message(1L, "user", "第一条");
        ChatMessage second = message(2L, "assistant", "第二条");
        chatMessageMapper.latest = List.of(second, first);

        List<ChatMessage> messages = chatMessageService.getLatestMessages("demo-user", 20);

        assertThat(messages).extracting(ChatMessage::getContent).containsExactly("第一条", "第二条");
    }

    private ChatMessage message(Long id, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setUserId("demo-user");
        message.setConversationId("conv-1");
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private static class FakeChatMessageMapper implements ChatMessageMapper {
        private List<ChatMessage> latest = new ArrayList<>();

        @Override
        public List<ChatMessage> findLatestByUserId(String userId, int limit) {
            return latest.stream().limit(limit).toList();
        }

        @Override
        public int insert(ChatMessage chatMessage) {
            latest.add(0, chatMessage);
            return 1;
        }
    }
}
