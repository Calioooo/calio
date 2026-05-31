package com.calio.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@EnableJpaAuditing
@SpringBootApplication
public class CalioApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalioApplication.class, args);
    }

}

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

@Entity
@Table(name = "events")
class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(nullable = false)
    private OffsetDateTime startAt;

    @Column(nullable = false)
    private OffsetDateTime endAt;

    protected Event() {
    }

    Event(String title, String description, OffsetDateTime startAt, OffsetDateTime endAt) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }
}

@Repository
class EventRepository {

    @PersistenceContext
    private EntityManager entityManager;

    Event save(Event event) {
        entityManager.persist(event);
        return event;
    }

    Event findById(Long eventId) {
        return entityManager.find(Event.class, eventId);
    }

    List<Event> findByStartAtBetweenOrderByStartAtAsc(OffsetDateTime from, OffsetDateTime to) {
        return entityManager.createQuery("""
                        select event
                        from Event event
                        where event.startAt between :from and :to
                        order by event.startAt asc
                        """, Event.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt
) {

    Event toEntity() {
        return new Event(title, description, startAt, endAt);
    }
}

record EventResponse(
        Long id,
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}

record ErrorResponse(String errorCode, String message) {
}

enum ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    HttpStatus getStatus() {
        return status;
    }

    String getDefaultMessage() {
        return defaultMessage;
    }
}

class CalendarException extends RuntimeException {

    private final ErrorCode errorCode;

    CalendarException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    ErrorCode getErrorCode() {
        return errorCode;
    }
}

@Service
class EventService {

    private final EventRepository eventRepository;

    EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    EventResponse createEvent(CreateEventRequest request) {
        validateEventTimeRange(request.startAt(), request.endAt());
        Event event = eventRepository.save(request.toEntity());
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    List<EventResponse> listEvents(OffsetDateTime from, OffsetDateTime to) {
        validateListTimeRange(from, to);
        return eventRepository.findByStartAtBetweenOrderByStartAtAsc(from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    private Event findEvent(Long eventId) {
        Event event = eventRepository.findById(eventId);
        if (event != null) {
            return event;
        }

        throw new CalendarException(ErrorCode.EVENT_NOT_FOUND);
    }

    private void validateEventTimeRange(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt.isBefore(endAt)) {
            return;
        }

        throw new CalendarException(ErrorCode.INVALID_TIME_RANGE);
    }

    private void validateListTimeRange(OffsetDateTime from, OffsetDateTime to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalendarException(ErrorCode.INVALID_TIME_RANGE);
    }
}

@Validated
@RestController
@RequestMapping("/api/events")
class EventController {

    private final EventService eventService;

    EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{eventId}")
    EventResponse getEvent(@PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping
    List<EventResponse> listEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return eventService.listEvents(from, to);
    }
}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CalendarException.class)
    ResponseEntity<ErrorResponse> handleCalendarException(CalendarException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(toResponse(errorCode, exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ErrorResponse> handleValidationException(Exception exception) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(toResponse(errorCode, errorCode.getDefaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(toResponse(errorCode, errorCode.getDefaultMessage()));
    }

    private ErrorResponse toResponse(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
