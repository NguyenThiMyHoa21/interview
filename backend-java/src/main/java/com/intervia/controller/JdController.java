package com.intervia.controller;

import com.intervia.dto.CvDto.AnalysisProgress;
import com.intervia.dto.CvDto.RenameBody;
import com.intervia.dto.CvDto.VersionInfo;
import com.intervia.dto.JdDto.*;
import com.intervia.dto.SessionDto.SessionSummary;
import com.intervia.service.AuthService;
import com.intervia.service.JdService;
import com.intervia.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JdController {

    private final JdService jdService;
    private final AuthService authService;
    private final SessionService sessionService;

    @GetMapping("/jds")
    public List<JdSummary> listJds(HttpServletRequest request) {
        return jdService.listJds(authService.resolveCurrentUserId(request));
    }

    @PostMapping(value = "/jds", consumes = {"multipart/form-data"})
    @ResponseStatus(HttpStatus.CREATED)
    public JdSummary createJdMultipart(
            HttpServletRequest request,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        return jdService.createJd(authService.resolveCurrentUserId(request), name, text, mode, file);
    }

    @PostMapping(value = "/jds", consumes = {"application/json"})
    @ResponseStatus(HttpStatus.CREATED)
    public JdSummary createJdJson(HttpServletRequest request, @RequestBody PasteJdBody body) {
        return jdService.createJd(authService.resolveCurrentUserId(request), body.getName(), body.getText(), body.getMode(), null);
    }

    @GetMapping("/jds/{jdId}")
    public JdSummary getJd(HttpServletRequest request, @PathVariable String jdId) {
        return jdService.getJd(authService.resolveCurrentUserId(request), jdId);
    }

    @GetMapping("/jds/{jdId}/sessions")
    public List<SessionSummary> listJdSessions(HttpServletRequest request, @PathVariable String jdId) {
        return sessionService.listSessionsByJdId(authService.resolveCurrentUserId(request), jdId);
    }

    @GetMapping("/jds/{jdId}/analysis")
    public AnalysisProgress getJdAnalysis(HttpServletRequest request, @PathVariable String jdId) {
        return jdService.getJdAnalysis(authService.resolveCurrentUserId(request), jdId);
    }

    @PostMapping("/jds/{jdId}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisProgress reanalyzeJd(
            HttpServletRequest request,
            @PathVariable String jdId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String mode = body != null ? body.get("mode") : null;
        return jdService.reanalyzeJd(authService.resolveCurrentUserId(request), jdId, mode);
    }

    @PatchMapping("/jds/{jdId}")
    public JdSummary renameJd(HttpServletRequest request, @PathVariable String jdId, @RequestBody RenameBody body) {
        return jdService.renameJd(authService.resolveCurrentUserId(request), jdId, body.getName());
    }

    @GetMapping("/jds/{jdId}/versions")
    public List<VersionInfo> listJdVersions(HttpServletRequest request, @PathVariable String jdId) {
        return jdService.listJdVersions(authService.resolveCurrentUserId(request), jdId);
    }

    @GetMapping("/jds/{jdId}/versions/{v}")
    public JdVersionView getJdVersion(HttpServletRequest request, @PathVariable String jdId, @PathVariable String v) {
        return jdService.getJdVersion(authService.resolveCurrentUserId(request), jdId, v);
    }

    @PostMapping("/jds/{jdId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public JdVersionView saveJdVersion(
            HttpServletRequest request,
            @PathVariable String jdId,
            @RequestBody NewJdVersionBody body
    ) {
        return jdService.saveJdVersion(authService.resolveCurrentUserId(request), jdId, body.getBaseVersion(), body.getRequirements());
    }

    @DeleteMapping("/jds/{jdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJd(HttpServletRequest request, @PathVariable String jdId) {
        jdService.deleteJd(authService.resolveCurrentUserId(request), jdId);
    }
}
