package com.rag.contract.provider;

/**
 * Pricing of a catalog model.
 *
 * @param inputPerMillion  USD per million input tokens
 * @param outputPerMillion USD per million output tokens
 */
public record ModelCostDTO(double inputPerMillion, double outputPerMillion) {
}