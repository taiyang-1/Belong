package com.example.belong.service;

import com.example.belong.entity.Memory;
import com.example.belong.mapper.MemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryMapper memoryMapper;

    public List<Memory> getActiveMemories(String userId) {
        return memoryMapper.findActiveByUserId(userId);
    }

    public List<Memory> getLatestActiveMemories(String userId, int limit) {
        return memoryMapper.findLatestActiveByUserId(userId, limit);
    }

    /**
     * Build memory context text for injecting into Dify requests.
     * V1 strategy: fetch latest 20 active memories, sorted by created_at DESC, concatenate.
     */
    public String buildMemoryContext(String userId) {
        List<Memory> memories = getLatestActiveMemories(userId, 20);
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Memory m : memories) {
            sb.append("用户记忆：").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    public void archiveMemory(Long id) {
        memoryMapper.archiveById(id);
    }

    /**
     * Save memories with dedup check.
     * V1 dedup: exact match on content + userId.
     */
    public int saveMemories(List<Memory> memories) {
        int saved = 0;
        for (Memory m : memories) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            int exists = memoryMapper.countByUserIdAndContent(m.getUserId(), m.getContent());
            if (exists > 0) {
                continue;
            }
            memoryMapper.insert(m);
            saved++;
        }
        return saved;
    }
}
