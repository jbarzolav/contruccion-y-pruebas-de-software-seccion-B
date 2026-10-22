package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
    Optional<Conversacion> findByProducto_IdAndCliente_IdAndVendedor_Id(Long producto, Long cliente, Long vendedor);
    List<Conversacion> findByCliente_IdOrVendedor_IdOrderByIdDesc(Long cliente, Long vendedor);
}
