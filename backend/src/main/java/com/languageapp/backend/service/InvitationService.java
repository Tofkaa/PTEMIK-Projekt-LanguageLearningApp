package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.InvitationPreview;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor
public class InvitationService {
    private final InvitationLinkRepository invitations;
    private final UserRepository users;
    private final ClassroomRepository classrooms;
    private final ClassroomMemberRepository members;
    private final FriendshipRepository friendships;
    private final ClassroomService classroomService;
    private final FriendshipService friendshipService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public record ManagedLink(String kind, String token) { }

    @Transactional
    public ManagedLink manage(String email, String kind, UUID classroomId, String operation) {
        User actor = users.findByEmailForInvitationUpdate(email).orElseThrow(() -> new ResourceNotFoundException("A fiók nem található."));
        ConnectionChecks.requireVerified(actor);
        Classroom room = null;
        if ("classroom".equals(kind)) {
            room = classrooms.findForInvitationUpdate(classroomId).orElseThrow(this::unavailable);
            if (actor.getRole() != Role.TEACHER || !room.getTeacher().getUserId().equals(actor.getUserId()))
                throw new ForbiddenException("Csak a saját osztálytermed meghívólinkjét kezelheted.");
        } else if (!"friend".equals(kind)) throw unavailable();
        String scope = kind+":"+(room == null ? actor.getUserId() : room.getClassroomId());
        InvitationLink link = invitations.findScopeForUpdate(scope).orElse(null);
        if ("revoke".equals(operation)) {
            if (link != null) { link.setToken(null); link.setRevokedAt(clock.instant()); link.setUpdatedAt(clock.instant()); }
            return new ManagedLink(kind, null);
        }
        if (link == null) {
            link = new InvitationLink(); link.setScopeKey(scope); link.setKind(kind);
            link.setOwner(actor); link.setClassroom(room); link.setCreatedAt(clock.instant());
        }
        if ("rotate".equals(operation) || link.getToken() == null || link.getRevokedAt() != null
                || (link.getExpiresAt() != null && !clock.instant().isBefore(link.getExpiresAt()))) {
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            link.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            link.setRevokedAt(null); link.setExpiresAt(null); link.setUpdatedAt(clock.instant());
        }
        // New records also need a token; no time limit is imposed on these reusable links.
        if (link.getToken() == null) throw new IllegalStateException("Missing invitation identifier");
        invitations.save(link);
        return new ManagedLink(kind, link.getToken());
    }

    @Transactional(readOnly=true)
    public InvitationPreview preview(String email, String kind, String token) {
        User actor = actor(email);
        InvitationLink link = valid(invitations.findByToken(token).orElseThrow(this::unavailable), kind);
        return describe(link, actor);
    }

    @Transactional
    public InvitationPreview confirm(String email, String kind, String token) {
        User actor = actor(email);
        InvitationLink snapshot = valid(invitations.findByToken(token).orElseThrow(this::unavailable), kind);
        // All classroom-link writers lock the classroom before its invitation row.
        Classroom room = "classroom".equals(kind)
                ? classrooms.findForInvitationUpdate(snapshot.getClassroom().getClassroomId()).orElseThrow(this::unavailable) : null;
        InvitationLink link = valid(invitations.findByTokenForUpdate(token).orElseThrow(this::unavailable), kind);
        InvitationPreview before = describe(link, actor);
        if ("FORBIDDEN".equals(before.state())) throw new ForbiddenException("Osztályteremhez csak diák csatlakozhat.");
        if (!"READY".equals(before.state())) return before;
        if (room != null) classroomService.requestMembership(room, actor);
        else friendshipService.requestFromInvitation(actor, link.getOwner());
        return describe(link, actor);
    }

    private User actor(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("A fiók nem található."));
        ConnectionChecks.requireVerified(user); return user;
    }

    private InvitationLink valid(InvitationLink link, String kind) {
        if (!link.getKind().equals(kind) || link.getToken() == null || link.getRevokedAt() != null
                || (link.getExpiresAt() != null && !clock.instant().isBefore(link.getExpiresAt()))
                || !link.getOwner().isActive() || !link.getOwner().isVerified() || link.getOwner().getRole() == Role.DELETED)
            throw unavailable();
        if ("classroom".equals(kind)) {
            try {
                Classroom room = link.getClassroom();
                // A filtered lazy relation to an archived classroom must be a normal invalid link, not a 500.
                if (room == null || !classrooms.existsById(room.getClassroomId()) || !room.isActive()
                        || link.getOwner().getRole() != Role.TEACHER
                        || !room.getTeacher().getUserId().equals(link.getOwner().getUserId())) throw unavailable();
            } catch (jakarta.persistence.EntityNotFoundException unavailableTarget) { throw unavailable(); }
        }
        return link;
    }

    private InvitationPreview describe(InvitationLink link, User actor) {
        if ("classroom".equals(link.getKind())) {
            Classroom room = link.getClassroom();
            String state = link.getOwner().getUserId().equals(actor.getUserId()) ? "SELF"
                    : actor.getRole() != Role.STUDENT ? "FORBIDDEN"
                    : members.findByClassroom_ClassroomIdAndUser_UserId(room.getClassroomId(), actor.getUserId())
                        .map(m -> m.getStatus() == MembershipStatus.ACCEPTED ? "CONNECTED" : m.getStatus() == MembershipStatus.REJECTED ? "REJECTED" : "PENDING").orElse("READY");
            return new InvitationPreview(link.getKind(), room.getName(), "Tanár: "+link.getOwner().getName()+" · "+room.getCourse().getLanguageName(), state, room.getClassroomId());
        }
        String state = "READY";
        if (link.getOwner().getUserId().equals(actor.getUserId())) state = "SELF";
        else {
            var connections = friendships.findConnectionsBetween(actor.getUserId(), link.getOwner().getUserId());
            if (connections.stream().anyMatch(f -> f.getStatus() == FriendshipStatus.ACCEPTED)) state = "CONNECTED";
            else if (!connections.isEmpty()) state = connections.getFirst().getUser().getUserId().equals(actor.getUserId()) ? "PENDING" : "INCOMING";
        }
        return new InvitationPreview(link.getKind(), link.getOwner().getName()+"#"+link.getOwner().getUserTag(), "Barátkapcsolat meghívója", state, null);
    }

    private ResourceNotFoundException unavailable() { return new ResourceNotFoundException("Ez a meghívó nem érvényes vagy már nem használható. Kérj új linket a tulajdonosától!"); }
}
