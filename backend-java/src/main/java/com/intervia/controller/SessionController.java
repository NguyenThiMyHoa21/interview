package com.intervia.controller;

import com.intervia.dto.ReportDto.SessionReportView;
import com.intervia.dto.ReportDto.SessionStats;
import com.intervia.dto.SessionDto.*;
import com.intervia.service.AuthService;
import com.intervia.service.ReportService;
import com.intervia.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final ReportService reportService;
    private final AuthService authService;

    @GetMapping("/sessions")
    public List<SessionSummary> listSessions(HttpServletRequest request) {
        return sessionService.listSessions(authService.resolveCurrentUserId(request));
    }

    @GetMapping("/sessions/stats")
    public SessionStats getStats(HttpServletRequest request) {
        return reportService.computeUserStats(authService.resolveCurrentUserId(request));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionSummary createSession(HttpServletRequest request, @RequestBody CreateSessionRequest req) {
        return sessionService.createSession(authService.resolveCurrentUserId(request), req);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionSummary getSession(HttpServletRequest request, @PathVariable String sessionId) {
        return sessionService.getSession(authService.resolveCurrentUserId(request), sessionId);
    }

    @PatchMapping("/sessions/{sessionId}")
    public SessionSummary updateSession(HttpServletRequest request, @PathVariable String sessionId, @RequestBody UpdateSessionRequest req) {
        return sessionService.updateDraftInputs(authService.resolveCurrentUserId(request), sessionId, req.getCvVersion(), req.getJdVersion());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(HttpServletRequest request, @PathVariable String sessionId) {
        sessionService.deleteSession(authService.resolveCurrentUserId(request), sessionId);
    }

    @PostMapping("/sessions/{sessionId}/blueprint")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BlueprintView createBlueprint(HttpServletRequest request, @PathVariable String sessionId) {
        return sessionService.triggerBlueprint(authService.resolveCurrentUserId(request), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/blueprint")
    public BlueprintView getBlueprint(HttpServletRequest request, @PathVariable String sessionId) {
        return sessionService.getBlueprint(authService.resolveCurrentUserId(request), sessionId);
    }

    @PatchMapping("/sessions/{sessionId}/blueprint")
    public BlueprintView patchBlueprint(HttpServletRequest request, @PathVariable String sessionId, @RequestBody UpdateBlueprintRequest req) {
        return sessionService.patchBlueprint(authService.resolveCurrentUserId(request), sessionId, req.getCompetencies());
    }

    @PostMapping(value = "/sessions/{sessionId}/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startSession(HttpServletRequest request, @PathVariable String sessionId, @RequestBody(required = false) StartSessionRequest req) {
        String mode = req != null ? req.getMode() : "text";
        return sessionService.startInterview(authService.resolveCurrentUserId(request), sessionId, mode);
    }

    @GetMapping("/sessions/{sessionId}/transcript")
    public TranscriptResponse getTranscript(HttpServletRequest request, @PathVariable String sessionId) {
        return sessionService.getTranscript(authService.resolveCurrentUserId(request), sessionId);
    }

    @PostMapping(value = "/sessions/{sessionId}/turns", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submitTurn(HttpServletRequest request, @PathVariable String sessionId, @RequestBody SubmitTurnRequest req) {
        return sessionService.submitTurn(authService.resolveCurrentUserId(request), sessionId, req.getText());
    }

    @PostMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submitEvent(HttpServletRequest request, @PathVariable String sessionId, @RequestBody SubmitEventRequest req) {
        return sessionService.submitEvent(authService.resolveCurrentUserId(request), sessionId, req.getType());
    }

    @PostMapping("/sessions/{sessionId}/stop")
    public SessionEndSummary stopSession(HttpServletRequest request, @PathVariable String sessionId) {
        return sessionService.stopSession(authService.resolveCurrentUserId(request), sessionId);
    }

    @PatchMapping("/sessions/{sessionId}/mode")
    public SessionSummary changeMode(HttpServletRequest request, @PathVariable String sessionId, @RequestBody StartSessionRequest req) {
        return sessionService.changeMode(authService.resolveCurrentUserId(request), sessionId, req.getMode());
    }

    @GetMapping("/sessions/{sessionId}/coverage")
    public List<Coverage> getCoverage(HttpServletRequest request, @PathVariable String sessionId) {
        return sessionService.computeCoverage(authService.resolveCurrentUserId(request), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/report")
    public SessionReportView getReport(HttpServletRequest request, @PathVariable String sessionId) {
        return reportService.getReport(authService.resolveCurrentUserId(request), sessionId);
    }

    @PostMapping("/sessions/{sessionId}/report")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SessionReportView createReport(HttpServletRequest request, @PathVariable String sessionId) {
        return reportService.triggerReport(authService.resolveCurrentUserId(request), sessionId);
    }
}
