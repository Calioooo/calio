package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.io.IOException;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TemporalAdapter;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.validate.ValidationException;
import org.springframework.stereotype.Component;

@Component
public class Ical4jRecurrenceEngine implements Rfc5545RecurrenceEngine {

    private static final String CONTENT_LINE_CALENDAR = "BEGIN:VCALENDAR\r\n"
            + "VERSION:2.0\r\n"
            + "PRODID:-//Calio//Recurrence Engine//EN\r\n"
            + "BEGIN:VEVENT\r\n"
            + "DTSTART:20000101T000000Z\r\n"
            + "%s\r\n"
            + "END:VEVENT\r\n"
            + "END:VCALENDAR\r\n";
    private static final Set<String> DATE_PARAMETER_NAMES = Set.of(Parameter.VALUE, Parameter.TZID);
    private static final Map<LineType, Integer> LINE_ORDER = Map.of(
            LineType.RRULE, 0,
            LineType.RDATE, 1,
            LineType.EXDATE, 2,
            LineType.EXRULE, 3
    );

    @Override
    public List<String> validate(RecurrenceSchedule schedule, List<String> recurrenceLines) {
        RecurrenceDefinition definition = parseDefinition(schedule, recurrenceLines);
        validateFirstOccurrence(schedule, definition);
        return definition.normalizedLines();
    }

