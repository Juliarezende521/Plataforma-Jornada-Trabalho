import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';

interface ApiStatus {
  status: string;
  stage: string;
  journeyAvailable: boolean;
}

@Component({
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly http = inject(HttpClient);
  protected readonly connection = signal<'idle' | 'loading' | 'success' | 'error'>('idle');

  protected checkConnection(): void {
    if (this.connection() === 'loading') return;
    this.connection.set('loading');
    this.http.get<ApiStatus>('/api/status').pipe(timeout(5000)).subscribe({
      next: (result) => this.connection.set(
        result.status === 'UP' && result.stage === 'FOUNDATION' ? 'success' : 'error'),
      error: () => this.connection.set('error'),
    });
  }
}
