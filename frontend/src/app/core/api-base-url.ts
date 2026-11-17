import { InjectionToken } from '@angular/core';

/** Préfixe des routes de la Gateway (StripPrefix=1 côté Gateway : /api/events → event-service /events). */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  factory: () => '/api',
});
