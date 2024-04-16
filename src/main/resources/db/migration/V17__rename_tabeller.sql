ALTER TABLE inntektsmelding
    RENAME TO vedtaksperiode;

ALTER TABLE inntektsmelding_status
    RENAME TO vedtaksperiode_status;

ALTER TABLE vedtaksperiode_status
    RENAME COLUMN inntektsmelding_id TO vedtaksperiode_db_id;