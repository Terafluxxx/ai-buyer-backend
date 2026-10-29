package ru.aibuyer.mvp.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Pure calculation; rational comparisons avoid rounding at decision boundaries. */
public final class RecommendationCalculator {
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");
    public enum Status { CRITICAL, NEED_PURCHASE, NORMAL, NO_SALES }
    public record Result(String averageDailySales, String stockDays, String reorderPoint,
                         String targetStock, int recommendedQuantity, String estimatedCost,
                         Status status, List<String> warningCodes, String reason) {}

    public Result calculate(long sales, int stock, Integer leadDays, Integer minimumOrder,
                            BigDecimal price, long incoming) {
        if (sales < 0 || sales > 9007199254740991L || stock < 0 || incoming < 0
                || incoming > 9007199254740991L) throw new IllegalArgumentException("INVALID_VALUE");
        boolean selected = leadDays != null;
        int lead = selected ? leadDays : 7;
        int minimum = selected && minimumOrder != null ? minimumOrder : 1;
        if (lead < 0 || lead > 365 || minimum < 1) throw new IllegalArgumentException("INVALID_VALUE");
        if (!selected) price = null;
        if (price != null && (price.signum() < 0 || price.compareTo(MAX_MONEY) > 0
                || price.stripTrailingZeros().scale() > 2)) throw new IllegalArgumentException("INVALID_VALUE");
        BigDecimal s = BigDecimal.valueOf(sales);
        BigDecimal q30 = BigDecimal.valueOf(stock).multiply(THIRTY);
        BigDecimal delivery = s.multiply(BigDecimal.valueOf(lead));
        BigDecimal reorder = s.multiply(BigDecimal.valueOf(lead + 7));
        BigDecimal target = s.multiply(BigDecimal.valueOf(lead + 30));
        Status status = sales == 0 ? Status.NO_SALES : q30.compareTo(delivery) <= 0
                ? Status.CRITICAL : q30.compareTo(reorder) <= 0 ? Status.NEED_PURCHASE : Status.NORMAL;
        int quantity = 0;
        if (status == Status.CRITICAL || status == Status.NEED_PURCHASE) {
            BigDecimal needed = target.subtract(q30).subtract(BigDecimal.valueOf(incoming).multiply(THIRTY))
                    .divide(THIRTY, 0, RoundingMode.CEILING).max(BigDecimal.ZERO);
            if (needed.signum() > 0) needed = needed.max(BigDecimal.valueOf(minimum));
            try { quantity = needed.intValueExact(); }
            catch (ArithmeticException e) { throw new ArithmeticException("NUMERIC_OVERFLOW"); }
        }
        BigDecimal cost = price == null ? null : price.multiply(BigDecimal.valueOf(quantity));
        if (cost != null && cost.compareTo(MAX_MONEY) > 0) throw new ArithmeticException("NUMERIC_OVERFLOW");
        List<String> warnings = new ArrayList<>();
        if (!selected) warnings.add("SUPPLIER_NOT_SELECTED");
        if (sales == 0) warnings.add("NO_SALES");
        if (incoming > 0) warnings.add("INCOMING_NOT_DATED");
        String average = display(s, THIRTY);
        String reason = "Остаток " + stock + " шт., средние продажи " + average
                + " шт./день, срок поставки " + lead + " дней. Уже заказано " + incoming
                + " шт. Рекомендуется купить " + quantity + " шт.";
        if (sales == 0) reason += " За 30 завершённых дней продажи не зарегистрированы.";
        if (incoming > 0) reason += " Заказанный объём вычтен, дата прибытия неизвестна.";
        if (!selected) reason += " Поставщик не выбран, бюджет неизвестен.";
        return new Result(average, sales == 0 ? null : display(q30, s), display(reorder, THIRTY),
                display(target, THIRTY), quantity, cost == null ? null : cost.setScale(2).toPlainString(),
                status, List.copyOf(warnings), reason);
    }
    private static String display(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).toPlainString();
    }
}
