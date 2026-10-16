package com.ejemplo.backenduserapi.controller;
import com.ejemplo.backenduserapi.dto.AsignarRolRequest;
import com.ejemplo.backenduserapi.dto.UsuarioRolResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.RolService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** HU 23 - Consulta y asignación de roles exclusivamente administrativa. */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {
    private final RolService servicio;
    public AdminUsuarioController(RolService servicio) { this.servicio = servicio; }
    @GetMapping
    public List<UsuarioRolResponse> listar(@RequestAttribute("usuarioSesion") Usuario administrador) { return servicio.listar(administrador); }
    @PatchMapping("/{idUsuario}/rol")
    public UsuarioRolResponse asignar(@PathVariable Long idUsuario, @Valid @RequestBody AsignarRolRequest solicitud,
            @RequestAttribute("usuarioSesion") Usuario administrador) { return servicio.asignar(administrador, idUsuario, solicitud.getRol()); }
}
