package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

public class CarritoTotalResponse {

    private final BigDecimal total;

    public CarritoTotalResponse(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
