package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.io.IOException;
import java.io.StringReader;
import java.time.DateTimeException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
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

    private static final int MAX_OCCURRENCES_PER_EXPANSION = 10_000;
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
    public List<String> validate(RecurrenceSchedule schedule, List<String> recurrenceRules) {
        return parseDefinition(schedule, recurrenceRules).normalizedRules();
    }

    @Override
    public List<RecurrenceOccurrence> expand(
            RecurrenceSchedule schedule,
            List<String> recurrenceRules,
            Instant from,
            Instant to
    ) {
        if (!from.isBefore(to)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        RecurrenceDefinition definition = parseDefinition(schedule, recurrenceRules);
        ExpansionWindow window = expansionWindow(schedule, from, to);
        if (isEmpty(window)) {
            return List.of();
        }

        CandidateLimit candidateLimit = new CandidateLimit();
        Set<Instant> includedStarts = new LinkedHashSet<>();
        addCandidate(includedStarts, schedule.firstOccurrenceStartAt(), candidateLimit);
        addRuleCandidates(schedule, definition.inclusionRules(), window, includedStarts, candidateLimit);
        addDateCandidates(schedule, definition.inclusionDates(), includedStarts, candidateLimit);

        Set<Instant> excludedStarts = new HashSet<>();
        addRuleCandidates(schedule, definition.exclusionRules(), window, excludedStarts, candidateLimit);
        addDateCandidates(schedule, definition.exclusionDates(), excludedStarts, candidateLimit);

        includedStarts.removeAll(excludedStarts);
        return includedStarts.stream()
                .map(startAt -> toOccurrence(schedule, startAt))
                .flatMap(Optional::stream)
                .filter(occurrence -> overlaps(occurrence.startAt(), occurrence.endAt(), from, to))
                .sorted(Comparator.comparing(RecurrenceOccurrence::originStartAt))
                .toList();
    }

    @Override
    public boolean containsOrigin(
            RecurrenceSchedule schedule,
            List<String> recurrenceRules,
            Instant originStartAt
    ) {
        if (originStartAt == null || originStartAt.isBefore(schedule.firstOccurrenceStartAt())) {
            return false;
        }
        try {
            return expand(schedule, recurrenceRules, originStartAt, originStartAt.plusNanos(1)).stream()
                    .anyMatch(occurrence -> occurrence.originStartAt().equals(originStartAt));
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private void addRuleCandidates(
            RecurrenceSchedule schedule,
            List<ContentLine> rules,
            ExpansionWindow window,
            Set<Instant> target,
            CandidateLimit candidateLimit
    ) {
        for (ContentLine rule : rules) {
            ExpansionWindow generationWindow = boundedByUntil(schedule, rule, window);
            if (isEmpty(generationWindow)) {
                continue;
            }
            List<Temporal> candidates = recurrenceRuleForGeneration(schedule, rule)
                    .getDatesAsStream(
                            scheduleSeed(schedule),
                            generationWindow.localFrom(),
                            generationWindow.localTo(),
                            MAX_OCCURRENCES_PER_EXPANSION + 1
                    )
                    .toList();
            if (candidates.size() > MAX_OCCURRENCES_PER_EXPANSION) {
                throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED);
            }
            candidates.stream()
                    .filter(candidate -> startsOnOrBeforeUntil(schedule, rule, candidate))
                    .map(candidate -> occurrenceStart(schedule, candidate))
                    .flatMap(Optional::stream)
                    .forEach(candidate -> addCandidate(target, candidate, candidateLimit));
        }
    }

    private void addDateCandidates(
            RecurrenceSchedule schedule,
            List<ContentLine> dateLines,
            Set<Instant> target,
            CandidateLimit candidateLimit
    ) {
        dateLines.stream()
                .flatMap(line -> dateValues(line).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .forEach(candidate -> addCandidate(target, candidate, candidateLimit));
    }

    private void addCandidate(Set<Instant> target, Instant candidate, CandidateLimit candidateLimit) {
        candidateLimit.track(candidate);
        target.add(candidate);
    }

    private Optional<RecurrenceOccurrence> toOccurrence(RecurrenceSchedule schedule, Instant startAt) {
        if (startAt.equals(schedule.firstOccurrenceStartAt())) {
            return Optional.of(new RecurrenceOccurrence(
                    startAt,
                    startAt,
                    schedule.firstOccurrenceEndAt()
            ));
        }
        Optional<Instant> endAt = schedule.allDay()
                ? Optional.of(startAt.plusSeconds(schedule.allDayDurationDays() * 86_400))
                : resolveTimedOccurrenceEnd(schedule, startAt);
        return endAt
                .filter(startAt::isBefore)
                .map(end -> new RecurrenceOccurrence(startAt, startAt, end));
    }

    private Optional<Instant> resolveTimedOccurrenceEnd(RecurrenceSchedule schedule, Instant startAt) {
        ZoneId zoneId = schedule.zoneId();
        LocalDateTime localEnd = startAt.atZone(zoneId)
                .toLocalDateTime()
                .plus(schedule.timedWallClockDuration());
        List<ZoneOffset> offsets = zoneId.getRules().getValidOffsets(localEnd);
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        ZoneOffset startOffset = zoneId.getRules().getOffset(startAt);
        ZoneOffset endOffset = offsets.stream()
                .filter(startOffset::equals)
                .findFirst()
                .orElse(offsets.getFirst());
        return Optional.of(localEnd.toInstant(endOffset));
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

    private Temporal scheduleSeed(RecurrenceSchedule schedule) {
        return schedule.allDay()
                ? schedule.firstOccurrenceDate()
                : schedule.firstOccurrenceLocalStart();
    }

    private ExpansionWindow expansionWindow(RecurrenceSchedule schedule, Instant from, Instant to) {
        if (schedule.allDay()) {
            LocalDate requestedFrom = from.atOffset(ZoneOffset.UTC)
                    .toLocalDate()
                    .minusDays(schedule.allDayDurationDays() + 1);
            LocalDate requestedTo = to.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(2);
            return new ExpansionWindow(
                    laterDate(requestedFrom, schedule.firstOccurrenceDate()),
                    requestedTo
            );
        }
        LocalDateTime requestedFrom = from.atZone(schedule.zoneId())
                .toLocalDateTime()
                .minus(schedule.timedWallClockDuration())
                .minusDays(1);
        LocalDateTime requestedTo = to.atZone(schedule.zoneId()).toLocalDateTime().plusDays(1);
        return new ExpansionWindow(
                laterDateTime(requestedFrom, schedule.firstOccurrenceLocalStart()),
                requestedTo
        );
    }

    private LocalDate laterDate(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDateTime laterDateTime(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDateTime earlierDateTime(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private boolean isEmpty(ExpansionWindow window) {
        if (window.localFrom() instanceof LocalDate from && window.localTo() instanceof LocalDate to) {
            return !from.isBefore(to);
        }
        return !LocalDateTime.from(window.localFrom()).isBefore(LocalDateTime.from(window.localTo()));
    }

    private ExpansionWindow boundedByUntil(
            RecurrenceSchedule schedule,
            ContentLine line,
            ExpansionWindow window
    ) {
        Temporal until = recurrenceRule(line).getUntil();
        if (schedule.allDay() || until == null) {
            return window;
        }
        LocalDateTime localUntil = Instant.from(until).atZone(schedule.zoneId()).toLocalDateTime();
        return new ExpansionWindow(
                window.localFrom(),
                earlierDateTime(LocalDateTime.from(window.localTo()), localUntil.plusNanos(1))
        );
    }

    private Recur<Temporal> recurrenceRuleForGeneration(
            RecurrenceSchedule schedule,
            ContentLine line
    ) {
        Recur<Temporal> rule = recurrenceRule(line);
        if (schedule.allDay() || rule.getUntil() == null) {
            return rule;
        }
        String valueWithoutUntil = Stream.of(rule.toString().split(";"))
                .filter(part -> !part.startsWith("UNTIL="))
                .collect(Collectors.joining(";"));
        return new Recur<>(valueWithoutUntil);
    }

    private boolean startsOnOrBeforeUntil(
            RecurrenceSchedule schedule,
            ContentLine line,
            Temporal candidate
    ) {
        Temporal until = recurrenceRule(line).getUntil();
        if (until == null) {
            return true;
        }
        if (schedule.allDay()) {
            return !LocalDate.from(candidate).isAfter(LocalDate.from(until));
        }
        return occurrenceStart(schedule, candidate)
                .map(start -> !start.isAfter(Instant.from(until)))
                .orElse(false);
    }

    private RecurrenceDefinition parseDefinition(
            RecurrenceSchedule schedule,
            List<String> recurrenceRules
    ) {
        if (recurrenceRules == null) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        List<ContentLine> rules = recurrenceRules.stream()
                .map(this::parseContentLine)
                .map(line -> validateLine(schedule, line))
                .distinct()
                .sorted(Comparator
                        .comparingInt(this::propertyOrder)
                        .thenComparing(ContentLine::normalized))
                .toList();
        List<ContentLine> inclusionRules = propertiesOfType(rules, RRule.class);
        List<ContentLine> inclusionDates = propertiesOfType(rules, RDate.class);
        List<ContentLine> exclusionRules = propertiesOfType(rules, ExRule.class);
        List<ContentLine> exclusionDates = propertiesOfType(rules, ExDate.class);
        if (inclusionRules.isEmpty() && inclusionDates.isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        RecurrenceDefinition definition = new RecurrenceDefinition(
                inclusionRules,
                inclusionDates,
                exclusionRules,
                exclusionDates,
                rules.stream().map(ContentLine::normalized).toList()
        );
        validateFirstOccurrence(schedule, definition);
        return definition;
    }

    private List<ContentLine> propertiesOfType(List<ContentLine> lines, Class<?> propertyType) {
        return lines.stream()
                .filter(line -> propertyType.isInstance(line.property()))
                .toList();
    }

    private int propertyOrder(ContentLine line) {
        if (line.property() instanceof RRule<?>) {
            return 0;
        }
        if (line.property() instanceof RDate<?>) {
            return 1;
        }
        if (line.property() instanceof ExDate<?>) {
            return 2;
        }
        return 3;
    }

    private void validateFirstOccurrence(
            RecurrenceSchedule schedule,
            RecurrenceDefinition definition
    ) {
        boolean hasEarlierInclusionDate = definition.inclusionDates().stream()
                .flatMap(line -> dateValues(line).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .anyMatch(candidate -> candidate.isBefore(schedule.firstOccurrenceStartAt()));
        boolean excludedByDate = definition.exclusionDates().stream()
                .flatMap(line -> dateValues(line).stream())
                .map(candidate -> occurrenceStart(schedule, candidate))
                .flatMap(Optional::stream)
                .anyMatch(schedule.firstOccurrenceStartAt()::equals);
        boolean excludedByRule = definition.exclusionRules().stream()
                .anyMatch(line -> ruleContainsFirstOccurrence(schedule, line));
        if (hasEarlierInclusionDate || excludedByDate || excludedByRule) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private boolean ruleContainsFirstOccurrence(RecurrenceSchedule schedule, ContentLine line) {
        Temporal seed = scheduleSeed(schedule);
        Temporal to = schedule.allDay()
                ? LocalDate.from(seed).plusDays(1)
                : LocalDateTime.from(seed).plusNanos(1);
        try {
            return recurrenceRuleForGeneration(schedule, line)
                    .getDatesAsStream(seed, seed, to, 2)
                    .filter(candidate -> startsOnOrBeforeUntil(schedule, line, candidate))
                    .map(candidate -> occurrenceStart(schedule, candidate))
                    .flatMap(Optional::stream)
                    .anyMatch(schedule.firstOccurrenceStartAt()::equals);
        } catch (RuntimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
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
        if (property instanceof RRule<?>
                || property instanceof RDate<?>
                || property instanceof ExDate<?>
                || property instanceof ExRule<?>) {
            return property;
        }
        throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
    }

    private void validateRulePartCount(String rawLine, Property property) {
        if (!(property instanceof RRule<?>) && !(property instanceof ExRule<?>)) {
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
        if (line.property() instanceof RRule<?> || line.property() instanceof ExRule<?>) {
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
        if (line.property() instanceof RRule<?> || line.property() instanceof ExRule<?>) {
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

    private record ContentLine(Property property, String normalized) {
    }

    private record RecurrenceDefinition(
            List<ContentLine> inclusionRules,
            List<ContentLine> inclusionDates,
            List<ContentLine> exclusionRules,
            List<ContentLine> exclusionDates,
            List<String> normalizedRules
    ) {
    }

    private record ExpansionWindow(Temporal localFrom, Temporal localTo) {
    }

    private static final class CandidateLimit {

        private final Set<Instant> candidates = new HashSet<>();

        private void track(Instant candidate) {
            candidates.add(candidate);
            if (candidates.size() > MAX_OCCURRENCES_PER_EXPANSION) {
                throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED);
            }
        }
    }
}
