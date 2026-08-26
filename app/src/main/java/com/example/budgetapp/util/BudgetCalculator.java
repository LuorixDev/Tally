package com.example.budgetapp.util;

import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        double dailyBudget = days > 0 ? plan.totalAmount / days : 0;
        return Math.max(0, dailyBudget - expenseBetween(transactions, startMillis, endMillis));
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
        long cents = Math.max(0, Math.round(amount * 100));
        long each = cents / goals.size();
        long remainder = cents % goals.size();
        for (int i = 0; i < goals.size(); i++) {
            result.put(goals.get(i).id, (each + (i < remainder ? 1 : 0)) / 100.0);
        }
        return result;
    }

    private static LocalDate toDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
