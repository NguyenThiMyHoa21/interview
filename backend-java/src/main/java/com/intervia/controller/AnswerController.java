package com.intervia.controller;

import com.intervia.dto.SessionDto.EditAnswerRequest;
import com.intervia.service.AuthService;
import com.intervia.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnswerController {

    private final ReportService reportService;
    private final AuthService authService;

    @PatchMapping("/answers/{answerId}")
    public Map<String, Object> editAnswer(
            HttpServletRequest request,
            @PathVariable String answerId,
            @RequestBody EditAnswerRequest req
    ) {
        String userId = authService.resolveCurrentUserId(request);
        reportService.updateAnswer(userId, answerId, req.getTextEdited());
        return Map.of(
                "id", answerId,
                "text_edited", req.getTextEdited()
        );
    }
}
