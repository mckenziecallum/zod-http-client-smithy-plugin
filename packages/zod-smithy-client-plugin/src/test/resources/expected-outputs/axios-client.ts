import type { AxiosInstance, AxiosError } from 'axios';
import { z } from 'zod';
import { fromAxios } from './utils.js';
import { parseServiceError } from './errors.js';
import { CreateItemInput } from './CreateItemInput.js';
import { CreateItemOutput } from './CreateItemOutput.js';
import { GetItemInput } from './GetItemInput.js';
import { GetItemOutput } from './GetItemOutput.js';
import { GetItemStatusInput } from './GetItemStatusInput.js';
import { SearchItemsInput } from './SearchItemsInput.js';
import { GetVersionInput } from './GetVersionInput.js';

export function createAxiosClient(instance: AxiosInstance) {
  return {
    async createItem(input: z.input<typeof CreateItemInput>) {
      const req = CreateItemInput.parse(input);
      try {
        const response = await instance.request({
          method: req.method,
          url: req.url,
          headers: req.headers,
          params: req.query,
          data: req.body,
        });
        return CreateItemOutput.parse(fromAxios(response));
      } catch (e: any) {
        if (e?.response) {
          throw parseServiceError('CreateItem', e.response.status, e.response.data);
        }
        throw e;
      }
    },
    async getItem(input: z.input<typeof GetItemInput>) {
      const req = GetItemInput.parse(input);
      try {
        const response = await instance.request({
          method: req.method,
          url: req.url,
          headers: req.headers,
          params: req.query,
          data: req.body,
        });
        return GetItemOutput.parse(fromAxios(response));
      } catch (e: any) {
        if (e?.response) {
          throw parseServiceError('GetItem', e.response.status, e.response.data);
        }
        throw e;
      }
    },
    async getItemStatus(input: z.input<typeof GetItemStatusInput>) {
      const req = GetItemStatusInput.parse(input);
      try {
        const response = await instance.request({
          method: req.method,
          url: req.url,
          headers: req.headers,
          params: req.query,
          data: req.body,
        });
        return response.data;
      } catch (e: any) {
        if (e?.response) {
          throw parseServiceError('GetItemStatus', e.response.status, e.response.data);
        }
        throw e;
      }
    },
    async searchItems(input: z.input<typeof SearchItemsInput>) {
      const req = SearchItemsInput.parse(input);
      try {
        const response = await instance.request({
          method: req.method,
          url: req.url,
          headers: req.headers,
          params: req.query,
          data: req.body,
        });
        return response.data;
      } catch (e: any) {
        if (e?.response) {
          throw parseServiceError('SearchItems', e.response.status, e.response.data);
        }
        throw e;
      }
    },
    async getVersion(input: z.input<typeof GetVersionInput>) {
      const req = GetVersionInput.parse(input);
      try {
        const response = await instance.request({
          method: req.method,
          url: req.url,
          headers: req.headers,
          params: req.query,
          data: req.body,
        });
        return response.data;
      } catch (e: any) {
        if (e?.response) {
          throw parseServiceError('GetVersion', e.response.status, e.response.data);
        }
        throw e;
      }
    },
  };
}
