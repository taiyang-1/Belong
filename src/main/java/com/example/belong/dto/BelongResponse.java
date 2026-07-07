package com.example.belong.dto;

import lombok.Data;
import java.util.List;

@Data
public class BelongResponse {
    private Object result;
    private List<Object> savedMemories;
}
