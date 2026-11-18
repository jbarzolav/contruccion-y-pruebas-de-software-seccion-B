package com.ejemplo.backenduserapi.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="mensajes_entrega",indexes=@Index(columnList="pedido_id,id"))
public class MensajeEntrega {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="pedido_id") private Pedido pedido;
    private Long autorId;
    @Column(length=2000,nullable=false) private String texto;
    private Instant fecha;
    protected MensajeEntrega() {}
    public MensajeEntrega(Pedido pedido, Long autorId, String texto) { this.pedido=pedido; this.autorId=autorId; this.texto=texto; fecha=Instant.now(); }
    public Long getId(){return id;} public Long getAutorId(){return autorId;} public String getTexto(){return texto;} public Instant getFecha(){return fecha;}
}
