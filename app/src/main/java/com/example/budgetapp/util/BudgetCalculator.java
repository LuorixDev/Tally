package com.example.budgetapp.util;

import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Shared, deterministic budget calculations used by budget and record screens. */
public final class BudgetCalculator {
    public static final int PROGRESS_MAX = 10_000;

    private BudgetCalculator() {}

    /** Keeps sub-percent spending visible without changing the represented ratio. */
    public static int progress(double spent, double total) {
        if (spent <= 0 || total <= 0) return 0;
        return (int) Math.min(PROGRESS_MAX, Math.round(spent * PROGRESS_MAX / total));
    }

    public static double expenseBetween(List<Transaction> transactions, long start, long end) {
        double total = 0;
        if (transactions == null) return total;
        for (Transaction t : transactions) {
            if (t.type == 0 && t.date >= start && t.date < end
                    && !t.excludeFromBudget && t.type != 2
                    && !"资产互转".equals(t.category)) {
                total += t.amount;
            }
        }
        return total;
    }

    public static double dailySurplus(BudgetPlan plan, LocalDate day, List<Transaction> transactions) {
        LocalDate start = toDate(plan.startDate);
        LocalDate end = toDate(plan.endDate);
        if (day.isBefore(start) || day.isAfter(end)) return 0;
        long startMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        double dailyBudget = dailyBudget(plan, day);
        return Math.max(0, dailyBudget - expenseBetween(transactions, startMillis, endMillis));
    }

    /**
     * Returns the exact budget slice for a day in the plan period. Amounts are
     * allocated in cents, so the slices add up to the plan total after normal
     * currency rounding instead of accumulating floating-point division error.
     */
    public static double dailyBudget(BudgetPlan plan, LocalDate day) {
        if (plan == null || day == null) return 0;
        LocalDate start = toDate(plan.startDate);
        LocalDate end = toDate(plan.endDate);
        if (day.isBefore(start) || day.isAfter(end)) return 0;
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        if (days <= 0) return 0;
        long cents = Math.max(0, Math.round(plan.totalAmount * 100));
        long each = cents / days;
        long remainder = cents % days;
        long index = day.toEpochDay() - start.toEpochDay();
        return (each + (index < remainder ? 1 : 0)) / 100.0;
    }

    /** Splits a currency amount into equal cent-accurate parts. */
    public static List<Double> distributeEvenly(double amount, int parts) {
        if (parts <= 0 || amount <= 0) return Collections.emptyList();
        long cents = Math.max(0, Math.round(amount * 100));
        long each = cents / parts;
        long remainder = cents % parts;
        List<Double> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            result.add((each + (i < remainder ? 1 : 0)) / 100.0);
        }
        return result;
    }

    public static double remainingAmount(BudgetPlan plan, List<Transaction> transactions) {
        long endExclusive = Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault())
                .toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Math.max(0, plan.totalAmount - expenseBetween(transactions, plan.startDate, endExclusive));
    }

    /** Distributes the amount in cents, assigning any remainder deterministically. */
    public static Map<Integer, Double> distributeEvenly(double amount, List<Goal> goals) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (goals == null || goals.isEmpty() || amount <= 0) return result;
        List<Double> parts = distributeEvenly(amount, goals.size());
        for (int i = 0; i < goals.size(); i++) {
            result.put(goals.get(i).id, parts.get(i));
        }
        return result;
    }

    private static LocalDate toDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
