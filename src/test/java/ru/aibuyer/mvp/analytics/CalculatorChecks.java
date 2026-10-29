package ru.aibuyer.mvp.analytics;
import java.math.BigDecimal;
public class CalculatorChecks {
    public static void main(String[] args) {
        var c = new RecommendationCalculator();
        var r = c.calculate(60, 10, 7, 20, new BigDecimal("100.00"), 0);
        check(r.recommendedQuantity() == 64 && "6400.00".equals(r.estimatedCost()), "contract example");
        check(r.status() == RecommendationCalculator.Status.CRITICAL, "critical status");
        r = c.calculate(60, 10, 7, 20, new BigDecimal("100.00"), 70);
        check(r.recommendedQuantity() == 0 && r.status() == RecommendationCalculator.Status.CRITICAL, "incoming retains physical status");
        r = c.calculate(0, 0, null, null, null, 0);
        check(r.stockDays() == null && r.recommendedQuantity() == 0 && r.estimatedCost() == null, "no sales");
        r = c.calculate(1, 0, 0, 1, BigDecimal.ONE, 0);
        check(r.recommendedQuantity() == 1, "no premature rounding");
        r = c.calculate(60, 28, 7, 20, BigDecimal.ONE, 0);
        check(r.status() == RecommendationCalculator.Status.NEED_PURCHASE && r.recommendedQuantity() == 46, "boundary and no pack rounding");
        r = c.calculate(60, 29, 7, 20, BigDecimal.ONE, 0);
        check(r.status() == RecommendationCalculator.Status.NORMAL && r.recommendedQuantity() == 0, "normal");
        try { c.calculate(9007199254740991L, 0, 365, 1, BigDecimal.ONE, 0); throw new AssertionError("overflow accepted"); }
        catch (ArithmeticException expected) { check("NUMERIC_OVERFLOW".equals(expected.getMessage()), "overflow code"); }
        System.out.println("7 calculator scenarios passed");
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
