package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.TokenRecuperacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRecuperacion t where t.token = :token")
    Optional<TokenRecuperacion> buscarParaConsumir(@Param("token") String token);

    void deleteByUsuarioId(Long usuarioId);
}
