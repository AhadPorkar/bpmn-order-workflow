package io.github.ahadporkar.ordertocash.inventory;

import java.math.BigDecimal;

/** A catalog item with its stock. {@code available} excludes stock reserved for open orders. */
public record Product(String sku, String name, BigDecimal unitPrice, int available, int reserved) {}