    @Override
    public List<RecurrenceOccurrence> expand(
            RecurrenceSchedule schedule,
            List<String> recurrenceLines,
            Instant from,
            Instant to
    ) {
        if (!from.isBefore(to)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        RecurrenceDefinition definition = parseDefinition(schedule, recurrenceLines);
        return expand(schedule, definition, from, to);
    }

    @Override
    public boolean containsOrigin(
            RecurrenceSchedule schedule,
            List<String> recurrenceLines,
            Instant originStartAt
    ) {
        if (originStartAt == null || originStartAt.isBefore(schedule.startAt())) {
            return false;
        }
        try {
            Instant to = originStartAt.plusNanos(1);
            return expand(schedule, recurrenceLines, originStartAt, to).stream()
                    .anyMatch(occurrence -> occurrence.originStartAt().equals(originStartAt));
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private List<RecurrenceOccurrence> expand(
            RecurrenceSchedule schedule,
            RecurrenceDefinition definition,
            Instant from,
            Instant to
    ) {
        ExpansionWindow window = expansionWindow(schedule, from, to);
        Set<Instant> excludedStarts = excludedStarts(schedule, definition, window);
        Map<Instant, RecurrenceOccurrence> occurrences = new HashMap<>();

        // iCal4j may return floating, TZID, and UTC temporal types; set operations use canonical Instant identity.
        addOccurrence(schedule, scheduleSeed(schedule), excludedStarts, occurrences);
        definition.lines(LineType.RRULE).stream()
                .flatMap(line -> expandRule(schedule, line, window).stream())
                .forEach(candidate -> addOccurrence(schedule, candidate, excludedStarts, occurrences));
        definition.lines(LineType.RDATE).stream()
                .flatMap(line -> dateValues(line).stream())
                .forEach(candidate -> addOccurrence(schedule, candidate, excludedStarts, occurrences));

        return occurrences.values().stream()
                .filter(occurrence -> overlaps(occurrence.startAt(), occurrence.endAt(), from, to))
                .sorted(Comparator.comparing(RecurrenceOccurrence::originStartAt))
                .toList();
    }

    private Set<Instant> excludedStarts(
            RecurrenceSchedule schedule,
            RecurrenceDefinition definition,
            ExpansionWindow window
    ) {
        Set<Instant> excluded = new HashSet<>();
        definition.lines(LineType.EXDATE).stream()
                .flatMap(line -> dateValues(line).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .forEach(excluded::add);
        definition.lines(LineType.EXRULE).stream()
                .flatMap(line -> expandRule(schedule, line, window).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .forEach(excluded::add);
        return excluded;
    }

    private void addOccurrence(
            RecurrenceSchedule schedule,
            Temporal candidate,
            Set<Instant> excludedStarts,
            Map<Instant, RecurrenceOccurrence> occurrences
    ) {
        toOccurrence(schedule, candidate)
                .filter(occurrence -> !excludedStarts.contains(occurrence.originStartAt()))
                .ifPresent(occurrence -> occurrences.putIfAbsent(occurrence.originStartAt(), occurrence));
    }

    private Optional<RecurrenceOccurrence> toOccurrence(RecurrenceSchedule schedule, Temporal candidate) {
        if (schedule.allDay()) {
            LocalDate startDate = LocalDate.from(candidate);
            long durationDays = schedule.endDate().toEpochDay() - schedule.startDate().toEpochDay();
            Instant startAt = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant endAt = startDate.plusDays(durationDays).atStartOfDay().toInstant(ZoneOffset.UTC);
            return Optional.of(new RecurrenceOccurrence(startAt, startAt, endAt));
        }

        LocalDateTime startDateTime = toMasterLocalDateTime(schedule, candidate);
        LocalDateTime firstStart = schedule.startAt().atZone(schedule.zoneId()).toLocalDateTime();
        LocalDateTime firstEnd = schedule.endAt().atZone(schedule.zoneId()).toLocalDateTime();
        LocalDateTime endDateTime = startDateTime.plus(Duration.between(firstStart, firstEnd));
        Optional<Instant> explicitStartAt = explicitInstant(candidate);
        Optional<Instant> startAt = explicitStartAt.isPresent()
                ? explicitStartAt
                : resolveGeneratedTime(startDateTime, schedule.zoneId());
        if (startAt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> endAt = resolveOccurrenceEnd(
                endDateTime,
                schedule.zoneId(),
                startAt.get(),
                explicitStartAt.isPresent()
        );
        if (endAt.isEmpty() || !startAt.get().isBefore(endAt.get())) {
            return Optional.empty();
        }
        return Optional.of(new RecurrenceOccurrence(startAt.get(), startAt.get(), endAt.get()));
    }

    private Optional<Instant> occurrenceStart(RecurrenceSchedule schedule, Temporal candidate) {
        if (schedule.allDay()) {
            return Optional.of(LocalDate.from(candidate).atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        Optional<Instant> explicitStartAt = explicitInstant(candidate);
        if (explicitStartAt.isPresent()) {
            return explicitStartAt;
        }
        return resolveGeneratedTime(toMasterLocalDateTime(schedule, candidate), schedule.zoneId());
    }

    private Optional<Instant> explicitInstant(Temporal candidate) {
        if (!TemporalAdapter.isUtc(candidate)) {
            return Optional.empty();
        }
        return Optional.of(Instant.from(candidate));
    }

    private Optional<Instant> resolveGeneratedTime(LocalDateTime localDateTime, ZoneId zoneId) {
        List<ZoneOffset> offsets = zoneId.getRules().getValidOffsets(localDateTime);
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(localDateTime.toInstant(offsets.getFirst()));
    }

    private Optional<Instant> resolveOccurrenceEnd(
            LocalDateTime endDateTime,
            ZoneId zoneId,
            Instant startAt,
            boolean hasExplicitInstant
    ) {
        List<ZoneOffset> offsets = zoneId.getRules().getValidOffsets(endDateTime);
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        if (!hasExplicitInstant) {
            return Optional.of(endDateTime.toInstant(offsets.getFirst()));
        }
        ZoneOffset startOffset = zoneId.getRules().getOffset(startAt);
        ZoneOffset endOffset = offsets.stream()
                .filter(startOffset::equals)
                .findFirst()
                .orElse(offsets.getFirst());
        return Optional.of(endDateTime.toInstant(endOffset));
    }

    private Temporal scheduleSeed(RecurrenceSchedule schedule) {
        return schedule.allDay()
                ? schedule.startDate()
                : schedule.startAt().atZone(schedule.zoneId()).toLocalDateTime();
    }

    private List<Temporal> expandRule(
            RecurrenceSchedule schedule,
            ContentLine line,
            ExpansionWindow window
    ) {
        try {
            return recurrenceRule(line).getDates(
                    scheduleSeed(schedule),
                    window.localFrom(),
                    window.localTo()
            );
        } catch (RuntimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private ExpansionWindow expansionWindow(RecurrenceSchedule schedule, Instant from, Instant to) {
        if (schedule.allDay()) {
            long durationDays = schedule.endDate().toEpochDay() - schedule.startDate().toEpochDay();
            LocalDate localFrom = from.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(durationDays + 1);
            LocalDate localTo = to.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(2);
            return new ExpansionWindow(localFrom, localTo);
        }

        LocalDateTime firstStart = schedule.startAt().atZone(schedule.zoneId()).toLocalDateTime();
        LocalDateTime firstEnd = schedule.endAt().atZone(schedule.zoneId()).toLocalDateTime();
        Duration wallDuration = Duration.between(firstStart, firstEnd);
        LocalDateTime localFrom = from.atZone(schedule.zoneId()).toLocalDateTime()
                .minus(wallDuration)
                .minusDays(1);
        LocalDateTime localTo = to.atZone(schedule.zoneId()).toLocalDateTime().plusDays(1);
        return new ExpansionWindow(localFrom, localTo);
    }

    private RecurrenceDefinition parseDefinition(
            RecurrenceSchedule schedule,
            List<String> recurrenceLines
    ) {
        if (recurrenceLines == null) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        List<ContentLine> lines = recurrenceLines.stream()
                .map(this::parseContentLine)
                .map(line -> validateLine(schedule, line))
                .distinct()
                .sorted(Comparator
                        .comparing((ContentLine line) -> LINE_ORDER.get(line.type()))
                        .thenComparing(ContentLine::normalized))
                .toList();
        boolean hasInclusion = lines.stream()
                .anyMatch(line -> line.type() == LineType.RRULE || line.type() == LineType.RDATE);
        if (!hasInclusion) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        validateRdatesDoNotPrecedeStart(schedule, lines);
        return new RecurrenceDefinition(lines);
    }

    private ContentLine parseContentLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        try {
            net.fortuna.ical4j.model.Calendar calendar = new CalendarBuilder().build(
                    new StringReader(CONTENT_LINE_CALENDAR.formatted(rawLine.trim()))
            );
            Property property = recurrenceProperty(calendar);
            LineType type = lineType(property);
            validateRulePartCount(type, rawLine, property);
            return new ContentLine(type, property, normalize(property));
        } catch (CalioException exception) {
            throw exception;
        } catch (IOException | ParserException | RuntimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private Property recurrenceProperty(net.fortuna.ical4j.model.Calendar calendar) {
        List<?> components = calendar.getComponents();
        if (components.size() != 1 || !(components.getFirst() instanceof VEvent event)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        List<Property> properties = event.getPropertyList().getAll().stream()
                .filter(property -> !Property.DTSTART.equals(property.getName()))
                .toList();
        if (properties.size() != 1) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        return properties.getFirst();
    }

    private LineType lineType(Property property) {
        if (property instanceof RRule<?>) {
            return LineType.RRULE;
        }
        if (property instanceof RDate<?>) {
            return LineType.RDATE;
        }
        if (property instanceof ExDate<?>) {
            return LineType.EXDATE;
        }
        if (property instanceof ExRule<?>) {
            return LineType.EXRULE;
        }
        throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
    }

    private void validateRulePartCount(LineType type, String rawLine, Property property) {
        if (type != LineType.RRULE && type != LineType.EXRULE) {
            return;
        }
        long inputPartCount = rawLine.chars().filter(character -> character == '=').count();
        long parsedPartCount = property.getValue().chars().filter(character -> character == '=').count();
        if (inputPartCount != parsedPartCount) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private ContentLine validateLine(RecurrenceSchedule schedule, ContentLine line) {
        validateProperty(line.property());
        validateParameters(line);
        if (line.type() == LineType.RRULE || line.type() == LineType.EXRULE) {
            validateRuleValueType(schedule, line);
        } else {
            validateDateValueType(schedule, line);
        }
        return line;
    }

    private void validateProperty(Property property) {
        try {
            if (property.validate().hasErrors()) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
        } catch (ValidationException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private void validateParameters(ContentLine line) {
        List<Parameter> parameters = line.property().getParameterList().getAll();
        if (line.type() == LineType.RRULE || line.type() == LineType.EXRULE) {
            if (!parameters.isEmpty()) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
            return;
        }

        Set<String> parameterNames = new HashSet<>();
        boolean hasInvalidParameter = parameters.stream()
                .map(Parameter::getName)
                .anyMatch(name -> !DATE_PARAMETER_NAMES.contains(name) || !parameterNames.add(name));
        if (hasInvalidParameter) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private void validateRuleValueType(RecurrenceSchedule schedule, ContentLine line) {
        Recur<Temporal> recur = recurrenceRule(line);
        if (recur.getCount() > 0 && recur.getUntil() != null) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }

        Temporal until = recur.getUntil();
        if (until != null) {
            boolean validUntil = schedule.allDay()
                    ? until instanceof LocalDate
                    : TemporalAdapter.isDateTimePrecision(until) && TemporalAdapter.isUtc(until);
            if (!validUntil) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
        }

        boolean hasTimedPart = !recur.getHourList().isEmpty()
                || !recur.getMinuteList().isEmpty()
                || !recur.getSecondList().isEmpty();
        if (schedule.allDay() && hasTimedPart) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private void validateDateValueType(RecurrenceSchedule schedule, ContentLine line) {
        Value valueType = line.property().<Value>getParameter(Parameter.VALUE).orElse(null);
        TzId timeZone = line.property().<TzId>getParameter(Parameter.TZID).orElse(null);
        List<Temporal> dates = dateValues(line);
        if (dates.isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        if (schedule.allDay()) {
            validateAllDayDates(valueType, timeZone, dates);
            return;
        }
        validateTimedDates(schedule, valueType, timeZone, dates);
    }

    private void validateAllDayDates(Value valueType, TzId timeZone, List<Temporal> dates) {
        boolean hasInvalidValue = !Value.DATE.equals(valueType)
                || timeZone != null
                || dates.stream().anyMatch(date -> !(date instanceof LocalDate));
        if (hasInvalidValue) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private void validateTimedDates(
            RecurrenceSchedule schedule,
            Value valueType,
            TzId timeZone,
            List<Temporal> dates
    ) {
        boolean hasInvalidType = valueType != null && !Value.DATE_TIME.equals(valueType);
        boolean hasInvalidTimeZone = timeZone != null && !timeZone.getValue().equals(schedule.timeZone());
        boolean hasInvalidDate = dates.stream().anyMatch(date -> !TemporalAdapter.isDateTimePrecision(date));
        boolean hasGapDate = dates.stream()
                .filter(TemporalAdapter::isDateTimePrecision)
                .filter(date -> !TemporalAdapter.isUtc(date))
                .map(date -> toMasterLocalDateTime(schedule, date))
                .anyMatch(date -> resolveGeneratedTime(date, schedule.zoneId()).isEmpty());
        if (hasInvalidType || hasInvalidTimeZone || hasInvalidDate || hasGapDate) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    @SuppressWarnings("unchecked")
    private Recur<Temporal> recurrenceRule(ContentLine line) {
        if (line.property() instanceof RRule<?> rule) {
            return (Recur<Temporal>) rule.getRecur();
        }
        if (line.property() instanceof ExRule<?> rule) {
            return (Recur<Temporal>) rule.getRecur();
        }
        throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
    }

    private List<Temporal> dateValues(ContentLine line) {
        if (!(line.property() instanceof DateListProperty<?> dateProperty)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        try {
            return dateProperty.getDates().stream()
                    .map(Temporal.class::cast)
                    .toList();
        } catch (RuntimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private void validateRdatesDoNotPrecedeStart(RecurrenceSchedule schedule, List<ContentLine> lines) {
        boolean hasEarlierRdate = lines.stream()
                .filter(line -> line.type() == LineType.RDATE)
                .flatMap(line -> dateValues(line).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .anyMatch(startAt -> startAt.isBefore(schedule.startAt()));
        if (hasEarlierRdate) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private void validateFirstOccurrence(RecurrenceSchedule schedule, RecurrenceDefinition definition) {
        boolean includesFirst = expand(
                schedule,
                definition,
                schedule.startAt(),
                schedule.startAt().plusNanos(1)
        ).stream().anyMatch(occurrence -> occurrence.originStartAt().equals(schedule.startAt()));
        if (!includesFirst) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private LocalDateTime toMasterLocalDateTime(RecurrenceSchedule schedule, Temporal temporal) {
        if (temporal instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (temporal instanceof Instant
                || temporal instanceof OffsetDateTime
                || temporal instanceof ZonedDateTime) {
            return TemporalAdapter.toLocalTime(temporal, schedule.zoneId()).toLocalDateTime();
        }
        return LocalDateTime.from(temporal);
    }

    private String normalize(Property property) {
        String normalizedParameters = property.getParameterList().getAll().stream()
                .sorted(Comparator.comparing(Parameter::getName))
                .map(parameter -> ";" + parameter)
                .collect(Collectors.joining());
        return property.getName() + normalizedParameters + ":" + property.getValue();
    }

    private boolean overlaps(Instant startAt, Instant endAt, Instant from, Instant to) {
        return startAt.isBefore(to) && endAt.isAfter(from);
    }

    private enum LineType {
        RRULE,
        RDATE,
        EXDATE,
        EXRULE
    }

    private record ContentLine(
            LineType type,
            Property property,
            String normalized
    ) {
    }

    private record RecurrenceDefinition(List<ContentLine> contentLines) {

        private List<ContentLine> lines(LineType type) {
            return contentLines.stream().filter(line -> line.type() == type).toList();
        }

        private List<String> normalizedLines() {
            return contentLines.stream().map(ContentLine::normalized).toList();
        }
    }

    private record ExpansionWindow(Temporal localFrom, Temporal localTo) {
    }
}
