package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.InvitationRequest;
import com.languageapp.backend.dto.response.InvitationPreview;
import com.languageapp.backend.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/invitations") @RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitations;
    private final com.languageapp.backend.service.InvitationRequestLimiter limiter;
    // Preview uses a body so the reusable identifier is absent from query/access logs.
    @PostMapping("/preview")
    public ResponseEntity<InvitationPreview> preview(@Valid @RequestBody InvitationRequest request, Authentication auth) {
        limiter.check(auth.getName(), "preview");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.preview(auth.getName(), request.kind(), request.token()));
    }
    @PostMapping("/confirm")
    public ResponseEntity<InvitationPreview> confirm(@Valid @RequestBody InvitationRequest request, Authentication auth) {
        limiter.check(auth.getName(), "confirm");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.confirm(auth.getName(), request.kind(), request.token()));
    }
    @PostMapping("/friend") public ResponseEntity<InvitationService.ManagedLink> friend(Authentication auth) { return managed(auth,"friend",null,"current"); }
    @PutMapping("/friend") public ResponseEntity<InvitationService.ManagedLink> rotateFriend(Authentication auth) { return managed(auth,"friend",null,"rotate"); }
    @DeleteMapping("/friend") public ResponseEntity<InvitationService.ManagedLink> revokeFriend(Authentication auth) { return managed(auth,"friend",null,"revoke"); }
    @PostMapping("/classrooms/{id}") public ResponseEntity<InvitationService.ManagedLink> classroom(@PathVariable UUID id, Authentication auth) { return managed(auth,"classroom",id,"current"); }
    @PutMapping("/classrooms/{id}") public ResponseEntity<InvitationService.ManagedLink> rotateClassroom(@PathVariable UUID id, Authentication auth) { return managed(auth,"classroom",id,"rotate"); }
    @DeleteMapping("/classrooms/{id}") public ResponseEntity<InvitationService.ManagedLink> revokeClassroom(@PathVariable UUID id, Authentication auth) { return managed(auth,"classroom",id,"revoke"); }
    private ResponseEntity<InvitationService.ManagedLink> managed(Authentication auth,String kind,UUID id,String operation) {
        limiter.check(auth.getName(), "manage");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.manage(auth.getName(),kind,id,operation));
    }

}
