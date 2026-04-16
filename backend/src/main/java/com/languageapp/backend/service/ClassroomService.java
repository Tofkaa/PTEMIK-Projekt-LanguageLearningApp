package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ClassroomCreateRequest;
import com.languageapp.backend.dto.response.ClassroomMemberResponse;
import com.languageapp.backend.dto.response.ClassroomResponse;
import com.languageapp.backend.entity.Classroom;
import com.languageapp.backend.entity.ClassroomMember;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ClassroomMemberRepository;
import com.languageapp.backend.repository.ClassroomRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Service handling business logic for virtual classrooms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final UserRepository userRepository;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates a new classroom. Only TEACHER users should access this.
     */
    @Transactional
    public ClassroomResponse createClassroom(ClassroomCreateRequest request, String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found."));

        if (!Role.TEACHER.equals(teacher.getRole())) {
            throw new BadRequestException("Only teachers can create classrooms.");
        }

        Classroom classroom = new Classroom();
        classroom.setName(request.getName());
        classroom.setDescription(request.getDescription());
        classroom.setTeacher(teacher);

        String inviteCode;
        do {
            inviteCode = generateInviteCode();
        } while (classroomRepository.existsByInviteCode(inviteCode));

        classroom.setInviteCode(inviteCode);

        Classroom savedClassroom = classroomRepository.save(classroom);
        log.info("Classroom created successfully: {} with code {}", savedClassroom.getName(), savedClassroom.getInviteCode());

        return mapToResponse(savedClassroom, 0);
    }

    /**
     * Allows a student to request joining a classroom via an invite code.
     */
    @Transactional
    public void joinClassroom(String inviteCode, String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));

        Classroom classroom = classroomRepository.findByInviteCode(inviteCode.toUpperCase())
                .orElseThrow(() -> new BadRequestException("Invalid invite code."));

        if (classroom.getTeacher().getUserId().equals(student.getUserId())) {
            throw new BadRequestException("You cannot join your own classroom.");
        }

        if (classroomMemberRepository.existsByClassroom_ClassroomIdAndUser_UserId(classroom.getClassroomId(), student.getUserId())) {
            throw new BadRequestException("You are already a member or have a pending request for this classroom.");
        }

        ClassroomMember member = new ClassroomMember();
        member.setClassroom(classroom);
        member.setUser(student);
        member.setStatus(MembershipStatus.PENDING);

        classroomMemberRepository.save(member);
        log.info("Join request created for student {} to classroom {}", student.getEmail(), classroom.getName());
    }

    /**
     * Teacher approves or rejects a student's join request.
     */
    @Transactional
    public void moderateJoinRequest(UUID classroomId, UUID studentId, String teacherEmail, boolean isApproved) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));

        if (!classroom.getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("You do not have permission to moderate this classroom.");
        }

        ClassroomMember member = classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(classroomId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found."));

        if (isApproved) {
            member.setStatus(MembershipStatus.ACCEPTED);
            classroomMemberRepository.save(member);
            log.info("Student {} accepted into classroom {}", studentId, classroomId);
        } else {
            classroomMemberRepository.delete(member);
            log.info("Student {} rejected from classroom {}", studentId, classroomId);
        }
    }

    /**
     * Visszaadja egy adott tanár által létrehozott összes osztálytermet.
     */
    @Transactional(readOnly = true)
    public List<ClassroomResponse> getClassroomsForTeacher(String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found."));

        return classroomRepository.findAllByTeacher_UserIdOrderByCreatedAtDesc(teacher.getUserId())
                .stream()
                .map(classroom -> {
                    int activeCount = classroomMemberRepository.countByClassroom_ClassroomIdAndStatus(
                            classroom.getClassroomId(), MembershipStatus.ACCEPTED);
                    return mapToResponse(classroom, activeCount);
                })
                .toList();
    }

    /**
     * Returns classrooms where the student is accepted
     */
    @Transactional(readOnly = true)
    public List<ClassroomResponse> getClassroomsForStudent(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));

        return classroomMemberRepository.findAllByUser_UserIdAndStatus(student.getUserId(), MembershipStatus.ACCEPTED)
                .stream()
                .map(member -> {
                    Classroom classroom = member.getClassroom();
                    // Diákoknál is lekérjük az aktív létszámot, hogy lássák, mekkora az osztály
                    int activeCount = classroomMemberRepository.countByClassroom_ClassroomIdAndStatus(
                            classroom.getClassroomId(), MembershipStatus.ACCEPTED);
                    return mapToResponse(classroom, activeCount);
                })
                .toList();
    }

    /**
     * Gets classroom members filtered by status.
     * Both the owner (teacher) and accepted students can access the ACCEPTED list.
     * Only the owner can access the PENDING list.
     */
    @Transactional(readOnly = true)
    public List<ClassroomMemberResponse> getClassroomMembers(UUID classroomId, MembershipStatus status, String userEmail) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));

        User requestingUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        boolean isTeacher = classroom.getTeacher().getUserId().equals(requestingUser.getUserId());

        if (!isTeacher) {

            ClassroomMember member = classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(classroomId, requestingUser.getUserId())
                    .orElse(null);


            if (member == null || member.getStatus() != MembershipStatus.ACCEPTED || status == MembershipStatus.PENDING) {
                throw new ForbiddenException("Nincs jogosultságod a tagok megtekintéséhez.");
            }
        }

        return classroomMemberRepository.findAllByClassroom_ClassroomIdAndStatus(classroomId, status)
                .stream()
                .map(member -> new com.languageapp.backend.dto.response.ClassroomMemberResponse(
                        member.getClassroomMemberId(),
                        member.getUser().getUserId(),
                        member.getUser().getName(),
                        member.getUser().getEmail(),
                        member.getUser().getUserTag(),
                        member.getStatus(),
                        member.getJoinedAt()
                ))
                .toList();
    }

    /**
     * Get classroom member statistics.
     */
    @Transactional(readOnly = true)
    public List<com.languageapp.backend.dto.projection.ClassroomMemberStatDTO> getClassroomStats(UUID classroomId, String teacherEmail) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));

        if (!classroom.getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("You do not have permission to view stats for this classroom.");
        }

        return classroomMemberRepository.getClassroomStats(classroomId);
    }

    /**
     * Removes a student from the classroom (Kick).
     */
    @Transactional
    public void kickStudent(UUID classroomId, UUID studentId, String teacherEmail) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));


        if (!classroom.getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("You do not have permission to perform this action.");
        }

        ClassroomMember member = classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(classroomId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student is not a member of this classroom."));

        classroomMemberRepository.delete(member);
        log.info("Student {} was kicked from classroom {} by teacher {}", studentId, classroomId, teacherEmail);
    }

    // --- Helper methods ---

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private ClassroomResponse mapToResponse(Classroom classroom, int activeMemberCount) {
        return new ClassroomResponse(
                classroom.getClassroomId(),
                classroom.getName(),
                classroom.getDescription(),
                classroom.getInviteCode(),
                classroom.getTeacher().getName(),
                classroom.getCreatedAt(),
                activeMemberCount
        );
    }


}