package com.example.budgetapp.util;

import static org.junit.Assert.assertEquals;

import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;

public class BudgetCalculatorTest {
    @Test
    public void progress_preservesSubPercentSpending() {
        assertEquals(93, BudgetCalculator.progress(18.51, 2000));
        assertEquals(BudgetCalculator.PROGRESS_MAX, BudgetCalculator.progress(2100, 2000));
        assertEquals(0, BudgetCalculator.progress(0, 2000));
    }

    @Test
    public void remainingAmount_offsetsOverspendingAcrossDays() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 2);
        BudgetPlan plan = new BudgetPlan("test", millis(start), millis(end), 200);
        Transaction firstDay = new Transaction(millis(start) + 1000, 0, "餐饮", 150);
        Transaction secondDay = new Transaction(millis(end) + 1000, 0, "餐饮", 10);

        assertEquals(40, BudgetCalculator.remainingAmount(plan, Arrays.asList(firstDay, secondDay)), 0.001);
    }

    @Test
    public void distributeEvenly_preservesCents() {
        Goal first = new Goal("a", 100, 0, false, 0);
        first.id = 1;
        Goal second = new Goal("b", 100, 0, false, 0);
        second.id = 2;
        Goal third = new Goal("c", 100, 0, false, 0);
        third.id = 3;

        Map<Integer, Double> result = BudgetCalculator.distributeEvenly(10, Arrays.asList(first, second, third));

        assertEquals(3.34, result.get(1), 0.001);
        assertEquals(3.33, result.get(2), 0.001);
        assertEquals(3.33, result.get(3), 0.001);
    }

    private static long millis(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
