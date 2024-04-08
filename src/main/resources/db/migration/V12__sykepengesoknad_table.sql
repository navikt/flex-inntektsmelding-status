CREATE TABLE sykepengesoknad
(
    ID                   VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    FNR                  VARCHAR                  NOT NULL,
    ORGNUMMER            VARCHAR                  NOT NULL,
    SYKEPENGESOKNAD_UUID VARCHAR                  NOT NULL UNIQUE,
    FOM                  DATE                     NOT NULL,
    TOM                  DATE                     NOT NULL,
    SOKNADSTYPE          VARCHAR                  NOT NULL,
    OPPRETTET_DATABASE   TIMESTAMP WITH TIME ZONE NOT NULL,
    SENDT                TIMESTAMP WITH TIME ZONE NOT NULL
);
