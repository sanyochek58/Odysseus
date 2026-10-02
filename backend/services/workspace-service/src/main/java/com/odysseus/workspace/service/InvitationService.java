package com.odysseus.workspace.service;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.InvitationRequest;
import com.odysseus.workspace.dto.InvitationResponse;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.Invitation;
import com.odysseus.workspace.entity.InvitationStatus;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.ForbiddenOperationException;
import com.odysseus.workspace.exception.InvalidRequestException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.InvitationMapper;
import com.odysseus.workspace.mapper.MemberMapper;
import com.odysseus.workspace.repository.InvitationRepository;
import com.odysseus.workspace.repository.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Приглашения в текущий workspace. Приглашённый принимает его уже с токеном этой организации
 * (членство в организации Keycloak выдаётся отдельно), приём создаёт запись Member.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    public static final Duration TTL = Duration.ofDays(7);

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final InvitationMapper invitationMapper;
    private final MemberMapper memberMapper;
    private final Clock clock;

    @Transactional
    public InvitationResponse create(InvitationRequest request, String invitedBy) {
        if (request.role() == WorkspaceRole.OWNER) {
            throw new InvalidRequestException("Приглашать можно только в роли ниже OWNER");
        }
        String email = normalize(request.email());
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Пользователь уже участник workspace");
        }
        if (invitationRepository.existsByEmailAndStatus(email, InvitationStatus.PENDING)) {
            throw new ConflictException("Приглашение для этого email уже отправлено");
        }
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .email(email)
                .role(request.role())
                .status(InvitationStatus.PENDING)
                .invitedBy(invitedBy)
                .expiresAt(clock.instant().plus(TTL))
                .build());
        return invitationMapper.toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> list(Pageable pageable) {
        return invitationRepository.findAll(pageable).map(invitationMapper::toResponse);
    }

    @Transactional
    public InvitationResponse revoke(UUID id) {
        Invitation invitation = find(id);
        requirePending(invitation);
        invitation.setStatus(InvitationStatus.REVOKED);
        return invitationMapper.toResponse(invitationRepository.save(invitation));
    }

    /** Принимает приглашение от имени текущего пользователя: email токена должен совпасть с приглашённым. */
    @Transactional
    public MemberResponse accept(UUID id, String userId, String email) {
        Invitation invitation = find(id);
        // Сначала email (403), потом статус и срок (409): статус чужого приглашения не раскрывается.
        if (email == null || !normalize(email).equals(invitation.getEmail())) {
            throw new ForbiddenOperationException("Приглашение выдано на другой email");
        }
        requirePending(invitation);
        if (!clock.instant().isBefore(invitation.getExpiresAt())) {
            throw new ConflictException("Срок приглашения истёк");
        }
        if (memberRepository.existsByUserId(userId)) {
            throw new ConflictException("Пользователь уже участник workspace");
        }
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        Member member = memberRepository.save(Member.builder()
                .userId(userId)
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .build());
        return memberMapper.toResponse(member);
    }

    private void requirePending(Invitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("Приглашение уже обработано");
        }
    }

    private Invitation find(UUID id) {
        return invitationRepository.findById(id).orElseThrow(() -> new NotFoundException("Приглашение не найдено"));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase();
    }
}
