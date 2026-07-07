package com.example.belong.controller;

import com.example.belong.dto.BelongResponse;
import com.example.belong.dto.OrganizeRequest;
import com.example.belong.service.BelongAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrganizeController {

    private final BelongAiService belongAiService;

    @PostMapping("/organize")
    public BelongResponse organize(@RequestBody OrganizeRequest request) {
        return belongAiService.processOrganize(
                request.getContent(),
                request.getContentType(),
                request.getUserRequest(),
                request.getProfileContext()
        );
    }
}
