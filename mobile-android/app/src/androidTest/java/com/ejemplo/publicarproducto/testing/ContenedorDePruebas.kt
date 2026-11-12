package com.ejemplo.publicarproducto.testing

import android.content.Context
import com.ejemplo.publicarproducto.model.repository.AdminRepositorio
import com.ejemplo.publicarproducto.model.repository.AuthRepositorio
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.repository.ChatbotRepositorio
import com.ejemplo.publicarproducto.model.repository.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.model.di.ContenedorDatos
import com.ejemplo.publicarproducto.model.repository.ConversacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.FavoritosRepositorio
import com.ejemplo.publicarproducto.model.repository.MetricasRepositorio
import com.ejemplo.publicarproducto.model.sync.MonitorConectividad
import com.ejemplo.publicarproducto.model.repository.NotificacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.PedidosRepositorio
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.repository.UmbralStockRepositorio
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.model.sync.ServicioSincronizacion

/**
 * Contenedor de datos que apunta al servidor HTTP de pruebas.
 *
 * Lo usan las pruebas instrumentadas del Grupo B (productos y favoritos):
 * los ViewModels resuelven sus repositorios en `ProveedorDatos`, así que
 * basta con fijar aquí un contenedor con las interfaces de prueba.
 */
object ContenedorDePruebas {

    /**
     * @param idVendedor usuario simulado en la sesión de las pruebas.
     */
    fun crear(
        contexto: Context,
        bd: PulgaTecDatabase,
        sesion: SessionManager,
        servidor: ServidorDePruebas,
        idVendedor: Long = 5L,
    ): ContenedorDatos {
        val cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        val notificaciones = NotificacionesRepositorio(bd, servidor.api, cola)
        val pedidos = PedidosRepositorio(bd, servidor.api, cola)
        val metricas = MetricasRepositorio(bd, servidor.api)
        val umbrales = UmbralStockRepositorio(bd, servidor.api, cola, notificaciones)

        return ContenedorDatos(
            contexto = contexto,
            bd = bd,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
            metricas = metricas,
            umbrales = umbrales,
            metadatos = bd.metadatoSincronizacionDao(),
            conectividad = MonitorConectividad(contexto),
            sesion = sesion,
            sincronizacion = ServicioSincronizacion(
                bd = bd,
                api = servidor.api,
                cola = cola,
                notificaciones = notificaciones,
                pedidos = pedidos,
                metadatos = bd.metadatoSincronizacionDao(),
                sesion = sesion,
                umbrales = umbrales
            ),
            auth = AuthRepositorio(servidor.authApi, sesion),
            // HU 23/HU 24 - roles y auditoría (Django). Sin esta inyección el
            // contenedor usaría el cliente real (10.0.2.2:8001) y las pruebas
            // harían peticiones al servidor de desarrollo.
            admin = AdminRepositorio(servidor.adminApi),
            productos = ProductosRepositorio(servidor.api) { idVendedor },
            favoritos = FavoritosRepositorio(servidor.api),
            // HU 11/HU 12/HU 13/HU 14 - el carrito también apunta al servidor
            // de pruebas: si no, los ViewModel hablarían con el backend real.
            carrito = CarritoRepositorio(servidor.api),
            // HU 16/HU 17 - el chatbot apunta al servidor de pruebas.
            chatbot = ChatbotRepositorio(servidor.api),
            // HU 38/HU 39 - las conversaciones usan la interfaz de pruebas:
            // si no, el ViewModel hablaría con el backend real.
            conversaciones = ConversacionesRepositorio(servidor.conversacionApi)
        )
    }
}
