package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.ChatRequest;
import com.example.belong.service.BelongAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final BelongAiService belongAiService;

    @PostMapping("/chat")
    public BelongResponse chat(@RequestBody ChatRequest request) {
        return belongAiService.processChat(
                request.getMessage(),
                request.getProfileContext(),
                request.getRecentContext()
        );
    }
}
