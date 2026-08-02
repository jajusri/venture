import { Bonjour } from 'bonjour-service';

import type { MdnsPublisher, MdnsPublisherFactory } from './mdns-advertiser.js';

/** Real mDNS publisher backed by bonjour-service (pure JS, no native bindings). */
export function createBonjourMdnsPublisherFactory(): MdnsPublisherFactory {
  return (): MdnsPublisher => {
    const bonjour = new Bonjour();
    return {
      publish: (options) => {
        const service = bonjour.publish(options);
        return { stop: (callback) => service.stop(callback) };
      },
      destroy: () => bonjour.destroy(),
    };
  };
}
