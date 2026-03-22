/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.dto.LogCriticoDto;
import com.baryx.common.enums.EstadoLog;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.servidor.mapeo.LogCriticoMapper;
import com.baryx.servidor.modelo.entidad.LogCritico;
import com.baryx.servidor.repositorio.LogCriticoRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*Este servicio implementa la lógica de negocio para gestionar los logs críticos del sistema.
 * Provee métodos para listar, filtrar, registrar y resolver logs críticos, así como contar los pendientes.
 * Utiliza un repositorio JPA para acceder a la base de datos y un mapper MapStruct para convertir entre entidades y DTOs.
 * La fecha de creación de los logs se establece automáticamente al persistir la entidad, por lo que se ignora en el mapeo. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogCriticoServicioImpl implements LogCriticoServicio {

    private static final Logger logger = LoggerFactory.getLogger(LogCriticoServicioImpl.class);

    private final LogCriticoRepositorio repositorio;
    private final LogCriticoMapper mapper;

    @Override
    public List<LogCriticoDto> listarTodos() {
        logger.debug("Listando todos los logs críticos");
        return mapper.aListaDto(repositorio.findAllByOrderByFechaCreacionDesc());
    }

    @Override
    public List<LogCriticoDto> listarPorNivel(String nivel) {
        logger.debug("Listando logs críticos por nivel: {}", nivel);
        return mapper.aListaDto(repositorio.findByNivelOrderByFechaCreacionDesc(nivel));
    }

    @Override
    public List<LogCriticoDto> listarNoResueltos() {
        logger.debug("Listando logs críticos no resueltos");
        return mapper.aListaDto(repositorio.findByEstadoNotOrderByFechaCreacionDesc(EstadoLog.RESUELTO));
    }

    @Override
    @Transactional
    public LogCriticoDto registrar(LogCriticoDto dto) {
        logger.warn("Registrando log crítico - Nivel: {} | Origen: {} | Mensaje: {}",
                dto.getNivel(), dto.getOrigen(), dto.getMensaje());

        LogCritico entidad = mapper.aEntidad(dto);
        entidad.setEstado(EstadoLog.NOTIFICACION_ERROR);
        LogCritico guardado = repositorio.save(entidad);
        return mapper.aDto(guardado);
    }

    @Override
    @Transactional
    public LogCriticoDto cambiarEstado(Long idLog, String estado) {
        logger.info("Cambiando estado del log {} a {}", idLog, estado);

        EstadoLog nuevoEstado;
        try {
            nuevoEstado = EstadoLog.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado no válido: " + estado);
        }

        LogCritico log = repositorio.findById(idLog)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("LogCritico", idLog));

        log.setEstado(nuevoEstado);
        return mapper.aDto(repositorio.save(log));
    }

    @Override
    public long contarNoResueltos() {
        return repositorio.countByEstadoNot(EstadoLog.RESUELTO);
    }
}
