package io.github.ahadporkar.ordertocash.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request body of {@code POST /api/orders}. Prices are never taken from the client. */
public record CreateOrderRequest(
    @NotBlank @Size(max = 64) String customerId,
    @NotNull CustomerSegment customerSegment,
    @NotEmpty @Size(max = 20) List<@Valid Line> lines) {

  public record Line(@NotBlank @Size(max = 40) String sku, @Min(1) @Max(1000) int quantity) {}
}
