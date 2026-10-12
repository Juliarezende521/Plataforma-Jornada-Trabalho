import { HttpErrorResponse } from '@angular/common/http';
import { authErrorMessage } from './auth-feedback';

describe('Authentication feedback policy', () => {
  it('uses generic credentials feedback only for login 401', () => {
    const error = new HttpErrorResponse({ status: 401 });
    expect(authErrorMessage(error, 'login')).toContain('Confira seu email e senha');
    expect(authErrorMessage(error, 'logout')).toContain('confirmar a saída');
  });

  it('explains CSRF recovery without automatically retrying', () => {
    expect(authErrorMessage(new HttpErrorResponse({ status: 403 }), 'login'))
      .toContain('Verifique a sessão');
  });

  it('keeps rate-limit guidance for both operations', () => {
    const error = new HttpErrorResponse({ status: 429 });
    expect(authErrorMessage(error, 'login')).toContain('Aguarde um minuto');
    expect(authErrorMessage(error, 'logout')).toContain('Aguarde um minuto');
  });

  it('does not show server details or exception messages', () => {
    const error = new Error('sensitive-internal-detail');
    expect(authErrorMessage(error, 'login')).not.toContain(error.message);
    expect(authErrorMessage(error, 'logout')).not.toContain(error.message);
  });
});
