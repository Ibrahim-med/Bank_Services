package com.bankservices.bankservices_api.security;

import com.bankservices.bankservices_api.dto.request.ActivateAccountRequest;
import com.bankservices.bankservices_api.dto.request.ClientRegistrationRequest;
import com.bankservices.bankservices_api.dto.request.CompteRequest;
import com.bankservices.bankservices_api.dto.request.LoginRequest;
import com.bankservices.bankservices_api.dto.request.TransactionRequest;
import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.enums.TypeTransaction;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation rules: legitimate data with special characters is accepted;
 * only data that is invalid for the business is refused.
 */
class InputValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ClientRegistrationRequest client(String prenom, String nom, String email, String phone, String address) {
        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setPrenom(prenom);
        request.setNom(nom);
        request.setEmail(email);
        request.setTelephone(phone);
        request.setAdresse(address);
        return request;
    }

    private Set<String> invalidFields(Object bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"O'Connor", "Jean-Luc", "Zoë", "Nguyễn Văn", "D'Artagnan", "Ørsted", "Åsa-Märta", "李", "Mc Donald (Jr.)", "\"Jo\""})
    void legitimateNames_areAccepted(String name) {
        assertThat(invalidFields(client(name, name, "user@example.com", null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"o'connor+bank@example.com", "first.last@sub.example.co.uk", "jean-luc_99@example.fr"})
    void legitimateEmails_areAccepted(String email) {
        assertThat(invalidFields(client("A", "B", email, null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0550123456", "+213 (0)5 50-12-34", "+1.202.555.0101", "05/50/12/34/56"})
    void legitimatePhoneNumbers_areAccepted(String phone) {
        assertThat(invalidFields(client("A", "B", "a@example.com", phone, null))).isEmpty();
    }

    @Test
    void multiLineAddress_isAccepted() {
        assertThat(invalidFields(client("A", "B", "a@example.com", null, "12 rue de l'Église\r\nApt. 3\tBâtiment B"))).isEmpty();
    }

    @Test
    void sqlLookingText_isOrdinaryDataForFreeTextFields() {
        // Parameter binding makes this harmless; refusing it would only block real users.
        assertThat(invalidFields(client("Robert'); DROP TABLE client; --", "O'Brien", "a@example.com", null, "1' OR '1'='1"))).isEmpty();
    }

    @Test
    void controlCharactersInNames_areRefused() {
        assertThat(invalidFields(client("Alice\u0000", "Bob\nSmith", "a@example.com", null, null)))
                .contains("prenom", "nom");
    }

    @Test
    void lettersOrScriptInPhone_areRefused() {
        assertThat(invalidFields(client("A", "B", "a@example.com", "0550' OR 1=1", null))).contains("telephone");
    }

    @Test
    void invalidEmail_isRefused() {
        assertThat(invalidFields(client("A", "B", "not-an-email' OR '1'='1", null, null))).contains("email");
    }

    @Test
    void accountNumbers_followTheBankFormat() {
        CompteRequest ok = new CompteRequest();
        ok.setNumero("ACC1001");
        ok.setTypeCompte(TypeCompte.COURANT);
        ok.setIdClient(1);
        assertThat(invalidFields(ok)).isEmpty();

        ok.setNumero("ACC-2026-0042");
        assertThat(invalidFields(ok)).isEmpty();

        ok.setNumero("ACC1001' OR '1'='1");
        assertThat(invalidFields(ok)).contains("numero");
    }

    @Test
    void amountsBeyondTheDatabasePrecision_areRefused() {
        TransactionRequest request = new TransactionRequest();
        request.setTypeTransaction(TypeTransaction.DEPOT);
        request.setIdCompteDestination(1);

        request.setMontant(new BigDecimal("9999999999999.99"));
        assertThat(invalidFields(request)).isEmpty();

        request.setMontant(new BigDecimal("10.005"));
        assertThat(invalidFields(request)).contains("montant");

        request.setMontant(new BigDecimal("99999999999999.00"));
        assertThat(invalidFields(request)).contains("montant");

        request.setMontant(new BigDecimal("10.00"));
        request.setIdCompteDestination(-1);
        assertThat(invalidFields(request)).contains("idCompteDestination");
    }

    @Test
    void passwords_areLimitedTo72Bytes_notCharacters() {
        ActivateAccountRequest request = new ActivateAccountRequest();
        request.setToken("token");

        request.setPassword("a".repeat(72));
        request.setConfirmPassword(request.getPassword());
        assertThat(invalidFields(request)).isEmpty();

        request.setPassword("a".repeat(73));
        request.setConfirmPassword(request.getPassword());
        assertThat(invalidFields(request)).contains("password");

        request.setPassword("é".repeat(37));          // 37 characters but 74 bytes in UTF-8
        request.setConfirmPassword(request.getPassword());
        assertThat(invalidFields(request)).contains("password");
    }

    @Test
    void oversizedLoginInput_isRefused() {
        LoginRequest request = new LoginRequest();
        request.setUsername("u".repeat(51));
        request.setPassword("p".repeat(73));

        assertThat(invalidFields(request)).contains("username", "password");
    }
}
