package com.rag.provider.domain;

/**
 * Pricing of a catalog model.
 *
 * @param inputPerMillion  USD per million input tokens
 * @param outputPerMillion USD per million output tokens
 */
public record ModelCost(double inputPerMillion, double outputPerMillion) {
}