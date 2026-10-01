package com.bankflow.ledger.domain;

/**
 * Direction of a ledger entry from the account's perspective.
 *
 * <ul>
 *   <li>DEBIT  — money leaving the account (sender). Amount is negative.</li>
 *   <li>CREDIT — money entering the account (receiver). Amount is positive.</li>
 * </ul>
 */
public enum EntryType {
    DEBIT,
    CREDIT
}
