DROP TABLE vedtaksperiode_behandling_status;
DROP TABLE vedtaksperiode_behandling_sykepengesoknad;
DROP TABLE vedtaksperiode_behandling;

CREATE TABLE vedtaksperiode_behandling
(
    id                             VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    opprettet_database             TIMESTAMP WITH TIME ZONE NOT NULL,
    oppdatert_database             TIMESTAMP WITH TIME ZONE NOT NULL,
    siste_spleisstatus             VARCHAR                  NOT NULL,
    siste_spleisstatus_tidspunkt   TIMESTAMP WITH TIME ZONE NOT NULL,
    siste_varslingstatus           VARCHAR                  NULL,
    siste_varslingstatus_tidspunkt TIMESTAMP WITH TIME ZONE NULL,
    vedtaksperiode_id              VARCHAR(36)              NOT NULL,
    behandling_id                  VARCHAR(36)              NOT NULL UNIQUE,
    CONSTRAINT unique_vedtaksperiode_id_behandling_id UNIQUE (vedtaksperiode_id, behandling_id)

);

CREATE TABLE vedtaksperiode_behandling_sykepengesoknad
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    vedtaksperiode_behandling_id VARCHAR(36) NOT NULL REFERENCES vedtaksperiode_behandling (id),
    sykepengesoknad_uuid         VARCHAR(36) NOT NULL,
    CONSTRAINT unique_vedtaksperiode_behandling_sykepengesoknad UNIQUE (vedtaksperiode_behandling_id, sykepengesoknad_uuid)
);

CREATE TABLE vedtaksperiode_behandling_status
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    vedtaksperiode_behandling_id VARCHAR(36)              NOT NULL REFERENCES vedtaksperiode_behandling (id),
    opprettet_database           TIMESTAMP WITH TIME ZONE NOT NULL,
    tidspunkt                    TIMESTAMP WITH TIME ZONE NOT NULL,
    status                       VARCHAR                  NOT NULL,
    brukervarsel_id              VARCHAR(36)              NULL,
    ditt_sykefravaer_melding_id  VARCHAR(36)              NULL
);

CREATE INDEX vedtaksperiode_behandling_siste_statuser ON vedtaksperiode_behandling (siste_spleisstatus, siste_varslingstatus);

