import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule],
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly busy = signal(false);
  protected readonly checking = signal(true);
  protected readonly unavailable = signal(false);
  protected readonly message = signal('');
  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(254)] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() { this.checkSession(); }

  protected checkSession(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.checking.set(true);
    this.message.set('');
    this.auth.refresh().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
      this.busy.set(false);
      this.checking.set(false);
    })).subscribe({
      next: () => this.unavailable.set(false),
      error: () => {
        this.unavailable.set(true);
        this.message.set('Não foi possível verificar seu acesso. Tente novamente.');
      },
    });
  }

  protected login(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const { email, password } = this.form.getRawValue();
    if (!password.trim()) {
      this.message.set('Informe sua senha.');
      return;
    }
    if (new TextEncoder().encode(password).length > 72) {
      this.message.set('A senha informada é muito longa. Verifique a senha fornecida pela empresa.');
      return;
    }
    this.busy.set(true);
    this.message.set('');
    this.auth.login(email, password).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
      this.form.controls.password.reset();
      this.busy.set(false);
    })).subscribe({
      next: (user) => {
        this.unavailable.set(false);
        if (!user) this.message.set('Não foi possível manter a sessão. Entre novamente.');
      },
      error: (error: unknown) => this.message.set(this.errorMessage(error, 'login')),
    });
  }

  protected logout(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.message.set('');
    this.auth.logout().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.message.set('Você saiu da plataforma.'),
        error: (error: unknown) => this.message.set(this.errorMessage(error, 'logout')),
      });
  }

  private errorMessage(error: unknown, operation: 'login' | 'logout'): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 429) return 'Muitas tentativas de acesso. Aguarde um minuto e tente novamente.';
      if (error.status === 403) return 'Seu acesso precisa ser atualizado. Verifique a sessão e tente novamente.';
      if (error.status === 401 && operation === 'login') return 'Não foi possível entrar. Confira seu email e senha.';
    }
    return operation === 'login'
      ? 'Não foi possível confirmar o acesso. Verifique a sessão antes de tentar novamente.'
      : 'Não foi possível confirmar a saída. Verifique a sessão e tente novamente.';
  }
}
