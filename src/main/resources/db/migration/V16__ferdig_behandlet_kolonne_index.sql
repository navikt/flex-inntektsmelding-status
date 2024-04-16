-- Støtter: WHERE ferdig_behandlet IS NOT NULL ORDER BY opprettet.
CREATE INDEX inntektsmelding_ferdig_behandlet_idx ON inntektsmelding (ferdig_behandlet, opprettet);