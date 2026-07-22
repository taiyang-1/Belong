package com.example.belong.service;

import com.example.belong.entity.ChatMessage;
import com.example.belong.mapper.ChatMessageMapper;
import com.example.belong.dto.ChatConversationSummary;
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

    @Test
    void getConversationSummariesReturnsRecentConversations() {
        ChatConversationSummary recent = conversation("conv-2", "今天中午吃什么", "可以吃清淡一点。", 2);
        ChatConversationSummary older = conversation("conv-1", "昨天聊学习", "先做 30 分钟。", 4);
        chatMessageMapper.conversations = List.of(recent, older);

        List<ChatConversationSummary> conversations = chatMessageService.getConversationSummaries("demo-user", 20);

        assertThat(conversations).extracting(ChatConversationSummary::getConversationId)
                .containsExactly("conv-2", "conv-1");
    }

    @Test
    void getConversationMessagesReturnsOnlySelectedConversationInConversationOrder() {
        ChatMessage first = message(1L, "user", "今天中午吃什么");
        first.setConversationId("conv-2");
        ChatMessage second = message(2L, "assistant", "可以吃清淡一点。");
        second.setConversationId("conv-2");
        chatMessageMapper.conversationMessages = List.of(first, second);

        List<ChatMessage> messages = chatMessageService.getConversationMessages("demo-user", "conv-2");

        assertThat(messages).extracting(ChatMessage::getContent)
                .containsExactly("今天中午吃什么", "可以吃清淡一点。");
    }

    @Test
    void deleteConversationRemovesOnlySelectedConversationForUser() {
        int deleted = chatMessageService.deleteConversation("demo-user", "conv-2");

        assertThat(deleted).isEqualTo(3);
        assertThat(chatMessageMapper.deletedUserId).isEqualTo("demo-user");
        assertThat(chatMessageMapper.deletedConversationId).isEqualTo("conv-2");
    }

    @Test
    void deleteConversationIgnoresBlankConversationId() {
        int deleted = chatMessageService.deleteConversation("demo-user", " ");

        assertThat(deleted).isZero();
        assertThat(chatMessageMapper.deletedConversationId).isNull();
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

    private ChatConversationSummary conversation(String conversationId, String title, String lastMessage, int messageCount) {
        ChatConversationSummary conversation = new ChatConversationSummary();
        conversation.setConversationId(conversationId);
        conversation.setTitle(title);
        conversation.setLastMessage(lastMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setMessageCount(messageCount);
        return conversation;
    }

    private static class FakeChatMessageMapper implements ChatMessageMapper {
        private List<ChatMessage> latest = new ArrayList<>();
        private List<ChatConversationSummary> conversations = new ArrayList<>();
        private List<ChatMessage> conversationMessages = new ArrayList<>();
        private String deletedUserId;
        private String deletedConversationId;

        @Override
        public List<ChatMessage> findLatestByUserId(String userId, int limit) {
            return latest.stream().limit(limit).toList();
        }

        @Override
        public int insert(ChatMessage chatMessage) {
            latest.add(0, chatMessage);
            return 1;
        }

        @Override
        public List<ChatConversationSummary> findRecentConversationsByUserId(String userId, int limit) {
            return conversations.stream().limit(limit).toList();
        }

        @Override
        public List<ChatMessage> findByUserIdAndConversationId(String userId, String conversationId) {
            return conversationMessages.stream()
                    .filter(message -> conversationId.equals(message.getConversationId()))
                    .toList();
        }

        @Override
        public int deleteByUserIdAndConversationId(String userId, String conversationId) {
            deletedUserId = userId;
            deletedConversationId = conversationId;
            return 3;
        }
    }
}
