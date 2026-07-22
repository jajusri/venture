export const CONNECTOR_VERSION = '0.1.0';
export const SCHEMA_VERSION = '1.0.0';

export const config = {
  port: Number(process.env.BUDCOM_CONNECTOR_PORT ?? 8080),
  host: process.env.BUDCOM_CONNECTOR_HOST ?? '0.0.0.0',
};
