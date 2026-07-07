package com.example.belong.dto;

import lombok.Data;

@Data
public class PlanRequest {
    private String planAction;
    private String userGoal;
    private String currentPlan;
    private String progressContext;
    private String profileContext;
}
