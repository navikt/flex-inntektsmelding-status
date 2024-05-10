CREATE TABLE vedtaksperiode_behandling
(
    id                   VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    opprettet            TIMESTAMP WITH TIME ZONE NOT NULL,
    oppdatert            TIMESTAMP WITH TIME ZONE NOT NULL,
    siste_spleisstatus   VARCHAR                  NOT NULL,
    siste_varslingstatus VARCHAR                  NULL,
    vedtaksperiode_id    VARCHAR(36)              NOT NULL,
    behandling_id        VARCHAR(36)              NOT NULL UNIQUE,
    sykepengesoknad_uuid VARCHAR(36)              NOT NULL
);


CREATE TABLE vedtaksperiode_behandling_status
(
    id                          VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    periode_behandling_id       VARCHAR(36)              NOT NULL REFERENCES vedtaksperiode_behandling (id),
    opprettet                   TIMESTAMP WITH TIME ZONE NOT NULL,
    status                      VARCHAR                  NOT NULL,
    brukervarsel_id             VARCHAR(36)              NULL,
    ditt_sykefravaer_melding_id VARCHAR(36)              NULL
);

CREATE INDEX vedtaksperiode_behandling_sykepengesoknad_uuid_idx ON vedtaksperiode_behandling (sykepengesoknad_uuid);
CREATE INDEX vedtaksperiode_behandling_siste_statuser ON vedtaksperiode_behandling (siste_spleisstatus, siste_varslingstatus);

CREATE INDEX sykepengesoknad_fnr_idx ON sykepengesoknad (fnr);