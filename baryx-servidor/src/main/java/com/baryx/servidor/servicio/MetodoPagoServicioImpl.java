/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.common.excepcion.ValidacionException;
import com.baryx.common.dto.MetodoPagoDto;
import com.baryx.servidor.modelo.entidad.MetodoPago;
import com.baryx.servidor.repositorio.MetodoPagoRepositorio;
import com.baryx.servidor.mapeo.MetodoPagoMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/*Este servicio maneja toda la lógica de negocio relacionada con métodos de pago:
 *-Lista métodos de pago activos (ordenados por campo 'orden')
 *-Crea nuevos métodos de pago (validando nombre único)
 *-Elimina métodos de pago (excepto el predeterminado EFECTIVO) 
 *-Los nombres deben ser únicos (no pueden existir dos métodos con el mismo nombre)
 *-Solo el ADMIN puede crear y eliminar métodos de pago
 *-La eliminación es lógica (marca activo=false) */
@Service
@Transactional
@RequiredArgsConstructor
public class MetodoPagoServicioImpl implements MetodoPagoServicio {
    
    private static final Logger logger = LoggerFactory.getLogger(MetodoPagoServicioImpl.class);
    
    private final MetodoPagoRepositorio metodoPagoRepositorio;
    private final MetodoPagoMapper metodoPagoMapper;
    
    @Override
    @Transactional(readOnly = true)
    public List<MetodoPagoDto> listarActivos() {
        logger.debug("Listando métodos de pago activos");
        return metodoPagoMapper.aListaDto(metodoPagoRepositorio.findByActivoTrueOrderByOrdenAsc());
    }
    
    @Override
    public MetodoPagoDto crear(MetodoPagoDto dto) {
        logger.info("Creando nuevo método de pago: {}", dto.getNombre());
        
        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            throw new ValidacionException("El nombre del método de pago es obligatorio");
        }
        
        dto.setNombre(dto.getNombre().trim().toUpperCase());
        
        if (metodoPagoRepositorio.findByNombreAndActivoTrue(dto.getNombre()).isPresent()) {
            throw new ValidacionException("Ya existe un método de pago con el nombre: " + dto.getNombre());
        }
        
        MetodoPago metodoPago = metodoPagoMapper.aEntidad(dto);
        metodoPago.setActivo(true);
        metodoPago.setEsPredeterminado(false);
        
        if (metodoPago.getOrden() == null || metodoPago.getOrden() == 0) {
            List<MetodoPago> existentes = metodoPagoRepositorio.findByActivoTrueOrderByOrdenAsc();
            int maxOrden = existentes.stream()
                    .mapToInt(MetodoPago::getOrden)
                    .max()
                    .orElse(0);
            metodoPago.setOrden(maxOrden + 1);
        }
        
        if (metodoPago.getCodigo() == null || metodoPago.getCodigo().trim().isEmpty()) {
            metodoPago.setCodigo(generarSiguienteCodigoDisponible());
        }
        
        metodoPago = metodoPagoRepositorio.save(metodoPago);
        logger.info("Método de pago creado exitosamente con ID: {}", metodoPago.getIdMetodoPago());
        
        return metodoPagoMapper.aDto(metodoPago);
    }
    
    
    @Override
    public MetodoPagoDto actualizar(Long id, MetodoPagoDto dto) {
        logger.info("Actualizando método de pago ID: {} con nuevo nombre: {}, código: {}", id, dto.getNombre(), dto.getCodigo());
        
        MetodoPago metodoPago = metodoPagoRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Método de pago", id));
        
        final MetodoPago metodoOriginal = metodoPago;
        
        if (Boolean.TRUE.equals(metodoPago.getEsPredeterminado())) {
            throw new ValidacionException(
                    "No se puede modificar el método de pago '" + metodoPago.getNombre() + 
                    "' porque es el método predeterminado del sistema.");
        }
        
        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            throw new ValidacionException("El nombre del método de pago es obligatorio");
        }

        String nuevoNombre = dto.getNombre().trim().toUpperCase();
        
        metodoPagoRepositorio.findByNombreAndActivoTrue(nuevoNombre)
                .ifPresent(existente -> {
                    if (!existente.getIdMetodoPago().equals(id)) {
                        throw new ValidacionException("Ya existe un método de pago con el nombre: " + nuevoNombre);
                    }
                });
        
        // Manejar cambio de código con lógica de intercambio (swap)
        if (dto.getCodigo() != null && !dto.getCodigo().trim().isEmpty()) {
            String nuevoCodigo = dto.getCodigo().trim();
            // Si el código cambió, verificar si ya está en uso por otro método
            if (!nuevoCodigo.equals(metodoPago.getCodigo())) {
                metodoPagoRepositorio.findByCodigoAndActivoTrue(nuevoCodigo)
                        .ifPresent(otroMetodo -> {
                            // Intercambiar códigos: el otro método recibe el código actual
                            String codigoAnterior = metodoOriginal.getCodigo();
                            otroMetodo.setCodigo(codigoAnterior);
                            metodoPagoRepositorio.save(otroMetodo);
                            logger.info("Código intercambiado: método '{}' ahora tiene código '{}', método '{}' tendrá código '{}'",
                                    otroMetodo.getNombre(), codigoAnterior, nuevoNombre, nuevoCodigo);
                        });
                metodoPago.setCodigo(nuevoCodigo);
            }
        }
        
        metodoPago.setNombre(nuevoNombre);
        metodoPago = metodoPagoRepositorio.save(metodoPago);
        
        logger.info("Método de pago ID: {} actualizado exitosamente a '{}' con código '{}'", id, nuevoNombre, metodoPago.getCodigo());
        return metodoPagoMapper.aDto(metodoPago);
    }
    
    @Override
    public void eliminar(Long id) {
        logger.info("Eliminando método de pago ID: {}", id);
        
        MetodoPago metodoPago = metodoPagoRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Método de pago", id));
        
        if (Boolean.TRUE.equals(metodoPago.getEsPredeterminado())) {
            throw new ValidacionException(
                    "No se puede eliminar el método de pago '" + metodoPago.getNombre() + 
                    "' porque es el método predeterminado del sistema.");
        }
        
        metodoPago.setActivo(false);
        metodoPagoRepositorio.save(metodoPago);
        
        logger.info("Método de pago '{}' eliminado (desactivado) exitosamente", metodoPago.getNombre());
    }

    private String generarSiguienteCodigoDisponible() {
        List<MetodoPago> activos = metodoPagoRepositorio.findByActivoTrueOrderByOrdenAsc();
        java.util.Set<String> codigosUsados = activos.stream()
                .map(MetodoPago::getCodigo)
                .filter(c -> c != null)
                .collect(java.util.stream.Collectors.toSet());
        
        for (int i = 0; i <= 99; i++) {
            String candidato = String.format("%02d", i);
            if (!codigosUsados.contains(candidato)) {
                return candidato;
            }
        }
        throw new ValidacionException("No hay códigos disponibles (00-99). Máximo 100 métodos de pago.");
    }
}
