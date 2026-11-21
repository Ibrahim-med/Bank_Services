
package com.bankservices.bankservices_api.entity;

import com.bankservices.bankservices_api.enums.BillPaymentStatus;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Successful payment of a bill. The UNIQUE constraint on id_bill
 * guarantees that a bill can never be paid twice.
 */
@Entity
@Table(name = "bill_payment")
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bill_payment")
    private Integer idBillPayment;

    @Column(name = "reference", nullable = false, unique = true, length = 40)
    private String reference;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bill", nullable = false, unique = true)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compte", nullable = false)
    private CompteBancaire compte;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_transaction", nullable = false, unique = true)
    private TransactionBancaire transaction;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillPaymentStatus status = BillPaymentStatus.COMPLETED;

    public BillPayment() {
    }

    public Integer getIdBillPayment() {
        return idBillPayment;
    }

    public void setIdBillPayment(Integer idBillPayment) {
        this.idBillPayment = idBillPayment;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Bill getBill() {
        return bill;
    }

    public void setBill(Bill bill) {
        this.bill = bill;
    }

    public CompteBancaire getCompte() {
        return compte;
    }

    public void setCompte(CompteBancaire compte) {
        this.compte = compte;
    }

    public TransactionBancaire getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionBancaire transaction) {
        this.transaction = transaction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public BillPaymentStatus getStatus() {
        return status;
    }

    public void setStatus(BillPaymentStatus status) {
        this.status = status;
    }
}
