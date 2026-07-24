package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fortuna.ical4j.model.Recur;
import org.springframework.stereotype.Component;

@Component
public class Ical4jRecurrenceEngine implements Rfc5545RecurrenceEngine {

    private static final DateTimeFormatter BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final Set<String> DATE_PROPERTIES = Set.of("RDATE", "EXDATE");
    private static final Set<String> RULE_PROPERTIES = Set.of("RRULE", "EXRULE");
    private static final Set<String> RULE_PARTS = Set.of(
            "FREQ",
            "UNTIL",
            "COUNT",
            "INTERVAL",
            "BYSECOND",
            "BYMINUTE",
            "BYHOUR",
            "BYDAY",
            "BYMONTHDAY",
            "BYYEARDAY",
            "BYWEEKNO",
            "BYMONTH",
            "BYSETPOS",
            "WKST"
    );
    private static final Map<LineType, Integer> LINE_ORDER = Map.of(
            LineType.RRULE, 0,
            LineType.RDATE, 1,
            LineType.EXDATE, 2,
            LineType.EXRULE, 3
    );

    private final Ical4jRecurAdapter recurAdapter = new Ical4jRecurAdapter();

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

        addOccurrence(schedule, scheduleSeed(schedule), excludedStarts, occurrences);
        definition.lines(LineType.RRULE).stream()
                .flatMap(line -> expandRule(schedule, line, window).stream())
                .forEach(candidate -> addOccurrence(schedule, candidate, excludedStarts, occurrences));
        definition.lines(LineType.RDATE).stream()
                .flatMap(line -> parseDateValues(schedule, line).stream())
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
                .flatMap(line -> parseDateValues(schedule, line).stream())
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
        Optional<Instant> startAt = candidate instanceof Instant instant
                ? Optional.of(instant)
                : resolveGeneratedTime(startDateTime, schedule.zoneId());
        if (startAt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> endAt = resolveOccurrenceEnd(
                endDateTime,
                schedule.zoneId(),
                startAt.get(),
                candidate
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
        if (candidate instanceof Instant instant) {
            return Optional.of(instant);
        }
        return resolveGeneratedTime(toMasterLocalDateTime(schedule, candidate), schedule.zoneId());
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
            Temporal candidate
    ) {
        List<ZoneOffset> offsets = zoneId.getRules().getValidOffsets(endDateTime);
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        if (!(candidate instanceof Instant)) {
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
        return recurAdapter.expand(
                line.value(),
                scheduleSeed(schedule),
                window.localFrom(),
                window.localTo()
        );
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
                .map(this::unfold)
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
        validateRuleSyntax(schedule, lines);
        validateRdatesDoNotPrecedeStart(schedule, lines);
        return new RecurrenceDefinition(lines);
    }

    private String unfold(String rawLine) {
        if (rawLine == null) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        String unfolded = rawLine.replaceAll("\\r?\\n[ \\t]", "").trim();
        if (unfolded.isEmpty() || unfolded.contains("\r") || unfolded.contains("\n")) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        return unfolded;
    }

    private ContentLine parseContentLine(String line) {
        int valueSeparator = findUnquoted(line, ':');
        if (valueSeparator <= 0 || valueSeparator == line.length() - 1) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        List<String> propertyAndParameters = splitUnquoted(line.substring(0, valueSeparator), ';');
        String property = propertyAndParameters.getFirst().trim().toUpperCase(Locale.ROOT);
        LineType type;
        try {
            type = LineType.valueOf(property);
        } catch (IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
        Map<String, String> parameters = parseParameters(propertyAndParameters.subList(1, propertyAndParameters.size()));
        String value = line.substring(valueSeparator + 1).trim();
        if (value.isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        String normalizedValue = RULE_PROPERTIES.contains(property) ? value.toUpperCase(Locale.ROOT) : value;
        String normalized = normalize(type, parameters, normalizedValue);
        return new ContentLine(type, parameters, normalizedValue, normalized);
    }

    private Map<String, String> parseParameters(List<String> parameterParts) {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String parameterPart : parameterParts) {
            int separator = findUnquoted(parameterPart, '=');
            if (separator <= 0 || separator == parameterPart.length() - 1) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
            String name = parameterPart.substring(0, separator).trim().toUpperCase(Locale.ROOT);
            String value = unquote(parameterPart.substring(separator + 1).trim());
            if (!Set.of("VALUE", "TZID").contains(name) || parameters.putIfAbsent(name, value) != null) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
        }
        return Map.copyOf(parameters);
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private ContentLine validateLine(RecurrenceSchedule schedule, ContentLine line) {
        if (RULE_PROPERTIES.contains(line.type().name()) && !line.parameters().isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        if (DATE_PROPERTIES.contains(line.type().name())) {
            parseDateValues(schedule, line);
        }
        validateRuleValueType(schedule, line);
        return line;
    }

    private void validateRuleValueType(RecurrenceSchedule schedule, ContentLine line) {
        if (!RULE_PROPERTIES.contains(line.type().name())) {
            return;
        }
        Map<String, String> parts = ruleParts(line.value());
        if (!parts.containsKey("FREQ")) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        if (parts.containsKey("COUNT") && parts.containsKey("UNTIL")) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        String until = parts.get("UNTIL");
        if (until != null) {
            boolean validUntil = schedule.allDay()
                    ? until.matches("\\d{8}")
                    : until.matches("\\d{8}T\\d{6}Z");
            if (!validUntil) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
        }
        boolean hasTimedPart = parts.containsKey("BYHOUR")
                || parts.containsKey("BYMINUTE")
                || parts.containsKey("BYSECOND");
        if (schedule.allDay() && hasTimedPart) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
    }

    private Map<String, String> ruleParts(String value) {
        Map<String, String> parts = new HashMap<>();
        for (String part : value.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
            String name = part.substring(0, separator).trim().toUpperCase(Locale.ROOT);
            if (!RULE_PARTS.contains(name)
                    || parts.putIfAbsent(name, part.substring(separator + 1).trim()) != null) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
        }
        return parts;
    }

    private List<Temporal> parseDateValues(RecurrenceSchedule schedule, ContentLine line) {
        return List.of(line.value().split(",")).stream()
                .map(String::trim)
                .map(value -> parseDateValue(schedule, line.parameters(), value))
                .toList();
    }

    private Temporal parseDateValue(
            RecurrenceSchedule schedule,
            Map<String, String> parameters,
            String value
    ) {
        try {
            return schedule.allDay()
                    ? parseAllDayValue(parameters, value)
                    : parseTimedValue(schedule, parameters, value);
        } catch (DateTimeParseException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
        }
    }

    private LocalDate parseAllDayValue(Map<String, String> parameters, String value) {
        String valueType = parameters.get("VALUE");
        if (parameters.containsKey("TZID")
                || valueType == null
                || !valueType.equalsIgnoreCase("DATE")
                || !value.matches("\\d{8}")) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private Temporal parseTimedValue(
            RecurrenceSchedule schedule,
            Map<String, String> parameters,
            String value
    ) {
        String valueType = parameters.get("VALUE");
        if (valueType != null && !valueType.equalsIgnoreCase("DATE-TIME")) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        String parameterTimeZone = parameters.get("TZID");
        if (parameterTimeZone != null && !parameterTimeZone.equals(schedule.timeZone())) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        if (!value.matches("\\d{8}T\\d{6}Z?")) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        LocalDateTime parsed = LocalDateTime.parse(
                value.endsWith("Z") ? value.substring(0, value.length() - 1) : value,
                BASIC_DATE_TIME
        );
        if (value.endsWith("Z")) {
            if (parameterTimeZone != null) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
            }
            return parsed.toInstant(ZoneOffset.UTC);
        }
        if (resolveGeneratedTime(parsed, schedule.zoneId()).isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE);
        }
        return parsed;
    }

    private void validateRuleSyntax(RecurrenceSchedule schedule, List<ContentLine> lines) {
        ExpansionWindow validationWindow = expansionWindow(
                schedule,
                schedule.startAt().minusNanos(1),
                schedule.endAt().plusNanos(1)
        );
        lines.stream()
                .filter(line -> RULE_PROPERTIES.contains(line.type().name()))
                .forEach(line -> expandRule(schedule, line, validationWindow));
    }

    private void validateRdatesDoNotPrecedeStart(RecurrenceSchedule schedule, List<ContentLine> lines) {
        boolean hasEarlierRdate = lines.stream()
                .filter(line -> line.type() == LineType.RDATE)
                .flatMap(line -> parseDateValues(schedule, line).stream())
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
        if (temporal instanceof Instant instant) {
            return instant.atZone(schedule.zoneId()).toLocalDateTime();
        }
        return LocalDateTime.from(temporal);
    }

    private String normalize(LineType type, Map<String, String> parameters, String value) {
        String normalizedParameters = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ";" + entry.getKey() + "=" + normalizeParameterValue(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining());
        return type.name() + normalizedParameters + ":" + value;
    }

    private String normalizeParameterValue(String name, String value) {
        return name.equals("VALUE") ? value.toUpperCase(Locale.ROOT) : value;
    }

    private int findUnquoted(String value, char target) {
        boolean quoted = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == target && !quoted) {
                return index;
            }
        }
        return -1;
    }

    private List<String> splitUnquoted(String value, char separator) {
        List<String> parts = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == separator && !quoted) {
                parts.add(value.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
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
            Map<String, String> parameters,
            String value,
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

    private static final class Ical4jRecurAdapter {

        private List<Temporal> expand(String ruleValue, Temporal seed, Temporal from, Temporal to) {
            try {
                Recur<Temporal> recur = new Recur<>(ruleValue);
                return recur.getDates(seed, from, to);
            } catch (RuntimeException exception) {
                throw new CalioException(ErrorCode.INVALID_RECURRENCE_RULE, exception);
            }
        }
    }
}
