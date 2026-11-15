package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.IntentoCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** HU 59.2 - Busca el pedido registrado para una clave de intento. */
public interface IntentoCompraRepository extends JpaRepository<IntentoCompra, Long> {

    Optional<IntentoCompra> findByClave(String clave);
}
