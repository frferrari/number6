USE number6;

CREATE TABLE batch_listing_projection (
    id serial NOT NULL PRIMARY KEY,
    batch_id text,
    created_at timestamp without time zone,
    batch_specification json NOT NULL,
    auctions json NOT NULL
);
