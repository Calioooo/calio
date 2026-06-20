package com.calio.calendar.controller.dto;

public sealed interface NullableUpdateValue<T>
        permits NullableUpdateValue.Omitted, NullableUpdateValue.Present {

    static <T> NullableUpdateValue<T> omitted() {
        return new Omitted<>();
    }

    static <T> NullableUpdateValue<T> present(T value) {
        return new Present<>(value);
    }

    T applyTo(T currentValue);

    record Omitted<T>() implements NullableUpdateValue<T> {

        @Override
        public T applyTo(T currentValue) {
            return currentValue;
        }
    }

    record Present<T>(T value) implements NullableUpdateValue<T> {

        @Override
        public T applyTo(T currentValue) {
            return value;
        }
    }
}
