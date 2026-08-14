#!/usr/bin/env bash

set -euo pipefail

readonly LOGGING_CONFIG="${ODL_HOME}/etc/org.ops4j.pax.logging.cfg"

: "${SMA_LOG_DIRECTORY:=/logs}"
: "${SMA_LOG_FILENAME:=sdn_mpls_ml_controller.log}"
: "${SMA_LOG_FILE_MAX_BYTES:=10485760}"
: "${SMA_LOG_FILE_BACKUP_COUNT:=5}"
: "${SMA_LOG_LEVEL:=DEBUG}"

if [[ ! "${SMA_LOG_DIRECTORY}" =~ ^/[A-Za-z0-9_./-]+$ ]]; then
    printf '%s\n' "SMA_LOG_DIRECTORY debe ser una ruta absoluta sin espacios" >&2
    exit 64
fi
if [[ ! "${SMA_LOG_FILENAME}" =~ ^[A-Za-z0-9._-]+$ ]]; then
    printf '%s\n' "SMA_LOG_FILENAME contiene caracteres no permitidos" >&2
    exit 64
fi
if [[ ! "${SMA_LOG_FILE_MAX_BYTES}" =~ ^[1-9][0-9]*$ ]]; then
    printf '%s\n' "SMA_LOG_FILE_MAX_BYTES debe ser un entero positivo" >&2
    exit 64
fi
if [[ ! "${SMA_LOG_FILE_BACKUP_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
    printf '%s\n' "SMA_LOG_FILE_BACKUP_COUNT debe ser un entero positivo" >&2
    exit 64
fi

SMA_LOG_LEVEL="${SMA_LOG_LEVEL^^}"
case "${SMA_LOG_LEVEL}" in
    TRACE|DEBUG|INFO|WARN|ERROR)
        ;;
    *)
        printf '%s\n' "SMA_LOG_LEVEL debe ser TRACE, DEBUG, INFO, WARN o ERROR" >&2
        exit 64
        ;;
esac

mkdir -p "${SMA_LOG_DIRECTORY}"
touch "${SMA_LOG_DIRECTORY}/${SMA_LOG_FILENAME}"

sed -i '/^# SMA_LOGGING_BEGIN$/,/^# SMA_LOGGING_END$/d' "${LOGGING_CONFIG}"
{
    printf '\n# SMA_LOGGING_BEGIN\n'
    printf 'log4j2.appender.smaConsole.type = Console\n'
    printf 'log4j2.appender.smaConsole.name = SmaConsole\n'
    printf 'log4j2.appender.smaConsole.target = SYSTEM_OUT\n'
    printf 'log4j2.appender.smaConsole.layout.type = PatternLayout\n'
    printf 'log4j2.appender.smaConsole.layout.pattern = %%m%%n\n'
    printf 'log4j2.appender.smaPaxOsgi.type = PaxOsgi\n'
    printf 'log4j2.appender.smaPaxOsgi.name = SmaPaxOsgi\n'
    printf 'log4j2.appender.smaPaxOsgi.filter = VmLogAppender\n'
    printf 'log4j2.appender.smaFile.type = RollingRandomAccessFile\n'
    printf 'log4j2.appender.smaFile.name = SmaRollingFile\n'
    printf 'log4j2.appender.smaFile.fileName = %s/%s\n' "${SMA_LOG_DIRECTORY}" "${SMA_LOG_FILENAME}"
    printf 'log4j2.appender.smaFile.filePattern = %s/%s.%%i\n' "${SMA_LOG_DIRECTORY}" "${SMA_LOG_FILENAME}"
    printf 'log4j2.appender.smaFile.append = true\n'
    printf 'log4j2.appender.smaFile.layout.type = PatternLayout\n'
    printf 'log4j2.appender.smaFile.layout.pattern = %%m%%n\n'
    printf 'log4j2.appender.smaFile.policies.type = Policies\n'
    printf 'log4j2.appender.smaFile.policies.size.type = SizeBasedTriggeringPolicy\n'
    printf 'log4j2.appender.smaFile.policies.size.size = %s\n' "${SMA_LOG_FILE_MAX_BYTES}"
    printf 'log4j2.appender.smaFile.strategy.type = DefaultRolloverStrategy\n'
    printf 'log4j2.appender.smaFile.strategy.max = %s\n' "${SMA_LOG_FILE_BACKUP_COUNT}"
    printf 'log4j2.appender.smaFile.strategy.fileIndex = min\n'
    printf 'log4j2.logger.sma.name = com.sma.sdn\n'
    printf 'log4j2.logger.sma.level = %s\n' "${SMA_LOG_LEVEL}"
    printf 'log4j2.logger.sma.additivity = false\n'
    printf 'log4j2.logger.sma.appenderRef.SmaConsole.ref = SmaConsole\n'
    printf 'log4j2.logger.sma.appenderRef.SmaPaxOsgi.ref = SmaPaxOsgi\n'
    printf 'log4j2.logger.sma.appenderRef.SmaRollingFile.ref = SmaRollingFile\n'
    printf '# SMA_LOGGING_END\n'
} >> "${LOGGING_CONFIG}"

printf '%s\n' "Se configuro el logging estructurado de SDN-MPLS-ML en ${SMA_LOG_DIRECTORY}/${SMA_LOG_FILENAME}"

exec "$@"
