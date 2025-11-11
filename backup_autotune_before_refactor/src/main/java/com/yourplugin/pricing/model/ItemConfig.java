package com.yourplugin.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

public record ItemConfig(
    String section,
    Optional<Double> price,
    @JsonProperty("m") Optional<Double> margin,
    @JsonProperty("t") Optional<Double> tax,
    @JsonProperty("pmin") Optional<Double> minPrice,
    @JsonProperty("pmax") Optional<Double> maxPrice,
    Optional<Double> volatility,
    List<String> customRecipes,
    Optional<Amortization> amortize
) {
    public ItemConfig {
        // Default values for Optional fields if not provided
        price = price != null ? price : Optional.empty();
        margin = margin != null ? margin : Optional.empty();
        tax = tax != null ? tax : Optional.empty();
        minPrice = minPrice != null ? minPrice : Optional.empty();
        maxPrice = maxPrice != null ? maxPrice : Optional.empty();
        volatility = volatility != null ? volatility : Optional.empty();
        amortize = amortize != null ? amortize : Optional.empty();
    }

    // Constructor for Jackson to use when parsing from YAML
    public ItemConfig(
        String section,
        Double price,
        Double margin,
        Double tax,
        Double minPrice,
        Double maxPrice,
        Double volatility,
        List<String> customRecipes,
        Amortization amortize
    ) {
        this(
            section,
            Optional.ofNullable(price),
            Optional.ofNullable(margin),
            Optional.ofNullable(tax),
            Optional.ofNullable(minPrice),
            Optional.ofNullable(maxPrice),
            Optional.ofNullable(volatility),
            customRecipes != null ? customRecipes : List.of(),
            Optional.ofNullable(amortize)
        );
    }

    // Default constructor for when no config is found for an item
    public ItemConfig() {
        this(null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }
}
