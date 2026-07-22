import type { ServiceToken } from './tokens.js';

export type ServiceFactory<T> = () => T;

export class ServiceContainer {
  private readonly singletons = new Map<ServiceToken, unknown>();
  private readonly factories = new Map<ServiceToken, ServiceFactory<unknown>>();

  registerSingleton<T>(token: ServiceToken, instance: T): this {
    this.singletons.set(token, instance);
    return this;
  }

  registerFactory<T>(token: ServiceToken, factory: ServiceFactory<T>): this {
    this.factories.set(token, factory as ServiceFactory<unknown>);
    return this;
  }

  resolve<T>(token: ServiceToken): T {
    if (this.singletons.has(token)) {
      return this.singletons.get(token) as T;
    }

    const factory = this.factories.get(token);
    if (!factory) {
      throw new Error(`Service not registered: ${token}`);
    }

    const instance = factory();
    this.singletons.set(token, instance);
    return instance as T;
  }

  has(token: ServiceToken): boolean {
    return this.singletons.has(token) || this.factories.has(token);
  }
}
