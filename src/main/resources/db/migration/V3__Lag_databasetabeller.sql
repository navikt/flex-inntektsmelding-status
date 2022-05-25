CREATE TABLE inntektsmelding
(
    id                VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    fnr               VARCHAR(11)              NOT NULL,
    org_nr            VARCHAR(9)               NOT NULL,
    org_navn          VARCHAR                  NOT NULL,
    opprettet         TIMESTAMP WITH TIME ZONE NOT NULL,
    vedtak_fom        DATE                     NOT NULL,
    vedtak_tom        DATE                     NOT NULL,
    ekstern_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    ekstern_id        VARCHAR(36)              NOT NULL
);

CREATE INDEX inntektsmelding_fnr_idx ON inntektsmelding (fnr);
CREATE INDEX inntektsmelding_ekstern_id_idx ON inntektsmelding (ekstern_id);

CREATE TABLE inntektsmelding_status
(
    id                 VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    inntektsmelding_id VARCHAR(36)              NOT NULL REFERENCES inntektsmelding (id),
    opprettet          TIMESTAMP WITH TIME ZONE NOT NULL,
    status             VARCHAR                  NOT NULL
);

CREATE INDEX inntektsmelding_id_fk_idx ON inntektsmelding_status (inntektsmelding_id);