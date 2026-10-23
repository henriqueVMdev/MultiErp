package io.github.henriquevmdev.erpconnector.core.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Buyer of an order. Every field is optional because ERPs omit them. Location is city and state (UF) only, never the
 * full address. {@code toString()} masks personal data.
 */
public record Customer(
        Optional<TaxDocument> document,
        Optional<String> name,
        Optional<String> email,
        Optional<String> phone,
        Optional<String> city,
        Optional<String> state,
        Optional<LocalDate> birthDate) {

    public Customer {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(birthDate, "birthDate");
    }

    @Override
    public String toString() {
        return "Customer[document=" + document
                + ", name=" + name
                + ", email=" + email.map(Masking::email)
                + ", phone=" + phone.map(Masking::keepLastTwo)
                + ", city=" + city
                + ", state=" + state
                + ", birthDate=" + birthDate.map(d -> "****-**-**")
                + "]";
    }
}
