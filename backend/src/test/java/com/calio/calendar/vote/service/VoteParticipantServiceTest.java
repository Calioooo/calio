package com.calio.calendar.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.SubmitVoteRequest;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class VoteParticipantServiceTest {

    private static final UUID VOTE_ROOM_PUBLIC_ID = UUID.fromString("7ab6b7d8-11cd-4ce2-83e3-b81ad87ea3c9");

    @Mock
    private VoteParticipantQueryService voteParticipantQueryService;

    @Mock
    private VoteParticipantCommandService voteParticipantCommandService;

    @Mock
    private VoteCommandService voteCommandService;

    private VoteParticipantService voteParticipantService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = org.mockito.Mockito.spy(new BCryptPasswordEncoder());
        voteParticipantService = new VoteParticipantService(
                voteParticipantQueryService,
                voteParticipantCommandService,
                voteCommandService,
                passwordEncoder
        );
    }

    @Test
    @DisplayName("새 참여자는 VoteRoom에 연결된 REGISTERED 상태로 생성된다")
    void givenAvailableNicknameWithoutPassword_whenCreate_thenCreatesRegisteredParticipant() {
        // given
        VoteRoom voteRoom = voteRoom();
        when(voteParticipantCommandService.getVoteRoomForParticipantCreation(VOTE_ROOM_PUBLIC_ID))
                .thenReturn(voteRoom);
        when(voteParticipantQueryService.getParticipantByVoteRoomPublicIdAndNicknameIfExists(
                VOTE_ROOM_PUBLIC_ID,
                "calio"
        )).thenReturn(Optional.empty());
        when(voteParticipantCommandService.create(any(VoteParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VoteParticipant participant = voteParticipantService.create(VOTE_ROOM_PUBLIC_ID, "calio", null);

        // then
        ArgumentCaptor<VoteParticipant> participantCaptor = ArgumentCaptor.forClass(VoteParticipant.class);
        verify(voteParticipantCommandService).create(participantCaptor.capture());
        assertThat(participant).isSameAs(participantCaptor.getValue());
        assertThat(participant.getVoteRoom()).isSameAs(voteRoom);
        assertThat(participant.getNickname()).isEqualTo("calio");
        assertThat(participant.getPasswordHash()).isNull();
        assertThat(participant.getStatus()).isEqualTo(VoteParticipantStatus.REGISTERED);
    }

    @Test
    @DisplayName("비밀번호가 있는 새 참여자는 원문 대신 BCrypt 해시를 저장한다")
    void givenAvailableNicknameWithPassword_whenCreate_thenStoresPasswordHashOnly() {
        // given
        VoteRoom voteRoom = voteRoom();
        when(voteParticipantCommandService.getVoteRoomForParticipantCreation(VOTE_ROOM_PUBLIC_ID))
                .thenReturn(voteRoom);
        when(voteParticipantQueryService.getParticipantByVoteRoomPublicIdAndNicknameIfExists(
                VOTE_ROOM_PUBLIC_ID,
                "calio"
        )).thenReturn(Optional.empty());
        when(voteParticipantCommandService.create(any(VoteParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VoteParticipant participant = voteParticipantService.create(
                VOTE_ROOM_PUBLIC_ID,
                "calio",
                "participant-password"
        );

        // then
        assertThat(participant.getPasswordHash()).isNotEqualTo("participant-password");
        assertThat(participant.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("participant-password", participant.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("분해형 한글 닉네임은 NFC로 정규화한 값으로 중복을 조회하고 저장한다")
    void givenDecomposedKoreanNickname_whenCreate_thenUsesNfcNormalizedNickname() {
        // given
        VoteRoom voteRoom = voteRoom();
        String normalizedNickname = "캘리오";
        String decomposedNickname = Normalizer.normalize(normalizedNickname, Normalizer.Form.NFD);
        when(voteParticipantCommandService.getVoteRoomForParticipantCreation(VOTE_ROOM_PUBLIC_ID))
                .thenReturn(voteRoom);
        when(voteParticipantQueryService.getParticipantByVoteRoomPublicIdAndNicknameIfExists(
                VOTE_ROOM_PUBLIC_ID,
                normalizedNickname
        )).thenReturn(Optional.empty());
        when(voteParticipantCommandService.create(any(VoteParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VoteParticipant participant = voteParticipantService.create(VOTE_ROOM_PUBLIC_ID, decomposedNickname, null);

        // then
        verify(voteParticipantQueryService)
                .getParticipantByVoteRoomPublicIdAndNicknameIfExists(VOTE_ROOM_PUBLIC_ID, normalizedNickname);
        ArgumentCaptor<VoteParticipant> participantCaptor = ArgumentCaptor.forClass(VoteParticipant.class);
        verify(voteParticipantCommandService).create(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getNickname()).isEqualTo(normalizedNickname);
        assertThat(participant.getNickname()).isEqualTo(normalizedNickname);
    }

    @Test
    @DisplayName("같은 VoteRoom의 닉네임은 대소문자와 무관하게 중복 생성할 수 없다")
    void givenDuplicateNickname_whenCreate_thenRejectsBeforeHashingOrSaving() {
        // given
        VoteRoom voteRoom = voteRoom();
        when(voteParticipantCommandService.getVoteRoomForParticipantCreation(VOTE_ROOM_PUBLIC_ID))
                .thenReturn(voteRoom);
        when(voteParticipantQueryService.getParticipantByVoteRoomPublicIdAndNicknameIfExists(
                VOTE_ROOM_PUBLIC_ID,
                "Calio"
        )).thenReturn(Optional.of(new VoteParticipant(voteRoom, "calio", null)));

        // when, then
        assertThatThrownBy(() -> voteParticipantService.create(
                VOTE_ROOM_PUBLIC_ID,
                "Calio",
                "participant-password"
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT)
        );
        verify(voteParticipantCommandService, never()).create(any());
    }

    @Test
    @DisplayName("참여자 닉네임은 기존 Group Space와 같은 형식 규칙을 적용한다")
    void givenInvalidNickname_whenCreate_thenRejectsBeforeNicknameLookup() {
        // when, then
        assertThatThrownBy(() -> voteParticipantService.create(VOTE_ROOM_PUBLIC_ID, "calio-user", null))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
        verify(voteParticipantQueryService, never())
                .getParticipantByVoteRoomPublicIdAndNicknameIfExists(any(), any());
        verifyNoInteractions(voteParticipantCommandService);
    }

    @Test
    @DisplayName("존재하지 않는 공개 VoteRoom에는 참여자를 생성할 수 없다")
    void givenMissingVoteRoom_whenCreate_thenRejectsBeforeNicknameLookup() {
        // given
        when(voteParticipantCommandService.getVoteRoomForParticipantCreation(VOTE_ROOM_PUBLIC_ID))
                .thenThrow(new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));

        // when, then
        assertThatThrownBy(() -> voteParticipantService.create(VOTE_ROOM_PUBLIC_ID, "calio", null))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VOTE_ROOM_NOT_FOUND)
                );
        verifyNoInteractions(voteParticipantQueryService);
    }

    @Test
    @DisplayName("투표 제출은 비잠금 인증을 마친 뒤 참여자 쓰기 잠금을 획득한다")
    void givenValidCredential_whenSubmitVotes_thenVerifiesCredentialBeforeAcquiringLock() {
        // given
        VoteParticipant participant = new VoteParticipant(voteRoom(), "calio", passwordEncoder.encode("secret"));
        when(voteParticipantQueryService.getParticipantByVoteRoomPublicIdAndNicknameIfExists(
                VOTE_ROOM_PUBLIC_ID,
                "calio"
        )).thenReturn(Optional.of(participant));
        when(voteParticipantCommandService.getParticipantForVoteSubmission(VOTE_ROOM_PUBLIC_ID, "calio"))
                .thenReturn(participant);

        // when
        voteParticipantService.submitVotes(VOTE_ROOM_PUBLIC_ID, new SubmitVoteRequest(
                "calio",
                "secret",
                java.util.List.of(LocalDate.of(2026, 8, 15))
        ));

        // then
        InOrder inOrder = inOrder(
                voteParticipantQueryService,
                passwordEncoder,
                voteParticipantCommandService,
                voteCommandService
        );
        inOrder.verify(voteParticipantQueryService)
                .getParticipantByVoteRoomPublicIdAndNicknameIfExists(VOTE_ROOM_PUBLIC_ID, "calio");
        inOrder.verify(passwordEncoder).matches("secret", participant.getPasswordHash());
        inOrder.verify(voteParticipantCommandService)
                .getParticipantForVoteSubmission(VOTE_ROOM_PUBLIC_ID, "calio");
        inOrder.verify(voteCommandService).replaceVotes(eq(participant), anyList());
    }

    @Test
    @DisplayName("잘못된 비밀번호의 투표 제출은 참여자 잠금과 Vote 교체를 실행하지 않는다")
    void givenInvalidPassword_whenSubmitVotes_thenRejectsBeforeAcquiringLock() {
        // given
        VoteParticipant participant = new VoteParticipant(voteRoom(), "calio", passwordEncoder.encode("secret"));
        when(voteParticipantQueryService.getParticipantByVoteRoomPublicIdAndNicknameIfExists(
                VOTE_ROOM_PUBLIC_ID,
                "calio"
        )).thenReturn(Optional.of(participant));

        // when, then
        assertThatThrownBy(() -> voteParticipantService.submitVotes(VOTE_ROOM_PUBLIC_ID, new SubmitVoteRequest(
                "calio",
                "wrong",
                java.util.List.of(LocalDate.of(2026, 8, 15))
        ))).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID)
        );
        verify(voteParticipantCommandService, never())
                .getParticipantForVoteSubmission(VOTE_ROOM_PUBLIC_ID, "calio");
        verifyNoInteractions(voteCommandService);
    }

    private VoteRoom voteRoom() {
        return new VoteRoom(
                VOTE_ROOM_PUBLIC_ID,
                "여행 일정",
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 20),
                new Account()
        );
    }
}
