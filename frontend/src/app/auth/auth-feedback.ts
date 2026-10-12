import { HttpErrorResponse } from '@angular/common/http';

/** Presentation policy only: no requests, retries or authentication state changes. */
export function authErrorMessage(error: unknown, operation: 'login' | 'logout'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 429) return 'Muitas tentativas de acesso. Aguarde um minuto e tente novamente.';
    if (error.status === 403) return 'Seu acesso precisa ser atualizado. Verifique a sessão e tente novamente.';
    if (error.status === 401 && operation === 'login') return 'Não foi possível entrar. Confira seu email e senha.';
  }
  return operation === 'login'
    ? 'Não foi possível confirmar o acesso. Verifique a sessão antes de tentar novamente.'
    : 'Não foi possível confirmar a saída. Verifique a sessão e tente novamente.';
}
