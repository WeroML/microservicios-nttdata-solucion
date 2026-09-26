package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.api.dto.AnnouncementRequest;
import tacos.api.error.BusinessRuleException;
import tacos.api.error.ConflictException;
import tacos.api.error.NotFoundException;
import tacos.testsupport.Fixtures;

// TC-33: anuncios operativos persistentes, acotados y validados.
class AnnouncementServiceTest {

    private OpsAnnouncementRepository repo;
    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        repo = mock(OpsAnnouncementRepository.class);
        service = new AnnouncementService(repo, new AnnouncementProperties(), Fixtures.CLOCK);
        when(repo.countByActiveTrueAndExpiresAtAfter(any())).thenReturn(Mono.just(0L));
        when(repo.save(any())).thenAnswer(inv -> {
            OpsAnnouncement a = inv.getArgument(0);
            a.setId("a1");
            return Mono.just(a);
        });
    }

    private static AnnouncementRequest request(String text) {
        AnnouncementRequest request = new AnnouncementRequest();
        request.setText(text);
        request.setSeverity(OpsAnnouncement.Severity.WARNING);
        return request;
    }

    @Test
    void createsWithStableIdAuthorAndDefaultExpiration() {
        StepVerifier.create(service.create(request("  Kitchen closes early  "), "admin"))
            .assertNext(resp -> {
                assertThat(resp.getId()).isEqualTo("a1");
                assertThat(resp.getText()).isEqualTo("Kitchen closes early");
                assertThat(resp.getExpiresAt()).isEqualTo(Fixtures.CLOCK.instant().plus(Duration.ofDays(7)));
            })
            .verifyComplete();
        ArgumentCaptor<OpsAnnouncement> saved = ArgumentCaptor.forClass(OpsAnnouncement.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("admin");
    }

    @Test
    void expirationBeyondLimitOrInThePastIsRejected() {
        AnnouncementRequest tooFar = request("Too far");
        tooFar.setExpiresAt(Fixtures.CLOCK.instant().plus(Duration.ofDays(31)));
        AnnouncementRequest past = request("Past");
        past.setExpiresAt(Fixtures.CLOCK.instant().minusSeconds(1));

        StepVerifier.create(service.create(tooFar, "admin")).expectError(BusinessRuleException.class).verify();
        StepVerifier.create(service.create(past, "admin")).expectError(BusinessRuleException.class).verify();
        verify(repo, never()).save(any());
    }

    @Test
    void activeAnnouncementsAreBounded() {
        when(repo.countByActiveTrueAndExpiresAtAfter(any())).thenReturn(Mono.just(10L));
        StepVerifier.create(service.create(request("One more"), "admin")).expectError(ConflictException.class).verify();
    }

    @Test
    void expiredAnnouncementsAreNotListedAndAuthorIsNotExposed() {
        OpsAnnouncement active = new OpsAnnouncement();
        active.setId("a1");
        active.setText("Hi");
        active.setSeverity(OpsAnnouncement.Severity.INFO);
        active.setCreatedBy("admin");
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        when(repo.findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(now.capture())).thenReturn(Flux.just(active));

        StepVerifier.create(service.active())
            .assertNext(resp -> assertThat(resp.toString()).doesNotContain("admin"))
            .verifyComplete();
        assertThat(now.getValue()).isEqualTo(Fixtures.CLOCK.instant());
    }

    @Test
    void deleteByIdRemovesTheRightOneOr404() {
        OpsAnnouncement target = new OpsAnnouncement();
        target.setId("a2");
        when(repo.findById(anyString())).thenReturn(Mono.empty());
        when(repo.findById("a2")).thenReturn(Mono.just(target));
        when(repo.delete(target)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete("a2")).verifyComplete();
        verify(repo).delete(target);
        StepVerifier.create(service.delete("zzz")).expectError(NotFoundException.class).verify();
    }

    @Test
    void textIsValidatedAgainstEmptyLongAndControlCharacters() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(request(""))).isNotEmpty();
        assertThat(validator.validate(request("line\nbreak"))).isNotEmpty();
        assertThat(validator.validate(request(new String(new char[281]).replace('\0', 'x')))).isNotEmpty();
        assertThat(validator.validate(request("Kitchen closes at 9pm"))).isEmpty();
    }
}
