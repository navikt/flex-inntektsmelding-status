DROP TABLE vedtaksperiode_behandling_status;
DROP TABLE vedtaksperiode_behandling;

CREATE TABLE vedtaksperiode_behandling
(
    id                   VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    opprettet_database   TIMESTAMP WITH TIME ZONE NOT NULL,
    oppdatert            TIMESTAMP WITH TIME ZONE NOT NULL,
    siste_spleisstatus   VARCHAR                  NOT NULL,
    siste_varslingstatus VARCHAR                  NULL,
    vedtaksperiode_id    VARCHAR(36)              NOT NULL,
    behandling_id        VARCHAR(36)              NOT NULL UNIQUE,
    -- 0 til n her sykepengesoknad_
);

-- https://github.com/navikt/helse-sporbar/blob/f0f36b543182aba531e81cc2850f3e2fe9c32207/src/main/kotlin/no/nav/helse/sporbar/sis/SisPublisher.kt#L15
-- her er koden til det de sender
-- https://github.com/navikt/helse-sporbar/blob/master/src/main/kotlin/no/nav/helse/sporbar/sis/Behandlingstatusmelding.kt#L31
-- sjekk, gpt
CREATE TABLE vedtaksperiode_behandling_sykepengesoknad
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    vedtaksperiode_behandling_id VARCHAR(36)              NOT NULL REFERENCES vedtaksperiode_behandling (id),
    sykepengesoknad_uuid         VARCHAR(36)              NOT NULL
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

CREATE INDEX vedtaksperiode_behandling_sykepengesoknad_uuid_idx ON vedtaksperiode_behandling (sykepengesoknad_uuid);
CREATE INDEX vedtaksperiode_behandling_siste_statuser ON vedtaksperiode_behandling (siste_spleisstatus, siste_varslingstatus);

