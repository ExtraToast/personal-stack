-- Spring Authorization Server JDBC schema (PostgreSQL)
-- Adapted from oauth2-authorization-schema.sql and oauth2-authorization-consent-schema.sql
-- in spring-security-oauth2-authorization-server 7.0.4
-- blob -> text, timestamp -> timestamptz for PostgreSQL

CREATE TABLE oauth2_authorization (
    id                            varchar(100)  NOT NULL,
    registered_client_id          varchar(100)  NOT NULL,
    principal_name                varchar(200)  NOT NULL,
    authorization_grant_type      varchar(100)  NOT NULL,
    authorized_scopes             varchar(1000) DEFAULT NULL,
    attributes                    text          DEFAULT NULL,
    state                         varchar(500)  DEFAULT NULL,
    authorization_code_value      text          DEFAULT NULL,
    authorization_code_issued_at  timestamptz   DEFAULT NULL,
    authorization_code_expires_at timestamptz   DEFAULT NULL,
    authorization_code_metadata   text          DEFAULT NULL,
    access_token_value            text          DEFAULT NULL,
    access_token_issued_at        timestamptz   DEFAULT NULL,
    access_token_expires_at       timestamptz   DEFAULT NULL,
    access_token_metadata         text          DEFAULT NULL,
    access_token_type             varchar(100)  DEFAULT NULL,
    access_token_scopes           varchar(1000) DEFAULT NULL,
    oidc_id_token_value           text          DEFAULT NULL,
    oidc_id_token_issued_at       timestamptz   DEFAULT NULL,
    oidc_id_token_expires_at      timestamptz   DEFAULT NULL,
    oidc_id_token_metadata        text          DEFAULT NULL,
    refresh_token_value           text          DEFAULT NULL,
    refresh_token_issued_at       timestamptz   DEFAULT NULL,
    refresh_token_expires_at      timestamptz   DEFAULT NULL,
    refresh_token_metadata        text          DEFAULT NULL,
    user_code_value               text          DEFAULT NULL,
    user_code_issued_at           timestamptz   DEFAULT NULL,
    user_code_expires_at          timestamptz   DEFAULT NULL,
    user_code_metadata            text          DEFAULT NULL,
    device_code_value             text          DEFAULT NULL,
    device_code_issued_at         timestamptz   DEFAULT NULL,
    device_code_expires_at        timestamptz   DEFAULT NULL,
    device_code_metadata          text          DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100)  NOT NULL,
    principal_name       varchar(200)  NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
