CREATE TABLE FORELAGTE_OPPLYSNINGER_AINNTEKT
(
    ID                             VARCHAR(36) DEFAULT UUID_GENERATE_V4() PRIMARY KEY,
    FNR                            VARCHAR(11) NULL,
    MELDING                        JSONB                    NOT NULL,
    VEDTAKSPERIODE_ID              VARCHAR(36)              NOT NULL,
    BEHANDLING_ID                  VARCHAR(36)              NOT NULL,
    FORELAGTE_OPPLYSNINGER_MELDING JSONB                    NOT NULL,
    OPPRETTET                      TIMESTAMP WITH TIME ZONE NOT NULL,
    FORELAGT                       TIMESTAMP WITH TIME ZONE NULL,
    UNIQUE (BEHANDLING_ID, VEDTAKSPERIODE_ID)
);

CREATE INDEX idx_forelagteopplysningerainntekt_forelagt_null
    ON FORELAGTE_OPPLYSNINGER_AINNTEKT (FORELAGT) WHERE FORELAGT IS NULL;

CREATE INDEX idx_forelagteopplysningerainntekt_fnr
    ON FORELAGTE_OPPLYSNINGER_AINNTEKT (FNR);
