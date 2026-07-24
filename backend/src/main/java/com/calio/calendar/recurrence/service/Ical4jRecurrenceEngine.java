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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TemporalAdapter;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.ExDate;
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

    @Override
    public List<String> validate(RecurrenceSchedule schedule, List<String> recurrenceLines) {
        RecurrenceDefinition definition = parseDefinition(schedule, recurrenceLines);
        recurrenceAnchor(schedule, definition);
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
        if (originStartAt == null || originStartAt.isBefore(applicationStartAt(schedule))) {
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
        RecurrenceAnchor anchor = recurrenceAnchor(schedule, definition);
        ExpansionWindow window = expansionWindow(schedule, from, to);
        Set<Instant> excludedStarts = excludedStarts(schedule, definition);
        VEvent event = recurrenceEvent(definition, anchor);

        return event.calculateRecurrenceSet(new Period<>(window.localFrom(), window.localTo())).stream()
                .map(period -> toOccurrence(schedule, period))
                .flatMap(Optional::stream)
                .filter(occurrence -> startsWithinApplicationPeriod(schedule, occurrence.originStartAt()))
                .filter(occurrence -> !excludedStarts.contains(occurrence.originStartAt()))
                .filter(occurrence -> overlaps(occurrence.startAt(), occurrence.endAt(), from, to))
                .sorted(Comparator.comparing(RecurrenceOccurrence::originStartAt))
                .toList();
    }

    private Set<Instant> excludedStarts(
            RecurrenceSchedule schedule,
            RecurrenceDefinition definition
    ) {
        return definition.exclusionDates().stream()
                .flatMap(line -> dateValues(line).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    private VEvent recurrenceEvent(RecurrenceDefinition definition, RecurrenceAnchor anchor) {
        VEvent event = new VEvent(anchor.start(), anchor.end(), "Recurrence");
        event.add(definition.rule().property());
        return event;
    }

    private boolean startsWithinApplicationPeriod(RecurrenceSchedule schedule, Instant occurrenceStartAt) {
        if (schedule.allDay()) {
            return true;
        }
        LocalDate occurrenceDate = occurrenceStartAt.atZone(schedule.zoneId()).toLocalDate();
        return !occurrenceDate.isBefore(schedule.startDate())
                && !occurrenceDate.isAfter(schedule.endDate());
    }

    private Optional<RecurrenceOccurrence> toOccurrence(RecurrenceSchedule schedule, Period<?> period) {
        Optional<Instant> startAt = periodStart(schedule, period);
        if (startAt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> endAt = periodEnd(schedule, period, startAt.get());
        if (endAt.isEmpty() || !startAt.get().isBefore(endAt.get())) {
            return Optional.empty();
        }
        return Optional.of(new RecurrenceOccurrence(startAt.get(), startAt.get(), endAt.get()));
    }

    private Optional<Instant> periodStart(RecurrenceSchedule schedule, Period<?> period) {
        return occurrenceStart(schedule, period.getStart());
    }

    private Optional<Instant> periodEnd(RecurrenceSchedule schedule, Period<?> period, Instant startAt) {
        Temporal end = period.getEnd();
        if (schedule.allDay()) {
            return Optional.of(LocalDate.from(end).atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        Optional<Instant> explicitEndAt = explicitInstant(end);
        if (explicitEndAt.isPresent()) {
            return explicitEndAt;
        }
        return resolveOccurrenceEnd(
                toMasterLocalDateTime(schedule, end),
                schedule.zoneId(),
                startAt,
                explicitInstant(period.getStart()).isPresent()
        );
    }

    private Optional<Instant> occurrenceStart(RecurrenceSchedule schedule, Temporal temporal) {
        if (schedule.allDay()) {
            return Optional.of(LocalDate.from(temporal).atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        Optional<Instant> explicitStartAt = explicitInstant(temporal);
        if (explicitStartAt.isPresent()) {
            return explicitStartAt;
        }
        return resolveGeneratedTime(toMasterLocalDateTime(schedule, temporal), schedule.zoneId());
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
                : schedule.startDate().atTime(schedule.startTime());
    }

    private Temporal occurrenceEnd(RecurrenceSchedule schedule, Temporal occurrenceStart) {
        if (schedule.allDay()) {
            long durationDays = schedule.endDate().toEpochDay() - schedule.startDate().toEpochDay();
            return LocalDate.from(occurrenceStart).plusDays(durationDays);
        }
        return toMasterLocalDateTime(schedule, occurrenceStart).plus(timedOccurrenceDuration(schedule));
    }

    private ExpansionWindow expansionWindow(RecurrenceSchedule schedule, Instant from, Instant to) {
        if (schedule.allDay()) {
            long durationDays = schedule.endDate().toEpochDay() - schedule.startDate().toEpochDay();
            LocalDate localFrom = from.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(durationDays + 1);
            LocalDate localTo = to.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(2);
            return new ExpansionWindow(localFrom, localTo);
        }

        LocalDateTime localFrom = from.atZone(schedule.zoneId()).toLocalDateTime()
                .minus(timedOccurrenceDuration(schedule))
                .minusDays(1);
        LocalDateTime localTo = to.atZone(schedule.zoneId()).toLocalDateTime().plusDays(1);
        return new ExpansionWindow(localFrom, localTo);
    }

    private Duration timedOccurrenceDuration(RecurrenceSchedule schedule) {
        LocalDate occurrenceEndDate = schedule.endTime().isAfter(schedule.startTime())
                ? schedule.startDate()
                : schedule.startDate().plusDays(1);
        return Duration.between(
                schedule.startDate().atTime(schedule.startTime()),
                occurrenceEndDate.atTime(schedule.endTime())
        );
    }

    private RecurrenceAnchor recurrenceAnchor(
            RecurrenceSchedule schedule,
            RecurrenceDefinition definition
    ) {
        ExpansionWindow applicationWindow = applicationWindow(schedule);
        Temporal seed = scheduleSeed(schedule);
        Temporal firstStart = firstOccurrence(schedule, definition.rule(), seed, applicationWindow);
        return new RecurrenceAnchor(firstStart, occurrenceEnd(schedule, firstStart));
    }

    private Temporal firstOccurrence(
            RecurrenceSchedule schedule,
            ContentLine line,
            Temporal seed,
            ExpansionWindow applicationWindow
    ) {
        try {
            return recurrenceRule(line)
                    .getDatesAsStream(
                            seed,
                            applicationWindow.localFrom(),
                            applicationWindow.localTo(),
                            -1
                    )
                    .map(candidate -> validOccurrenceStart(schedule, candidate))
                    .flatMap(Optional::stream)
                    .map(candidate -> masterTemporal(schedule, candidate))
                    .findFirst()
                    .orElseThrow(() -> new CalioException(ErrorCode.INVALID_RECURRENCE_RULE));
        } catch (CalioException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private Optional<Temporal> validOccurrenceStart(
            RecurrenceSchedule schedule,
            Temporal temporal
    ) {
        return occurrenceStart(schedule, temporal)
                .filter(startAt -> startsWithinApplicationPeriod(schedule, startAt))
                .map(startAt -> temporal);
    }

    private Temporal masterTemporal(RecurrenceSchedule schedule, Temporal temporal) {
        return schedule.allDay()
                ? LocalDate.from(temporal)
                : toMasterLocalDateTime(schedule, temporal);
    }

    private ExpansionWindow applicationWindow(RecurrenceSchedule schedule) {
        if (schedule.allDay()) {
            return new ExpansionWindow(schedule.startDate(), schedule.endDate().plusDays(1));
        }
        return new ExpansionWindow(
                schedule.startDate().atStartOfDay(),
                schedule.endDate().plusDays(1).atStartOfDay()
        );
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
                .toList();
        List<ContentLine> rules = lines.stream()
                .filter(line -> line.property() instanceof RRule<?>)
                .toList();
        if (rules.size() != 1) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        List<ContentLine> exclusionDates = lines.stream()
                .filter(line -> line.property() instanceof ExDate<?>)
                .distinct()
                .sorted(Comparator.comparing(ContentLine::normalized))
                .toList();
        return new RecurrenceDefinition(rules.getFirst(), exclusionDates);
    }

    private ContentLine parseContentLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        try {
            Calendar calendar = new CalendarBuilder().build(
                    new StringReader(CONTENT_LINE_CALENDAR.formatted(rawLine.trim()))
            );
            Property property = recurrenceProperty(calendar);
            validateRulePartCount(rawLine, property);
            return new ContentLine(property, normalize(property));
        } catch (CalioException exception) {
            throw exception;
        } catch (IOException | ParserException | RuntimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private Property recurrenceProperty(Calendar calendar) {
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
        Property property = properties.getFirst();
        if (property instanceof RRule<?> || property instanceof ExDate<?>) {
            return property;
        }
        throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
    }

    private void validateRulePartCount(String rawLine, Property property) {
        if (!(property instanceof RRule<?>)) {
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
        if (line.property() instanceof RRule<?>) {
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
        if (line.property() instanceof RRule<?>) {
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

    private Instant applicationStartAt(RecurrenceSchedule schedule) {
        if (schedule.allDay()) {
            return schedule.startDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return resolveGeneratedTime(schedule.startDate().atTime(schedule.startTime()), schedule.zoneId())
                .orElseThrow(() -> new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE));
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

    private record ContentLine(
            Property property,
            String normalized
    ) {
    }

    private record RecurrenceDefinition(
            ContentLine rule,
            List<ContentLine> exclusionDates
    ) {

        private List<String> normalizedLines() {
            return Stream.concat(Stream.of(rule), exclusionDates.stream())
                    .map(ContentLine::normalized)
                    .toList();
        }
    }

    private record ExpansionWindow(Temporal localFrom, Temporal localTo) {
    }

    private record RecurrenceAnchor(Temporal start, Temporal end) {
    }
}
