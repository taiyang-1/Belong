package com.example.belong.controller;

import com.example.belong.config.BelongProperties;
import com.example.belong.entity.Memory;
import com.example.belong.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;
    private final BelongProperties belongProperties;

    @GetMapping("/memories")
    public Map<String, Object> listMemories() {
        List<Memory> memories = memoryService.getActiveMemories(belongProperties.getDemoUserId());
        return Map.of("memories", memories);
    }

    @DeleteMapping("/memories/{id}")
    public Map<String, Object> archiveMemory(@PathVariable Long id) {
        memoryService.archiveMemory(id);
        return Map.of("success", true);
    }
}
