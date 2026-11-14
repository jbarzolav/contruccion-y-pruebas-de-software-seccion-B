export default function CampoTextoAjustable({ value, className, ...props }) {
    return (
        <div className="campo-texto-ajustable" data-texto={value ?? ''}>
            <textarea {...props} value={value} rows={1} className={className} />
        </div>
    )
}
