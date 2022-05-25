ALTER TABLE inntektsmelding
    ADD CONSTRAINT ekstern_id_unique UNIQUE (ekstern_id);
