CREATE INDEX vbs_vedtaksperiode_behandling_id_idx
    ON vedtaksperiode_behandling_sykepengesoknad (vedtaksperiode_behandling_id);

CREATE INDEX vbs_sykepengesoknad_uuid_idx
    ON vedtaksperiode_behandling_sykepengesoknad (sykepengesoknad_uuid);