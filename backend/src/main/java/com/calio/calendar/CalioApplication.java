package com.calio.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
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

interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStartAtBetweenOrderByStartAtAsc(OffsetDateTime from, OffsetDateTime to);
}

@JsonIgnoreProperties(ignoreUnknown = false)
record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt
) {
}

record EventResponse(
        Long id,
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Instant createdAt,
        Instant updatedAt
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

    static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    static ErrorResponse from(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}

enum ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "startAt must be earlier than endAt."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    HttpStatus getHttpStatus() {
        return httpStatus;
    }

    String getMessage() {
        return message;
    }
}

class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    ErrorCode getErrorCode() {
        return errorCode;
    }
}

class EventNotFoundException extends BusinessException {

    EventNotFoundException(Long eventId) {
        super(ErrorCode.EVENT_NOT_FOUND, "Event not found: " + eventId);
    }
}

class InvalidTimeRangeException extends BusinessException {

    InvalidTimeRangeException() {
        super(ErrorCode.INVALID_TIME_RANGE);
    }
}

class ValidationFailedException extends BusinessException {

    ValidationFailedException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}

@Service
class EventService {

    private final EventRepository eventRepository;

    EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        validateCreateTimeRange(request.startAt(), request.endAt());

        Event event = new Event(
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt()
        );
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(OffsetDateTime from, OffsetDateTime to) {
        validateListRange(from, to);

        return eventRepository.findByStartAtBetweenOrderByStartAtAsc(from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    private void validateCreateTimeRange(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new InvalidTimeRangeException();
        }
    }

    private void validateListRange(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new ValidationFailedException("from must be earlier than or equal to to.");
        }
    }
}

@RestController
@RequestMapping("/api/events")
class EventController {

    private final EventService eventService;

    EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping
    public List<EventResponse> listEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return eventService.listEvents(from, to);
    }
}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.from(errorCode, exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationException(Exception exception) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ErrorResponse.from(ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
