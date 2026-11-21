
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.validation.InputPatterns;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Administrator: issue a bill to a client. */
public class BillRequest {

    @NotBlank(message = "Bill reference is required")
    @Size(max = 40, message = "Bill reference cannot exceed 40 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]{2,39}$",
            message = "Bill reference must be 3-40 characters: letters, digits, '.', '_', '/', '-'")
    private String reference;

    @NotBlank(message = "Provider is required")
    @Size(max = 100, message = "Provider cannot exceed 100 characters")
    @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT, message = "Provider contains invalid characters")
    private String provider;

    @NotNull(message = "Category is required")
    private BillCategory category;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 13, fraction = 2,
            message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @NotNull(message = "Client is required")
    @Positive(message = "Client is invalid")
    private Integer idClient;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public BillCategory getCategory() {
        return category;
    }

    public void setCategory(BillCategory category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getIdClient() {
        return idClient;
    }

    public void setIdClient(Integer idClient) {
        this.idClient = idClient;
    }
}
