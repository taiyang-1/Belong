package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.PlanRequest;
import com.example.belong.service.BelongAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlanController {

    private final BelongAiService belongAiService;

    @PostMapping("/plan")
    public BelongResponse plan(@RequestBody PlanRequest request) {
        return belongAiService.processPlan(
                request.getPlanAction(),
                request.getUserGoal(),
                request.getCurrentPlan(),
                request.getProgressContext(),
                request.getProfileContext()
        );
    }
}
