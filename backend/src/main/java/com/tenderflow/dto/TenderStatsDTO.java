package com.tenderflow.dto;

import java.math.BigDecimal;
import java.util.List;

public record TenderStatsDTO(
        long totalTenders,
        long openTenders,
        long draftTenders,
        long closedTenders,
        long totalBids,
        BigDecimal totalOpenBudget,
        BigDecimal totalAwardedVolume,
        BigDecimal totalSavingsAmount,
        double averageSavingsPercentage,
        List<TopSupplierDTO> topSuppliers
) {
    public record TopSupplierDTO(
            String bidderName,
            long wonCount,
            BigDecimal totalWonVolume
    ) {}
}