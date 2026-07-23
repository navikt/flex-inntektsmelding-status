CREATE TABLE vedtaksperiode_behandling_status_v2
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    vedtaksperiode_behandling_id VARCHAR(36)              NOT NULL
        CONSTRAINT vedtaksperiode_behandling_status_v2_vpb_id_fkey
        REFERENCES vedtaksperiode_behandling (id),
    opprettet_database           TIMESTAMP WITH TIME ZONE NOT NULL,
    tidspunkt                    TIMESTAMP WITH TIME ZONE NOT NULL,
    status                       VARCHAR                  NOT NULL,
    brukervarsel_id              VARCHAR(36)              NULL,
    ditt_sykefravaer_melding_id  VARCHAR(36)              NULL
);

CREATE INDEX vbst_v2_vedtaksperiode_behandling_id_idx
    ON vedtaksperiode_behandling_status_v2 (vedtaksperiode_behandling_id);
