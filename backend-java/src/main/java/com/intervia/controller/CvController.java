package com.intervia.controller;

import com.intervia.dto.CvDto.*;
import com.intervia.dto.SessionDto.SessionSummary;
import com.intervia.service.AuthService;
import com.intervia.service.CvService;
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
public class CvController {

    private final CvService cvService;
    private final AuthService authService;
    private final SessionService sessionService;

    @GetMapping("/cvs")
    public List<CvSummary> listCvs(HttpServletRequest request) {
        return cvService.listCvs(authService.resolveCurrentUserId(request));
    }

    @PostMapping("/cvs")
    @ResponseStatus(HttpStatus.CREATED)
    public CvSummary uploadCv(
            HttpServletRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "mode", required = false) String mode
    ) {
        return cvService.uploadCv(authService.resolveCurrentUserId(request), name, mode, file);
    }

    @GetMapping("/cvs/{cvId}")
    public CvSummary getCv(HttpServletRequest request, @PathVariable String cvId) {
        return cvService.getCv(authService.resolveCurrentUserId(request), cvId);
    }

    @GetMapping("/cvs/{cvId}/sessions")
    public List<SessionSummary> listCvSessions(HttpServletRequest request, @PathVariable String cvId) {
        return sessionService.listSessionsByCvId(authService.resolveCurrentUserId(request), cvId);
    }

    @GetMapping("/cvs/{cvId}/analysis")
    public AnalysisProgress getCvAnalysis(HttpServletRequest request, @PathVariable String cvId) {
        return cvService.getCvAnalysis(authService.resolveCurrentUserId(request), cvId);
    }

    @PostMapping("/cvs/{cvId}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisProgress reanalyzeCv(
            HttpServletRequest request,
            @PathVariable String cvId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String mode = body != null ? body.get("mode") : null;
        return cvService.reanalyzeCv(authService.resolveCurrentUserId(request), cvId, mode);
    }

    @PatchMapping("/cvs/{cvId}")
    public CvSummary renameCv(HttpServletRequest request, @PathVariable String cvId, @RequestBody RenameBody body) {
        return cvService.renameCv(authService.resolveCurrentUserId(request), cvId, body.getName());
    }

    @GetMapping("/cvs/{cvId}/versions")
    public List<VersionInfo> listCvVersions(HttpServletRequest request, @PathVariable String cvId) {
        return cvService.listCvVersions(authService.resolveCurrentUserId(request), cvId);
    }

    @GetMapping("/cvs/{cvId}/versions/{v}")
    public CvVersionView getCvVersion(HttpServletRequest request, @PathVariable String cvId, @PathVariable String v) {
        return cvService.getCvVersion(authService.resolveCurrentUserId(request), cvId, v);
    }

    @PostMapping("/cvs/{cvId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public CvVersionView saveCvVersion(
            HttpServletRequest request,
            @PathVariable String cvId,
            @RequestBody NewCvVersionBody body
    ) {
        return cvService.saveCvVersion(authService.resolveCurrentUserId(request), cvId, body.getBaseVersion(), body.getProfile());
    }

    @DeleteMapping("/cvs/{cvId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCv(HttpServletRequest request, @PathVariable String cvId) {
        cvService.deleteCv(authService.resolveCurrentUserId(request), cvId);
    }
}
