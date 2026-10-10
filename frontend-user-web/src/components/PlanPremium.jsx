import { useEffect, useState } from 'react'
import { consultarPlanPremium } from '../api/productos.js'

// HU 18 - Consultar beneficios del plan Premium.
export default function PlanPremium() {
    const [plan, setPlan] = useState(null)
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [seleccionado, setSeleccionado] = useState(false)

    useEffect(() => {
        let activo = true

        async function cargarPlan() {
            try {
                const datos = await consultarPlanPremium()
                if (activo) setPlan(datos)
            } catch {
                if (activo) {
                    setError('No se pudo consultar el plan Premium. Verifica que el backend esté encendido.')
                }
            } finally {
                if (activo) setCargando(false)
            }
        }

        cargarPlan()

        return () => {
            activo = false
        }
    }, [])

    return (
        <section className="tarjeta plan-premium">
            <h2>Plan Premium de PulgaTec</h2>

            {cargando && <p>Cargando información del plan...</p>}

            {error && <p role="alert">{error}</p>}

            {!cargando && plan && (
                <>
                    <h3>{plan.nombre}</h3>
                    <p>{plan.descripcion}</p>

                    <h3>Beneficios del plan</h3>
                    <ul className="premium-beneficios">
                        {plan.beneficios.map((beneficio, indice) => (
                            <li key={indice}>✓ {beneficio}</li>
                        ))}
                    </ul>

                    <button
                        type="button"
                        onClick={() => setSeleccionado(true)}
                        disabled={seleccionado}
                    >
                        {seleccionado
                            ? 'Plan seleccionado'
                            : 'Seleccionar plan Premium'}
                    </button>

                    {seleccionado && (
                        <p role="status" className="premium-confirmacion">
                            Has seleccionado el plan Premium para conocer sus beneficios.
                            Esta selección no activa una suscripción ni genera pagos.
                        </p>
                    )}
                </>
            )}
        </section>
    )
}
