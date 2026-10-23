package io.github.henriquevmdev.erpconnector.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxDocumentTest {

    @Test
    void parsesMaskedCpf() {
        TaxDocument doc = TaxDocument.parse("123.456.789-09").orElseThrow();

        assertThat(doc.type()).isEqualTo(TaxDocument.Type.CPF);
        assertThat(doc.value()).isEqualTo("12345678909");
        assertThat(doc.checkDigitsValid()).isTrue();
    }

    @Test
    void parsesAlphanumericCnpjAndUpperCasesIt() {
        TaxDocument doc = TaxDocument.parse("12.abc.345/01de-35").orElseThrow();

        assertThat(doc.type()).isEqualTo(TaxDocument.Type.CNPJ);
        assertThat(doc.value()).isEqualTo("12ABC34501DE35");
        assertThat(doc.checkDigitsValid()).isTrue();
    }

    @Test
    void keepsInvalidDocumentAndReportsIt() {
        TaxDocument doc = TaxDocument.parse("12345678900").orElseThrow();

        assertThat(doc.type()).isEqualTo(TaxDocument.Type.CPF);
        assertThat(doc.checkDigitsValid()).isFalse();
    }

    @Test
    void anythingElseIsOtherAndNeverValid() {
        TaxDocument doc = TaxDocument.parse("AB-1234").orElseThrow();

        assertThat(doc.type()).isEqualTo(TaxDocument.Type.OTHER);
        assertThat(doc.checkDigitsValid()).isFalse();
    }

    @Test
    void blankOrNullMeansNoDocument() {
        assertThat(TaxDocument.parse(null)).isEmpty();
        assertThat(TaxDocument.parse("  ")).isEmpty();
    }

    @Test
    void rejectsTypeThatDoesNotMatchTheValue() {
        assertThatThrownBy(() -> new TaxDocument(TaxDocument.Type.CPF, "12345678000195"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("12345678000195");
    }

    @Test
    void toStringMasksTheValue() {
        String text = TaxDocument.parse("12345678909").orElseThrow().toString();

        assertThat(text).doesNotContain("123456789").endsWith("*********09]");
    }
}
