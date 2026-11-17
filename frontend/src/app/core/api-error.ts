import { HttpErrorResponse } from '@angular/common/http';

interface CorpsErreur {
  message?: string;
}

/**
 * Message lisible d'une erreur d'API. Les services renvoient un JSON homogène { status, message } :
 * les fallbacks Resilience4j y nomment la cause (« participant-service : circuit ouvert… »).
 */
export function messageErreur(erreur: unknown, defaut: string): string {
  if (erreur instanceof HttpErrorResponse) {
    const corps = erreur.error as CorpsErreur | null;
    if (corps && typeof corps === 'object' && corps.message) {
      return `${corps.message} (HTTP ${erreur.status})`;
    }
    if (erreur.status === 0) {
      return 'La Gateway ne répond pas : vérifier que la stack est démarrée (make up).';
    }
    return `${defaut} (HTTP ${erreur.status})`;
  }
  return defaut;
}
