package com.odysseus.workspace.service;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.MemberMapper;
import com.odysseus.workspace.repository.MemberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Участники текущего workspace. Инвариант: в workspace всегда остаётся хотя бы один OWNER. */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    @Transactional(readOnly = true)
    public Page<MemberResponse> list(Pageable pageable) {
        return memberRepository.findAll(pageable).map(memberMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(UUID id) {
        return memberMapper.toResponse(find(id));
    }

    @Transactional
    public MemberResponse changeRole(UUID id, WorkspaceRole role) {
        Member member = find(id);
        if (member.getRole() == WorkspaceRole.OWNER && role != WorkspaceRole.OWNER) {
            ensureNotLastOwner();
        }
        member.setRole(role);
        return memberMapper.toResponse(memberRepository.save(member));
    }

    @Transactional
    public void remove(UUID id) {
        Member member = find(id);
        if (member.getRole() == WorkspaceRole.OWNER) {
            ensureNotLastOwner();
        }
        memberRepository.delete(member);
    }

    /** Читает владельцев с PESSIMISTIC_WRITE: параллельные понижения и удаления идут по очереди. */
    private void ensureNotLastOwner() {
        if (memberRepository.findAllByRole(WorkspaceRole.OWNER).size() <= 1) {
            throw new ConflictException("Нельзя лишить workspace последнего владельца");
        }
    }

    private Member find(UUID id) {
        return memberRepository.findById(id).orElseThrow(() -> new NotFoundException("Участник не найден"));
    }
}
