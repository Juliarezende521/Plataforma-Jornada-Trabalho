import { Component, DestroyRef, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { authErrorMessage } from './auth/auth-feedback';
import { HistoryComponent } from './history/history.component';
import { JourneyComponent } from './journey/journey.component';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, JourneyComponent, HistoryComponent],
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly busy = signal(false);
  protected readonly journeyBusy = signal(false);
  protected readonly view = signal<'current' | 'history'>('current');
  private readonly journeyView = viewChild(JourneyComponent);
  private readonly historyView = viewChild(HistoryComponent);
  private readonly accessHeading = viewChild<ElementRef<HTMLElement>>('accessHeading');
  protected readonly checking = signal(true);
  protected readonly unavailable = signal(false);
  protected readonly message = signal('');
  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(254)] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.checkSession();
    effect(() => {
      const heading = this.accessHeading();
      if (heading && !this.checking()) heading.nativeElement.focus();
    });
    effect(() => { if (!this.auth.user()) { this.journeyBusy.set(false); this.view.set('current'); } });
  }

  protected show(view: 'current' | 'history'): void {
    if (this.busy() || this.journeyBusy()) return;
    this.view.set(view);
    setTimeout(() => {
      if (this.destroyRef.destroyed) return;
      if (view === 'current') this.journeyView()?.focusHeading();
      else this.historyView()?.focusHeading();
    });
  }

  protected checkSession(): void {
    if (this.busy() || this.journeyBusy()) return;
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
    if (this.busy() || this.journeyBusy()) return;
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
      error: (error: unknown) => this.message.set(authErrorMessage(error, 'login')),
    });
  }

  protected logout(): void {
    if (this.busy() || this.journeyBusy()) return;
    this.busy.set(true);
    this.message.set('');
    this.auth.logout().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.message.set('Você saiu da plataforma.'),
        error: (error: unknown) => this.message.set(authErrorMessage(error, 'logout')),
      });
  }

}
