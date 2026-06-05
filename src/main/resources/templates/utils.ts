import type { AxiosResponse } from 'axios';

export interface RawResponse {
  body: unknown;
  headers: Record<string, string>;
  statusCode: number;
}

export function fromAxios(response: AxiosResponse): RawResponse {
  return {
    body: response.data,
    headers: response.headers as Record<string, string>,
    statusCode: response.status,
  };
}

export async function fromFetch(response: Response): Promise<RawResponse> {
  const headers: Record<string, string> = {};
  response.headers.forEach((value, key) => { headers[key] = value; });
  return {
    body: await response.json(),
    headers,
    statusCode: response.status,
  };
}

export class ServiceError extends Error {
  readonly statusCode: number;
  readonly body: unknown;
  readonly operationName: string;
  readonly _kind: string;

  constructor(opts: {
    message: string;
    statusCode: number;
    body: unknown;
    operationName: string;
    _kind: string;
  }) {
    super(opts.message);
    this.name = 'ServiceError';
    this.statusCode = opts.statusCode;
    this.body = opts.body;
    this.operationName = opts.operationName;
    this._kind = opts._kind;
  }
}
