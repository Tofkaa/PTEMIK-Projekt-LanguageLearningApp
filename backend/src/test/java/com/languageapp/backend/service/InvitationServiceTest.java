package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {
    @Mock InvitationLinkRepository invitations;
    @Mock UserRepository users;
    @Mock ClassroomRepository classrooms;
    @Mock ClassroomMemberRepository members;
    @Mock FriendshipRepository friendships;
    @Mock ClassroomService classroomService;
    @Mock FriendshipService friendshipService;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC);
    @InjectMocks InvitationService service;
    User owner, actor;
    InvitationLink link;
    @BeforeEach void setup() {
        owner = user("owner", Role.TEACHER); actor = user("actor", Role.STUDENT);
        link = new InvitationLink(); link.setKind("friend"); link.setOwner(owner); link.setToken("a".repeat(43));
    }
    User user(String name, Role role) {
        User user = new User(); user.setUserId(UUID.randomUUID()); user.setName(name); user.setUserTag("1234");
        user.setEmail(name+"@example.test"); user.setRole(role); user.setVerified(true); return user;
    }
    void previewSetup() {
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(invitations.findByToken(link.getToken())).thenReturn(Optional.of(link));
    }
    Classroom classroom() {
        Course course = new Course(); course.setLanguageName("Angol");
        Classroom room = new Classroom(); room.setClassroomId(UUID.randomUUID()); room.setName("Osztály"); room.setTeacher(owner); room.setCourse(course);
        link.setKind("classroom"); link.setClassroom(room);
        lenient().when(classrooms.existsById(room.getClassroomId())).thenReturn(true);
        return room;
    }
    @Test void creatingAndCopyingAReusableLinkKeepsTheSameTokenUntilExplicitRotation() {
        when(users.findByEmailForInvitationUpdate(owner.getEmail())).thenReturn(Optional.of(owner));
        var first = service.manage(owner.getEmail(), "friend", null, "current");
        assertTrue(first.token().matches("[A-Za-z0-9_-]{43}"));
        var saved = ArgumentCaptor.forClass(InvitationLink.class); verify(invitations).save(saved.capture());
        assertNull(saved.getValue().getExpiresAt());
        when(invitations.findScopeForUpdate("friend:"+owner.getUserId())).thenReturn(Optional.of(saved.getValue()));
        assertEquals(first.token(), service.manage(owner.getEmail(), "friend", null, "current").token());
        assertNotEquals(first.token(), service.manage(owner.getEmail(), "friend", null, "rotate").token());
        service.manage(owner.getEmail(), "friend", null, "revoke");
        assertNull(saved.getValue().getToken()); assertNotNull(saved.getValue().getRevokedAt());
        verifyNoInteractions(classroomService, friendshipService, members, friendships);
    }
    @Test void anotherTeachersClassroomCannotBeManaged() {
        Classroom room = classroom();
        when(users.findByEmailForInvitationUpdate(actor.getEmail())).thenReturn(Optional.of(actor));
        when(classrooms.findForInvitationUpdate(room.getClassroomId())).thenReturn(Optional.of(room));
        assertThrows(ForbiddenException.class, () -> service.manage(actor.getEmail(), "classroom", room.getClassroomId(), "rotate"));
        verify(invitations, never()).save(any());
    }
    @Test void previewContainsNoPrivateEmailOrTokenAndNeverCreatesConnections() {
        previewSetup(); var preview = service.preview(actor.getEmail(), "friend", link.getToken());
        assertEquals("READY", preview.state());
        assertFalse(preview.toString().contains(owner.getEmail())); assertFalse(preview.toString().contains(link.getToken()));
        verifyNoInteractions(friendshipService, classroomService); verify(invitations, never()).save(any());
    }
    @Test void wrongKindAndRevokedOrExpiredLinksAreUnavailable() {
        previewSetup();
        assertThrows(ResourceNotFoundException.class, () -> service.preview(actor.getEmail(), "classroom", link.getToken()));
        link.setExpiresAt(clock.instant());
        assertThrows(ResourceNotFoundException.class, () -> service.preview(actor.getEmail(), "friend", link.getToken()));
        link.setExpiresAt(null); link.setRevokedAt(clock.instant());
        assertThrows(ResourceNotFoundException.class, () -> service.preview(actor.getEmail(), "friend", link.getToken()));
    }
    @Test void unverifiedActorCannotPreviewOrConfirm() {
        actor.setVerified(false); when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        assertThrows(ForbiddenException.class, () -> service.preview(actor.getEmail(), "friend", link.getToken()));
        assertThrows(ForbiddenException.class, () -> service.confirm(actor.getEmail(), "friend", link.getToken()));
        verifyNoInteractions(invitations, friendshipService, classroomService);
    }
    @Test void inactiveOwnerCannotReceiveANewConnection() {
        previewSetup(); owner.setActive(false);
        assertThrows(ResourceNotFoundException.class, () -> service.preview(actor.getEmail(), "friend", link.getToken()));
    }
    @Test void archivedClassroomIsAnInvalidLink() {
        Classroom room = classroom(); previewSetup(); when(classrooms.existsById(room.getClassroomId())).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.preview(actor.getEmail(), "classroom", link.getToken()));
    }
    @Test void teacherCannotJoinAnotherClassroomAsAStudent() {
        Classroom room = classroom(); actor.setRole(Role.TEACHER); previewSetup();
        assertEquals("FORBIDDEN", service.preview(actor.getEmail(), "classroom", link.getToken()).state());
        when(classrooms.findForInvitationUpdate(room.getClassroomId())).thenReturn(Optional.of(room));
        when(invitations.findByTokenForUpdate(link.getToken())).thenReturn(Optional.of(link));
        assertThrows(ForbiddenException.class, () -> service.confirm(actor.getEmail(), "classroom", link.getToken()));
        verifyNoInteractions(classroomService, friendshipService);
    }
    @Test void pendingMembershipIsIdempotentAndDoesNotCreateAnotherRecord() {
        Classroom room = classroom(); previewSetup();
        ClassroomMember member = new ClassroomMember(); member.setStatus(MembershipStatus.PENDING);
        when(members.findByClassroom_ClassroomIdAndUser_UserId(room.getClassroomId(), actor.getUserId())).thenReturn(Optional.of(member));
        when(classrooms.findForInvitationUpdate(room.getClassroomId())).thenReturn(Optional.of(room));
        when(invitations.findByTokenForUpdate(link.getToken())).thenReturn(Optional.of(link));
        assertEquals("PENDING", service.confirm(actor.getEmail(), "classroom", link.getToken()).state());
        verifyNoInteractions(classroomService);
    }
    @Test void incomingFriendRequestIsPresentedWithoutAutomaticAcceptance() {
        previewSetup(); Friendship request = new Friendship(); request.setUser(owner); request.setFriend(actor); request.setStatus(FriendshipStatus.PENDING);
        when(friendships.findConnectionsBetween(actor.getUserId(), owner.getUserId())).thenReturn(List.of(request));
        assertEquals("INCOMING", service.preview(actor.getEmail(), "friend", link.getToken()).state());
        when(invitations.findByTokenForUpdate(link.getToken())).thenReturn(Optional.of(link));
        assertEquals("INCOMING", service.confirm(actor.getEmail(), "friend", link.getToken()).state());
        verifyNoInteractions(friendshipService);
    }
    @Test void tokenIsRecheckedUnderALockAtConfirmation() {
        previewSetup(); when(invitations.findByTokenForUpdate(link.getToken())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.confirm(actor.getEmail(), "friend", link.getToken()));
        verifyNoInteractions(friendshipService, classroomService);
    }
    @Test void ownFriendLinkDoesNotCreateASelfRelationship() {
        actor = owner; previewSetup();
        assertEquals("SELF", service.preview(actor.getEmail(), "friend", link.getToken()).state());
        verifyNoInteractions(friendshipService);
    }
}
