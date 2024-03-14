DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'bigquery-dataprodukt')
        THEN
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "bigquery-dataprodukt";
            GRANT USAGE ON SCHEMA public TO "bigquery-dataprodukt";
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "bigquery-dataprodukt";
        END IF;
    END
$$;
